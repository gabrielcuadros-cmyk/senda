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
    fun `question prompt grounds questions on the PDF Markdown, prioritizes its data and cites the slide`() {
        val markdown = "## Diapositiva 4\n- v = d / t\n- Caso: un auto recorre 100 m en 20 s"
        val withPdf = prompts.generateQuestions("Física", "Cinemática", 10, pdfMarkdown = markdown)
        assertTrue(withPdf.expectsJson && withPdf.questionSchema)
        assertTrue(withPdf.user.contains("Contenido de los PDF del curso en Markdown"))
        assertTrue(withPdf.user.contains(markdown))
        assertTrue(withPdf.user.contains("prioriza sus datos numéricos, fórmulas y casos concretos"))
        assertTrue(withPdf.user.contains("\"Fuente: diap. N\" para \"## Diapositiva N\""))
        assertTrue(withPdf.system.contains("Nunca recomiendes amanecidas"))

        assertFalse(prompts.generateQuestions("Física", "Cinemática", 10).user.contains("Contenido de los PDF"))
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
    fun `the whole bank is one prompt that asks the model to check each question, with medium thinking and 90 s`() {
        val generation = prompts.generateQuestions("Física", "Cinemática", 15, avoidStatements = listOf("¿Qué es la rapidez?"))

        assertEquals(ThinkingLevel.MEDIUM, generation.thinking)
        assertEquals(ResponseSchema.QUESTIONS, generation.schema)
        assertEquals(RetryPolicy.BANK_TIMEOUT_MILLIS, generation.timeoutMillis)
        listOf(
            "Genera exactamente 15 preguntas", "9 conceptuales y 6 de aplicación", "Prohibido \"todas las anteriores\"",
            "Fuente: diap. N", "¿Qué es la rapidez?", "unidades del Sistema Internacional",
            "Antes de devolver cada pregunta, verifícala tú mismo contra el material",
            "la respuesta marcada es la única correcta", "nunca devuelvas una pregunta dudosa",
        ).forEach { assertTrue("missing: $it", generation.user.contains(it)) }
        assertFalse(generation.user.contains("tanda"))
    }

    @Test
    fun `thinking setting depends on the model family`() {
        assertEquals(ThinkingSetting.Level("low"), Thinking.forModel("gemini-3.5-flash", ThinkingLevel.LOW))
        assertEquals(ThinkingSetting.Level("medium"), Thinking.forModel("gemini-3-flash", ThinkingLevel.MEDIUM))
        assertEquals(ThinkingSetting.Level("high"), Thinking.forModel("gemini-3-pro-preview", ThinkingLevel.MEDIUM))
        assertEquals(ThinkingSetting.Budget(0), Thinking.forModel("gemini-2.5-flash", ThinkingLevel.LOW))
        assertEquals(ThinkingSetting.Budget(128), Thinking.forModel("gemini-2.5-pro", ThinkingLevel.LOW))
        assertNull(Thinking.forModel("gemini-2.0-flash", ThinkingLevel.LOW))
        assertNull(Thinking.forModel("gemini-3-flash", null))
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
