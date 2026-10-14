package com.fuerzasalvaje.senda.domain.repository

import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * AI features of Senda. Implementations may use the Gemini REST API or Firebase AI Logic.
 * Every request is text only: PDFs are turned into Markdown on the device and never sent.
 * Failures are reported as [com.fuerzasalvaje.senda.domain.model.AiError].
 */
interface GeminiService {

    /** True while a call is waiting to retry after an overload (500, 503) or rate limit (429). */
    val retrying: StateFlow<Boolean>

    /** Explains why [studentAnswer] is wrong (or missing) and why [correctAnswer] is right. */
    suspend fun explainMistake(
        courseName: String,
        question: String,
        options: List<String>,
        correctAnswer: String,
        studentAnswer: String?,
    ): Result<String>

    /** Study advice based on the stress check-in and the pending workload. */
    suspend fun studyTips(request: StudyTipsRequest): Result<String>

    /**
     * Generates a whole question bank (multiple-choice and case questions) from the syllabus and the PDF Markdown
     * in one call; the model checks each question before returning it. Returns every question received, valid or
     * not: the caller validates them.
     */
    suspend fun generateQuestions(request: QuestionRequest): Result<List<GeneratedQuestion>>

}

/** Diagnostic log for AI calls (Logcat tag SendaAI). Implementations must never print secrets. */
interface AiLogger {
    fun info(message: String)
    fun warn(message: String)
}
