package com.fuerzasalvaje.senda.domain.usecase.pdf

import com.fuerzasalvaje.senda.domain.engine.PdfMarkdown
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.localPdfPath
import com.fuerzasalvaje.senda.domain.model.pdfSummarized
import com.fuerzasalvaje.senda.domain.model.withPdfMarkdown
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.PdfTextExtractor
import com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase
import java.time.Clock
import javax.inject.Inject

/** Outcome of reading a PDF on the device. */
sealed interface PdfExtraction {
    /** [markdown] already fits the AI budget; [summarized] when it had to be shortened. */
    data class Ready(val markdown: String, val summarized: Boolean) : PdfExtraction

    /** Scanned or image-only PDF: fewer than [com.fuerzasalvaje.senda.domain.model.PdfText.MIN_TEXT_CHARS] characters. */
    data object NoText : PdfExtraction

    data class Failed(val error: Throwable) : PdfExtraction
}

/**
 * Turns a local PDF into the Markdown the AI reads, on the device. The SendaAI log gets pages, characters and
 * time, never the content.
 */
class ExtractPdfMarkdownUseCase @Inject constructor(
    private val extractor: PdfTextExtractor,
    private val logger: AiLogger,
    private val clock: Clock,
) {
    /** [syllabus] decides which pages stay first when the PDF must be shortened. */
    suspend operator fun invoke(path: String, syllabus: String = ""): PdfExtraction {
        val started = clock.millis()
        val text = extractor.extract(path).getOrElse { error ->
            logger.warn("PDF text extraction failed: ${error.javaClass.simpleName} in ${clock.millis() - started} ms")
            return PdfExtraction.Failed(error)
        }
        if (!text.hasReadableText) {
            logger.info(
                "PDF without readable text: pages=${text.pageCount} extractedChars=${text.chars} " +
                    "in ${clock.millis() - started} ms; nothing is sent to the AI"
            )
            return PdfExtraction.NoText
        }
        val markdown = PdfMarkdown.convert(text)
        val fitted = PdfMarkdown.fit(markdown, syllabus)
        logger.info(
            "PDF to Markdown: pages=${text.pageCount} slides=${text.slides} extractedChars=${text.chars} " +
                "markdownChars=${markdown.length} sentChars=${fitted.text.length} summarized=${fitted.summarized} " +
                "in ${clock.millis() - started} ms"
        )
        return PdfExtraction.Ready(fitted.text, fitted.summarized)
    }
}

/** "Contenido que leerá la IA" outside the form: edit it, or read the PDF again. Both refresh the question bank. */
class EditPdfMarkdownUseCase @Inject constructor(
    private val extract: ExtractPdfMarkdownUseCase,
    private val save: SaveEvaluationUseCase,
) {
    suspend fun update(evaluation: Evaluation, markdown: String): Result<Unit> =
        save(evaluation.withPdfMarkdown(markdown.trim(), evaluation.pdfSummarized)).map { }

    /** Reads the local PDF again; the result says what happened so the screen can explain it. */
    suspend fun reextract(evaluation: Evaluation): PdfExtraction {
        val path = evaluation.localPdfPath ?: return PdfExtraction.Failed(IllegalStateException("No local PDF"))
        val result = extract(path, (evaluation as? Exam)?.syllabus.orEmpty())
        if (result is PdfExtraction.Ready) {
            save(evaluation.withPdfMarkdown(result.markdown, result.summarized)).exceptionOrNull()?.let { return PdfExtraction.Failed(it) }
        }
        return result
    }
}
