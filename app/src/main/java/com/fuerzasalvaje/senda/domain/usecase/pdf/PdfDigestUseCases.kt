package com.fuerzasalvaje.senda.domain.usecase.pdf

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.PdfDigest
import com.fuerzasalvaje.senda.domain.model.PdfDigestRequest
import com.fuerzasalvaje.senda.domain.model.PdfDigestStatus
import com.fuerzasalvaje.senda.domain.model.PdfText
import com.fuerzasalvaje.senda.domain.model.localPdfPath
import com.fuerzasalvaje.senda.domain.model.pdfKeyOf
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.domain.repository.PdfDigestRepository
import com.fuerzasalvaje.senda.domain.repository.PdfDigestScheduler
import com.fuerzasalvaje.senda.domain.repository.PdfTextExtractor
import com.fuerzasalvaje.senda.domain.repository.QuestionPrefetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

/** Live digests by evaluation id (detail sheet and course cards). */
class ObservePdfDigestsUseCase @Inject constructor(private val repository: PdfDigestRepository) {
    operator fun invoke(): Flow<Map<String, PdfDigest>> = repository.observeAll().map { list -> list.associateBy { it.evaluationId } }
}

/**
 * Keeps the digest in step with the evaluation's PDF: a new PDF starts a fresh digest ("Leyendo tu PDF…" at once)
 * and its background job; a removed PDF or evaluation drops it.
 */
class TrackPdfDigestUseCase @Inject constructor(
    private val digests: PdfDigestRepository,
    private val scheduler: PdfDigestScheduler,
    private val clock: Clock,
) {
    suspend fun onSaved(evaluation: Evaluation, previous: Evaluation?) {
        val path = evaluation.localPdfPath
        if (path == previous?.localPdfPath) return
        if (path == null) {
            digests.delete(evaluation.id)
            return
        }
        digests.save(
            PdfDigest(
                evaluationId = evaluation.id,
                courseId = evaluation.courseId,
                pdfKey = pdfKeyOf(path),
                status = PdfDigestStatus.READING,
                updatedAt = clock.instant(),
            )
        )
        scheduler.schedule(evaluation.id, restart = true)
    }

    suspend fun onDeleted(evaluationId: String) {
        digests.delete(evaluationId)
    }
}

enum class PdfDigestOutcome { SUCCESS, RETRY, FAIL }

/**
 * Logic of the PDF digest worker:
 * 1. Extracts the text on the device once and stores it (a PDF without text is marked as scanned).
 * 2. Sends that text (or, if scanned, the PDF) to Gemini once for a study summary, with page or slide references.
 * 3. Stores the summary (Room and Firestore) and refreshes the course question bank from it.
 * Timeouts, network errors and overloads are retried with backoff; an invalid key or an unreadable file are final.
 * The SendaAI log gets sizes, times and the model, never the PDF content.
 */
class RunPdfDigestUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val digests: PdfDigestRepository,
    private val extractor: PdfTextExtractor,
    private val gemini: GeminiService,
    private val prefetcher: QuestionPrefetcher,
    private val logger: AiLogger,
    private val clock: Clock,
) {
    suspend operator fun invoke(evaluationId: String): PdfDigestOutcome {
        val evaluation = evaluations.observeById(evaluationId).firstOrNull()
        val path = evaluation?.localPdfPath
        if (evaluation == null || path == null) {
            // The PDF was removed meanwhile, or it lives on another device: nothing to read here.
            logger.info("PDF digest skipped: no local PDF for evaluation $evaluationId")
            return PdfDigestOutcome.SUCCESS
        }
        val key = pdfKeyOf(path)
        val stored = digests.observeById(evaluationId).first()?.takeIf { it.pdfKey == key }
        if (stored?.isReady == true) return PdfDigestOutcome.SUCCESS

        val digest = stored?.takeIf { it.extractedText != null } ?: extract(evaluation, path, key, stored)
            ?: return PdfDigestOutcome.RETRY
        return summarize(evaluation, path, digest)
    }

    private suspend fun extract(evaluation: Evaluation, path: String, key: String, stored: PdfDigest?): PdfDigest? {
        val started = clock.millis()
        val text = extractor.extract(path).getOrElse { error ->
            logger.warn("PDF text extraction failed (${error.javaClass.simpleName}); the PDF itself will be summarized")
            PdfText(emptyList())
        }
        logger.info(
            "PDF extracted: evaluation=${evaluation.id} pages=${text.pageCount} chars=${text.chars} " +
                "scanned=${text.isScanned} in ${clock.millis() - started} ms"
        )
        val digest = PdfDigest(
            evaluationId = evaluation.id,
            courseId = evaluation.courseId,
            pdfKey = key,
            status = stored?.status ?: PdfDigestStatus.READING,
            pageCount = text.pageCount,
            extractedChars = text.chars,
            scanned = text.isScanned,
            updatedAt = clock.instant(),
            extractedText = if (text.isScanned) "" else text.marked(),
        )
        return digest.takeIf { digests.save(it).isSuccess }
    }

    private suspend fun summarize(evaluation: Evaluation, path: String, digest: PdfDigest): PdfDigestOutcome {
        val courseName = courses.observeById(evaluation.courseId).firstOrNull()?.name.orEmpty()
        val request = PdfDigestRequest(
            courseName = courseName,
            evaluationTitle = evaluation.title,
            text = digest.extractedText.orEmpty(),
            pdfPath = if (digest.scanned) path else null,
        )
        val started = clock.millis()
        val result = gemini.digestPdf(request)
        val elapsed = clock.millis() - started
        val summary = result.getOrNull()?.summary?.trim()?.take(PdfDigest.MAX_SUMMARY_CHARS)
        if (summary != null && summary.isNotBlank()) {
            digests.save(digest.copy(status = PdfDigestStatus.READY, summary = summary, updatedAt = clock.instant()))
            logger.info(
                "PDF digest ready: evaluation=${evaluation.id} pages=${digest.pageCount} extractedChars=${digest.extractedChars} " +
                    "summaryChars=${summary.length} scanned=${digest.scanned} in $elapsed ms model=${result.getOrNull()?.model}"
            )
            // The bank so far came from the syllabus alone: a new set now comes from the PDF content.
            prefetcher.refill(evaluation.courseId, fresh = true)
            return PdfDigestOutcome.SUCCESS
        }
        val error = result.exceptionOrNull()
        val final = (error is AiError.Configuration && error.invalidKey) || error is IllegalArgumentException || error is AuthError
        logger.warn(
            "PDF digest ${if (final) "failed" else "will retry"} after $elapsed ms: " +
                (error?.javaClass?.simpleName ?: "empty summary")
        )
        val status = if (final) PdfDigestStatus.FAILED else PdfDigestStatus.RETRYING
        digests.save(digest.copy(status = status, updatedAt = clock.instant()))
        return if (final) PdfDigestOutcome.FAIL else PdfDigestOutcome.RETRY
    }
}
