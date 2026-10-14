package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.engine.QuestionValidator
import com.fuerzasalvaje.senda.domain.model.AiError
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptsTest {

    private val prompts = GeminiPrompts(Json { ignoreUnknownKeys = true; isLenient = true })

    private val validJson = """
        {"questions": [
          {"scenario": null, "statement": "¿Qué mide la velocidad?", "options": ["Distancia/tiempo", "Masa", "Fuerza", "Energía"],
           "correctOptionIndex": 0, "explanation": "La velocidad es distancia entre tiempo."},
          {"scenario": "Un auto frena en 5 s.", "statement": "¿Qué magnitud cambia?", "options": ["Masa", "Velocidad", "Carga", "Volumen"],
           "correctOptionIndex": 1, "explanation": "Frenar reduce la velocidad."}
        ]}
    """.trimIndent()

    @Test
    fun `valid schema JSON is parsed completely`() {
        val questions = prompts.parseQuestions(validJson)

        assertEquals(2, questions.size)
        assertNull(questions[0].scenario)
        assertTrue(questions[1].isCase)
        assertTrue(questions.all(QuestionValidator::isValid))
    }

    @Test
    fun `a bare array inside code fences is also accepted`() {
        val raw = "```json\n[{\"statement\": \"¿2+2?\", \"options\": [\"3\", \"4\", \"5\", \"6\"], \"correctOptionIndex\": 1, \"explanation\": \"Suma\"}]\n```"

        assertEquals(1, prompts.parseQuestions(raw).size)
    }

    @Test
    fun `partial JSON keeps every item so the validator can explain each discard`() {
        val raw = """
            {"preguntas": [
              {"statement": "Válida", "options": ["A", "B", "C", "D"], "correctOptionIndex": 2, "explanation": "Porque C"},
              {"statement": "Tres opciones", "options": ["A", "B", "C"], "correctOptionIndex": 0, "explanation": "x"},
              {"statement": "Sin explicación", "options": ["A", "B", "C", "D"], "correctOptionIndex": 0},
              {"statement": "Índice malo", "options": ["A", "B", "C", "D"], "correctOptionIndex": 7, "explanation": "x"},
              {"scenario": "null", "statement": "Escenario nulo como texto", "options": ["A", "B", "C", "D"], "correctOptionIndex": 3, "explanation": "y"},
              "esto no es una pregunta"
            ]}
        """.trimIndent()

        val questions = prompts.parseQuestions(raw)

        assertEquals(5, questions.size) // the plain string is skipped
        assertEquals(listOf(true, false, false, false, true), questions.map(QuestionValidator::isValid))
        assertEquals(listOf("tiene 3 opciones en vez de 4"), QuestionValidator.problems(questions[1]))
        assertEquals(listOf("sin explicación"), QuestionValidator.problems(questions[2]))
        assertEquals(listOf("índice correcto fuera de rango (7)"), QuestionValidator.problems(questions[3]))
        assertFalse(questions[4].isCase)
    }

    @Test(expected = AiError.InvalidResponse::class)
    fun `truncated JSON is an invalid response`() {
        prompts.parseQuestions(validJson.dropLast(40))
    }

    @Test(expected = AiError.InvalidResponse::class)
    fun `plain text is an invalid response`() {
        prompts.parseQuestions("Lo siento, no puedo ayudar con eso.")
    }

    @Test(expected = AiError.InvalidResponse::class)
    fun `an object without questions is an invalid response`() {
        prompts.parseQuestions("""{"error": "sin datos"}""")
    }

    @Test
    fun `question prompt asks for the schema and grounds questions on the PDF`() {
        val withPdf = prompts.generateQuestions("Física", "Cinemática", 10, listOf(PdfAttachment(byteArrayOf(1), "temario.pdf")))
        assertTrue(withPdf.expectsJson && withPdf.questionSchema)
        assertTrue(withPdf.user.contains("Basa todas las preguntas en el contenido de ese material"))
        assertTrue(withPdf.system.contains("Nunca recomiendes amanecidas"))

        assertFalse(prompts.generateQuestions("Física", "Cinemática", 10).user.contains("Material adjunto"))
    }

    @Test
    fun `study tips prompt includes this week's pomodoro minutes`() {
        val request = com.fuerzasalvaje.senda.domain.model.StudyTipsRequest(
            stressLevel = 4,
            pendingItems = listOf(com.fuerzasalvaje.senda.domain.model.PendingStudyItem("Parcial", "Física", 50.0, 20, 4.0)),
            weeklyPomodoroMinutes = 125,
        )

        assertTrue(prompts.studyTips(request).user.contains("Minutos de Pomodoro completados esta semana: 125"))
    }

    @Test
    fun `config never prints the api key`() {
        val config = GeminiConfig(apiKey = "secret-value", model = "m", provider = "apikey")
        assertFalse(config.toString().contains("secret-value"))
    }

    @Test
    fun `the REST schema requires exactly four options`() {
        val schema = GeminiRestService.QUESTION_SCHEMA.toString()
        assertTrue(schema.contains("\"minItems\":4") && schema.contains("\"maxItems\":4"))
        assertTrue(schema.contains("\"required\":[\"questions\"]"))
    }
}
