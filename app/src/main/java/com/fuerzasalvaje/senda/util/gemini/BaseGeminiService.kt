package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import kotlin.coroutines.cancellation.CancellationException

/** Shared prompt and parsing flow; subclasses only implement the transport. */
abstract class BaseGeminiService(private val prompts: GeminiPrompts) : GeminiService {

    /** Sends [prompt] to the model and returns its text output. Throws on failure. */
    protected abstract suspend fun generateText(prompt: GeminiPrompt): String

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

    override suspend fun generateQuestions(
        courseName: String,
        syllabus: String,
        count: Int,
    ): Result<List<GeneratedQuestion>> = runSafely {
        require(count > 0) { "count must be positive" }
        require(syllabus.isNotBlank()) { "syllabus must not be blank" }
        prompts.parseQuestions(generateText(prompts.generateQuestions(courseName, syllabus, count)))
    }

    // Like runCatching, but keeps coroutine cancellation working.
    private inline fun <T> runSafely(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
