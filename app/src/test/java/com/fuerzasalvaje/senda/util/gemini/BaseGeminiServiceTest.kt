package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseGeminiServiceTest {

    private val logger = FakeAiLogger()
    private val good = """{"questions":[{"statement":"¿A?","options":["1","2","3","4"],"correctOptionIndex":0,"explanation":"x"}]}"""

    /** Returns the scripted answers in order and records every prompt it receives. */
    private inner class ScriptedService(vararg answers: String) :
        BaseGeminiService(GeminiPrompts(Json { ignoreUnknownKeys = true }), logger, GeminiConfig("key", "main-model", "apikey")) {
        private val queue = ArrayDeque(answers.toList())
        val prompts = mutableListOf<GeminiPrompt>()
        override suspend fun send(prompt: GeminiPrompt, model: String): String {
            prompts += prompt
            return queue.removeFirst()
        }
    }

    @Test
    fun `invalid JSON is retried once`() = runTest {
        val service = ScriptedService("Claro, aquí tienes:", good)

        val questions = service.generateQuestions(QuestionRequest("Física", "Cinemática", 10)).getOrThrow()

        assertEquals(1, questions.size)
        assertEquals(2, service.prompts.size)
        assertTrue(logger.lines.any { it.startsWith("W Invalid JSON on attempt 1") })
    }

    @Test
    fun `two invalid answers fail as an invalid response`() = runTest {
        val service = ScriptedService("nope", "{\"questions\": [")

        val error = service.generateQuestions(QuestionRequest("Física", "Cinemática", 10)).exceptionOrNull()

        assertTrue(error is AiError.InvalidResponse)
        assertEquals(2, service.prompts.size)
    }

    @Test
    fun `questions are generated from the PDF Markdown as text, and the log has sizes only`() = runTest {
        val service = ScriptedService(good)

        service.generateQuestions(QuestionRequest("Física", "", 10, pdfMarkdown = "## Diapositiva 4\n- v = d / t")).getOrThrow()

        assertTrue(service.prompts.single().user.contains("## Diapositiva 4\n- v = d / t"))
        assertTrue(logger.lines.any { it.contains("pdfChars=28") && it.contains("sentChars=") })
        assertTrue(logger.lines.none { it.contains("v = d / t") })
    }
}
