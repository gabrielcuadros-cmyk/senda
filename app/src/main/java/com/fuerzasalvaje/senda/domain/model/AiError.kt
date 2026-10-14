package com.fuerzasalvaje.senda.domain.model

/** Typed AI failures, so each screen can explain what went wrong in Spanish. */
sealed class AiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection or the host could not be reached. */
    class Network(cause: Throwable? = null) : AiError("Network error", cause)

    /** The request took longer than the allowed time. */
    class Timeout(cause: Throwable? = null) : AiError("Timeout", cause)

    /** HTTP 429 / RESOURCE_EXHAUSTED. [daily] is true for per-day quotas, where waiting does not help. */
    class QuotaExceeded(detail: String, val daily: Boolean = false) : AiError(detail)

    /** Every model in the chain answered 429: the free tier is used up for today. */
    class FreeLimitReached(val models: List<String>) : AiError("All models hit their quota: ${models.joinToString()}")

    /** Missing or invalid API key, or a model name that does not exist. */
    class Configuration(detail: String) : AiError(detail)

    /** The answer could not be read (invalid JSON, empty or truncated). */
    class InvalidResponse(detail: String, cause: Throwable? = null) : AiError(detail, cause)

    /** The prompt or the answer was blocked by safety filters. */
    class Blocked(reason: String) : AiError(reason)

    /** Any other server failure. */
    class Server(val code: Int, detail: String) : AiError(detail)
}

/** Request to generate mock-exam questions from text and, optionally, the exams' PDFs. */
data class QuestionRequest(
    val courseName: String,
    val syllabus: String,
    val count: Int,
    /** Local paths of PDFs in the app storage (D-11). */
    val pdfPaths: List<String> = emptyList(),
)
