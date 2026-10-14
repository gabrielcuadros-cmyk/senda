package com.fuerzasalvaje.senda.util.gemini

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptsTest {

    private val prompts = GeminiPrompts(Json { ignoreUnknownKeys = true })

    @Test
    fun `parses valid questions and drops malformed ones`() {
        val raw = """
            ```json
            [
              {"statement": "¿2 + 2?", "scenario": null, "options": ["3", "4"], "correctOptionIndex": 1, "explanation": "Suma"},
              {"statement": "Caso", "scenario": "Una planta...", "options": ["A", "B", "C"], "correctOptionIndex": 0, "explanation": "x"},
              {"statement": "Sin opciones", "options": [], "correctOptionIndex": 0},
              {"statement": "Índice inválido", "options": ["A", "B"], "correctOptionIndex": 5}
            ]
            ```
        """.trimIndent()

        val questions = prompts.parseQuestions(raw)

        assertEquals(2, questions.size)
        assertFalse(questions[0].isCase)
        assertNull(questions[0].scenario)
        assertTrue(questions[1].isCase)
        assertEquals(1, questions[0].correctOptionIndex)
    }

    @Test(expected = GeminiException::class)
    fun `fails when no question is usable`() {
        prompts.parseQuestions("""[{"statement": "", "options": ["A"], "correctOptionIndex": 0}]""")
    }

    @Test(expected = GeminiException::class)
    fun `fails on non json output`() {
        prompts.parseQuestions("Lo siento, no puedo ayudar con eso.")
    }

    @Test
    fun `tutor rules forbid all-nighters`() {
        val prompt = prompts.generateQuestions("Física", "Cinemática", 10)
        assertTrue(prompt.system.contains("Nunca recomiendes amanecidas"))
        assertTrue(prompt.expectsJson)
    }

    @Test
    fun `config never prints the api key`() {
        val config = GeminiConfig(apiKey = "secret-value", model = "m", provider = "apikey")
        assertFalse(config.toString().contains("secret-value"))
    }
}
