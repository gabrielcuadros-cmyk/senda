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
     * Tries the main model and then each fallback in order. Every model gets the 2 s / 5 s / 10 s
     * retries for 429, 500 and 503, except a daily quota 429, which moves on at once because
     * waiting cannot fix it. Any other error fails immediately. When every model ends with 429,
     * the result is [AiError.FreeLimitReached].
     */
    private suspend fun generateText(prompt: GeminiPrompt): String {
        val chain = (listOf(config.model) + config.fallbackModels).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val failures = mutableListOf<AiError>()
        var markedRetrying = false
        val markRetrying = {
            if (!markedRetrying) {
                markedRetrying = true
                changeRetrying(+1)
            }
        }
        try {
            for ((index, model) in chain.withIndex()) {
                if (index > 0) {
                    markRetrying()
                    logger.warn("Switching to fallback model $model ($index/${chain.size - 1}) after ${failures.last().describe()}")
                }
                try {
                    return sendWithRetries(prompt, model, markRetrying)
                } catch (e: AiError) {
                    if (!e.isRetryable()) throw e
                    failures += e
                }
            }
            if (failures.all { it is AiError.QuotaExceeded }) {
                logger.warn("Every model hit its quota: ${chain.joinToString()}")
                throw AiError.FreeLimitReached(chain)
            }
            throw failures.last()
        } finally {
            if (markedRetrying) changeRetrying(-1)
        }
    }

    /** One model with its retries; rethrows the last retryable error when they run out. */
    private suspend fun sendWithRetries(prompt: GeminiPrompt, model: String, onRetry: () -> Unit): String {
        var lastError: AiError
        try {
            return send(prompt, model)
        } catch (e: AiError) {
            if (!e.isRetryable() || e.isDailyQuota()) throw e.also { logDailyQuota(it, model) }
            lastError = e
        }
        for ((index, wait) in retryPolicy.delaysMillis.withIndex()) {
            onRetry()
            logger.warn("Gemini busy (${lastError.describe()}, model=$model); retry ${index + 1}/${retryPolicy.delaysMillis.size} in ${wait / 1000} s")
            delay(wait)
            try {
                return send(prompt, model)
            } catch (e: AiError) {
                if (!e.isRetryable() || e.isDailyQuota()) throw e.also { logDailyQuota(it, model) }
                lastError = e
            }
        }
        throw lastError
    }

    private fun logDailyQuota(error: AiError, model: String) {
        if (error.isDailyQuota()) logger.warn("Daily quota exhausted for $model; no retries")
    }

    private fun AiError.isRetryable(): Boolean = when (this) {
        is AiError.QuotaExceeded -> true
        is AiError.Server -> code in RetryPolicy.RETRYABLE_HTTP_CODES
        else -> false
    }

    private fun AiError.isDailyQuota(): Boolean = this is AiError.QuotaExceeded && daily

    private fun AiError.describe(): String = when (this) {
        is AiError.QuotaExceeded -> if (daily) "HTTP 429 daily quota" else "HTTP 429"
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

/** Gemini names per-day quotas with "PerDay" in the quota id; the message may also say "per day". */
fun isDailyQuota(text: String): Boolean =
    text.contains("PerDay", ignoreCase = true) || text.contains("per day", ignoreCase = true)
