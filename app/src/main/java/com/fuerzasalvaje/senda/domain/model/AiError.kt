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

    /**
     * Missing or invalid API key ([invalidKey], stops everything) or a model that does not exist (HTTP 404),
     * which only skips that model.
     */
    class Configuration(detail: String, val invalidKey: Boolean = true) : AiError(detail)

    /** The answer could not be read (invalid JSON, empty or truncated). */
    class InvalidResponse(detail: String, cause: Throwable? = null) : AiError(detail, cause)

    /** The prompt or the answer was blocked by safety filters. */
    class Blocked(reason: String) : AiError(reason)

    /** Any other server failure. */
    class Server(val code: Int, detail: String) : AiError(detail)
}

/** Request to generate mock-exam questions from the syllabus and the study summaries of the course PDFs. */
data class QuestionRequest(
    val courseName: String,
    val syllabus: String,
    val count: Int,
    /** Study summaries of the exam and lab PDFs ([PdfDigest.summary]); the PDFs themselves are never sent. */
    val studyDigest: String = "",
    /** Position of this batch among the parallel batches, so each one covers a different part of the syllabus. */
    val batchIndex: Int = 0,
    val batchCount: Int = 1,
    /** Statements already in the bank, so the model does not ask the same again. */
    val avoidStatements: List<String> = emptyList(),
)

/**
 * One PDF to summarize: its text with "[Página N]" markers or, for a scanned PDF without text, the local
 * [pdfPath] so the file itself is sent once.
 */
data class PdfDigestRequest(
    val courseName: String,
    val evaluationTitle: String,
    val text: String,
    val pdfPath: String? = null,
)

/** The study summary and the model that wrote it (for the SendaAI log). */
data class PdfDigestResult(val summary: String, val model: String)
