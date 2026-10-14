package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.PdfDigestRequest
import com.fuerzasalvaje.senda.domain.model.PdfDigestResult
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.QuestionVerdict
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
     * Tries the main model and then each fallback in order. Every call has a [RetryPolicy.callTimeoutMillis] limit.
     * A timeout, a missing model (404), an overload (500, 503), an empty answer or any other failure moves on to
     * the next model at once; only a rate limit (429 per minute) waits and retries on the same model first.
     * Only an invalid API key stops the chain. When every model ends with 429 the result is
     * [AiError.FreeLimitReached]; otherwise the last error.
     */
    private suspend fun generateText(prompt: GeminiPrompt): String = generateWithModel(prompt).first

    /** [generateText] plus the model of the chain that answered. */
    private suspend fun generateWithModel(prompt: GeminiPrompt): Pair<String, String> {
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
                    return sendWithRetries(prompt, model, markRetrying) to model
                } catch (e: AiError) {
                    if (e.stopsChain()) throw e
                    failures += e
                }
            }
            if (failures.isNotEmpty() && failures.all { it is AiError.QuotaExceeded }) {
                logger.warn("Every model hit its quota: ${chain.joinToString()}")
                throw AiError.FreeLimitReached(chain)
            }
            throw failures.lastOrNull() ?: AiError.Configuration("No Gemini model configured")
        } finally {
            if (markedRetrying) changeRetrying(-1)
        }
    }

    /** One call with the time limit; a timeout becomes [AiError.Timeout] so the chain moves on. */
    private suspend fun sendOnce(prompt: GeminiPrompt, model: String): String {
        val limit = prompt.timeoutMillis ?: retryPolicy.callTimeoutMillis
        return try {
            withTimeout(limit) { send(prompt, model) }
        } catch (e: TimeoutCancellationException) {
            logger.warn("Gemini timeout after ${limit / 1000} s (model=$model)")
            throw AiError.Timeout(e)
        }
    }

    /** One model; only a per-minute rate limit is retried here, everything else goes back to the chain. */
    private suspend fun sendWithRetries(prompt: GeminiPrompt, model: String, onRetry: () -> Unit): String {
        var lastError: AiError
        try {
            return sendOnce(prompt, model)
        } catch (e: AiError) {
            if (!e.isRateLimit()) throw e.also { logDailyQuota(it, model) }
            lastError = e
        }
        for ((index, wait) in retryPolicy.delaysMillis.withIndex()) {
            onRetry()
            logger.warn("Gemini busy (${lastError.describe()}, model=$model); retry ${index + 1}/${retryPolicy.delaysMillis.size} in ${wait / 1000} s")
            delay(wait)
            try {
                return sendOnce(prompt, model)
            } catch (e: AiError) {
                if (!e.isRateLimit()) throw e.also { logDailyQuota(it, model) }
                lastError = e
            }
        }
        throw lastError
    }

    private fun logDailyQuota(error: AiError, model: String) {
        if (error.isDailyQuota()) logger.warn("Daily quota exhausted for $model; no retries")
    }

    /** 429 per minute: waiting on the same model helps. A daily quota does not. */
    private fun AiError.isRateLimit(): Boolean = this is AiError.QuotaExceeded && !daily

    private fun AiError.stopsChain(): Boolean = this is AiError.Configuration && invalidKey

    private fun AiError.isDailyQuota(): Boolean = this is AiError.QuotaExceeded && daily

    private fun AiError.describe(): String = when (this) {
        is AiError.QuotaExceeded -> if (daily) "HTTP 429 daily quota" else "HTTP 429"
        is AiError.Server -> "HTTP $code"
        is AiError.Configuration -> if (invalidKey) "invalid API key" else "model not found"
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

    /** The text (or, if scanned, the PDF itself) once, with the long time limit. Plain Markdown answer. */
    override suspend fun digestPdf(request: PdfDigestRequest): Result<PdfDigestResult> = runSafely {
        val attachments = request.pdfPath?.let { path -> withContext(Dispatchers.IO) { loadPdfs(listOf(path), logger) } }.orEmpty()
        require(request.text.isNotBlank() || attachments.isNotEmpty()) { "Nothing to summarize" }
        logger.info(
            "PDF summary requested: textChars=${request.text.length} " +
                "scannedPdf=${attachments.sumOf { it.bytes.size } / 1024} KB timeout=${RetryPolicy.DIGEST_TIMEOUT_MILLIS / 1000} s"
        )
        val (text, model) = generateWithModel(prompts.digestPdf(request.courseName, request.evaluationTitle, request.text, attachments))
        PdfDigestResult(text.trim(), model)
    }

    /** One batch with the strict question schema; one more call when the answer is not valid JSON. No PDF is sent. */
    override suspend fun generateQuestions(request: QuestionRequest): Result<List<GeneratedQuestion>> = runSafely {
        require(request.count > 0) { "count must be positive" }
        require(request.syllabus.isNotBlank() || request.studyDigest.isNotBlank()) { "Nothing to generate from" }
        val prompt = prompts.generateQuestions(
            request.courseName, request.syllabus, request.count, request.studyDigest,
            request.batchIndex, request.batchCount, request.avoidStatements,
        )
        logger.info(
            "Questions requested=${request.count} batch=${request.batchIndex + 1}/${request.batchCount} " +
                "syllabusChars=${request.syllabus.length} digestChars=${request.studyDigest.length}"
        )
        parsed(prompt, "Questions") { prompts.parseQuestions(it) }
    }

    override suspend fun reviewQuestions(request: QuestionRequest, questions: List<GeneratedQuestion>): Result<List<QuestionVerdict>> =
        runSafely {
            if (questions.isEmpty()) return@runSafely emptyList()
            val prompt = prompts.reviewQuestions(request.courseName, request.syllabus, questions, request.studyDigest)
            parsed(prompt, "Review") { prompts.parseReview(it) }
        }

    private suspend fun <T> parsed(prompt: GeminiPrompt, label: String, parse: (String) -> List<T>): List<T> {
        var lastError: AiError.InvalidResponse? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val raw = generateText(prompt)
            try {
                val items = parse(raw)
                logger.info("$label received=${items.size} on attempt ${attempt + 1}")
                return items
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
