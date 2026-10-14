package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BaseGeminiServiceTest {

    @get:Rule val folder = TemporaryFolder()

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
    fun `PDFs are attached up to the size limit and missing files are skipped`() {
        val small = folder.newFile("temario.pdf").apply { writeBytes(ByteArray(1_000)) }
        val big = folder.newFile("libro.pdf").apply { writeBytes(ByteArray(3_000)) }

        val attachments = BaseGeminiService.loadPdfs(
            listOf(small.path, "/no/existe.pdf", big.path),
            logger,
            maxBytes = 2_000,
        )

        assertEquals(listOf("temario.pdf"), attachments.map { it.name })
        assertTrue(logger.lines.any { it.contains("PDF not found") })
        assertTrue(logger.lines.any { it.contains("over the") && it.contains("libro.pdf") })
    }

    @Test
    fun `the PDF goes in the prompt and logs never contain the content`() = runTest {
        val pdf = folder.newFile("guia.pdf").apply { writeBytes("%PDF-1.4 contenido".toByteArray()) }
        val service = ScriptedService(good)

        service.generateQuestions(QuestionRequest("Física", "", 10, listOf(pdf.path))).getOrThrow()

        assertEquals(1, service.prompts.single().attachments.size)
        assertTrue(logger.lines.any { it.contains("pdfs=1") })
        assertTrue(logger.lines.none { it.contains("%PDF") })
    }
}
