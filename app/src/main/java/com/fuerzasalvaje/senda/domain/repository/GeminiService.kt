package com.fuerzasalvaje.senda.domain.repository

import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest

/**
 * AI features of Senda. Implementations may use the Gemini REST API or Firebase AI Logic.
 * Failures are reported as [com.fuerzasalvaje.senda.domain.model.AiError].
 */
interface GeminiService {

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
     * Generates multiple-choice and case questions from the syllabus and the attached PDFs.
     * Returns every question received, valid or not: the caller validates them.
     */
    suspend fun generateQuestions(request: QuestionRequest): Result<List<GeneratedQuestion>>
}

/** Diagnostic log for AI calls (Logcat tag SendaAI). Implementations must never print secrets. */
interface AiLogger {
    fun info(message: String)
    fun warn(message: String)
}
