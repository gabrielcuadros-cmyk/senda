package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.PdfDigestRequest
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
    fun `questions are generated from the PDF summary, never with the PDF attached`() = runTest {
        val service = ScriptedService(good)

        service.generateQuestions(QuestionRequest("Física", "", 10, studyDigest = "- v = d / t (diap. 4)")).getOrThrow()

        assertTrue(service.prompts.single().attachments.isEmpty())
        assertTrue(logger.lines.any { it.contains("digestChars=21") })
        assertTrue(logger.lines.none { it.contains("v = d / t") })
    }

    @Test
    fun `a scanned PDF is attached once to the summary call, with 120 s, and logs never contain the content`() = runTest {
        val pdf = folder.newFile("guia.pdf").apply { writeBytes("%PDF-1.4 contenido".toByteArray()) }
        val service = ScriptedService("## Fórmulas\n- v = d / t (diap. 4)")

        val result = service.digestPdf(PdfDigestRequest("Física", "Parcial 1", text = "", pdfPath = pdf.path)).getOrThrow()

        assertEquals("main-model", result.model)
        val prompt = service.prompts.single()
        assertEquals(1, prompt.attachments.size)
        assertEquals(RetryPolicy.DIGEST_TIMEOUT_MILLIS, prompt.timeoutMillis)
        assertTrue(logger.lines.none { it.contains("%PDF") || it.contains("v = d / t") })
    }

    @Test
    fun `a PDF with text is summarized from its text`() = runTest {
        val service = ScriptedService("- Resumen (diap. 1)")

        service.digestPdf(PdfDigestRequest("Física", "Parcial 1", text = "[Página 1]\nCinemática")).getOrThrow()

        assertTrue(service.prompts.single().attachments.isEmpty())
        assertTrue(service.prompts.single().user.contains("[Página 1]\nCinemática"))
    }
}
