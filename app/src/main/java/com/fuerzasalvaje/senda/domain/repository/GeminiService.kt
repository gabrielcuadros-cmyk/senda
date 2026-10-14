package com.fuerzasalvaje.senda.domain.repository

import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest

/**
 * AI features of Senda. Implementations may use the Gemini REST API or Firebase AI Logic.
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

    /** Generates [count] multiple-choice and case questions from the course [syllabus]. */
    suspend fun generateQuestions(
        courseName: String,
        syllabus: String,
        count: Int,
    ): Result<List<GeneratedQuestion>>
}
