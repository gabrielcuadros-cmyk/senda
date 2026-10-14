package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** Shared prompt, attachment, retry and parsing flow; subclasses only implement the transport. */
abstract class BaseGeminiService(
    private val prompts: GeminiPrompts,
    protected val logger: AiLogger,
    private val config: GeminiConfig,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) : GeminiService {

    /** Sends [prompt] to [model] and returns its text output. Throws [AiError] on failure. */
    protected abstract suspend fun send(prompt: GeminiPrompt, model: String): String

    // Number of calls currently waiting or retrying; several AI calls can overlap.
    private val retryingCalls = MutableStateFlow(0)
    private val _retrying = MutableStateFlow(false)
    override val retrying: StateFlow<Boolean> = _retrying.asStateFlow()

    private fun changeRetrying(delta: Int) {
        _retrying.value = retryingCalls.updateAndGet { it + delta } > 0
    }

    /**
     * 500, 503 and 429 are retried after 2 s, 5 s and 10 s; if they keep failing, one attempt goes
     * to the fallback model. Any other error fails immediately.
     */
    private suspend fun generateText(prompt: GeminiPrompt): String {
        var lastError: AiError
        try {
            return send(prompt, config.model)
        } catch (e: AiError) {
            if (!e.isRetryable()) throw e
            lastError = e
        }
        changeRetrying(+1)
        try {
            for ((index, wait) in retryPolicy.delaysMillis.withIndex()) {
                logger.warn("Gemini busy (${lastError.describe()}); retry ${index + 1}/${retryPolicy.delaysMillis.size} in ${wait / 1000} s")
                delay(wait)
                try {
                    return send(prompt, config.model)
                } catch (e: AiError) {
                    if (!e.isRetryable()) throw e
                    lastError = e
                }
            }
            val fallback = config.fallbackModel.trim()
            if (fallback.isEmpty() || fallback == config.model) throw lastError
            logger.warn("Gemini still busy after ${retryPolicy.delaysMillis.size} retries; using fallback model $fallback")
            return send(prompt, fallback)
        } finally {
            changeRetrying(-1)
        }
    }

    private fun AiError.isRetryable(): Boolean = when (this) {
        is AiError.QuotaExceeded -> true
        is AiError.Server -> code in RetryPolicy.RETRYABLE_HTTP_CODES
        else -> false
    }

    private fun AiError.describe(): String = when (this) {
        is AiError.QuotaExceeded -> "HTTP 429"
        is AiError.Server -> "HTTP $code"
        else -> javaClass.simpleName
    }

    override suspend fun explainMistake(
        courseName: String,
        question: String,
        options: List<String>,
        correctAnswer: String,
        studentAnswer: String?,
    ): Result<String> = runSafely {
        generateText(prompts.explainMistake(courseName, question, options, correctAnswer, studentAnswer))
    }

    override suspend fun studyTips(request: StudyTipsRequest): Result<String> = runSafely {
        generateText(prompts.studyTips(request))
    }

    /** Strict JSON with the question schema; one retry when the answer is not valid JSON. */
    override suspend fun generateQuestions(request: QuestionRequest): Result<List<GeneratedQuestion>> = runSafely {
        require(request.count > 0) { "count must be positive" }
        val attachments = withContext(Dispatchers.IO) { loadPdfs(request.pdfPaths, logger) }
        require(request.syllabus.isNotBlank() || attachments.isNotEmpty()) { "Nothing to generate from" }
        val prompt = prompts.generateQuestions(request.courseName, request.syllabus, request.count, attachments)
        logger.info(
            "Questions requested=${request.count} syllabusChars=${request.syllabus.length} " +
                "pdfs=${attachments.size} (${attachments.sumOf { it.bytes.size } / 1024} KB)"
        )

        var lastError: AiError.InvalidResponse? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val raw = generateText(prompt)
            try {
                val questions = prompts.parseQuestions(raw)
                logger.info("Questions received=${questions.size} on attempt ${attempt + 1}")
                return@runSafely questions
            } catch (e: AiError.InvalidResponse) {
                logger.warn("Invalid JSON on attempt ${attempt + 1}: ${e.message}. Answer starts with: ${raw.take(160)}")
                lastError = e
            }
        }
        throw lastError ?: AiError.InvalidResponse("No answer")
    }

    // Like runCatching, but keeps coroutine cancellation working.
    private inline fun <T> runSafely(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        /** First call plus one retry for invalid JSON. */
        const val MAX_ATTEMPTS = 2

        /** Total PDF size sent inline (base64 grows it by a third; Gemini accepts about 20 MB). */
        const val MAX_PDF_BYTES = 15L * 1024 * 1024

        /** Reads the local PDFs that exist and fit in [MAX_PDF_BYTES], skipping (and logging) the rest. */
        fun loadPdfs(paths: List<String>, logger: AiLogger, maxBytes: Long = MAX_PDF_BYTES): List<PdfAttachment> {
            var total = 0L
            return paths.distinct().mapNotNull { path ->
                val file = File(path)
                when {
                    !file.isFile -> {
                        logger.warn("PDF not found on this device: ${file.name}")
                        null
                    }
                    total + file.length() > maxBytes -> {
                        logger.warn("PDF skipped, over the ${maxBytes / (1024 * 1024)} MB limit: ${file.name} (${file.length() / 1024} KB)")
                        null
                    }
                    else -> {
                        total += file.length()
                        PdfAttachment(file.readBytes(), file.name)
                    }
                }
            }
        }
    }
}
