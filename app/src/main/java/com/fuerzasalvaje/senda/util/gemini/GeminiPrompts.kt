package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Strict JSON shape requested with responseSchema. */
enum class ResponseSchema { QUESTIONS }

/**
 * A provider-agnostic, text-only request: system instruction, user text, expected output and thinking.
 * [timeoutMillis] overrides the usual per-call limit (the whole question bank needs longer).
 */
data class GeminiPrompt(
    val system: String,
    val user: String,
    val expectsJson: Boolean = false,
    val schema: ResponseSchema? = null,
    val thinking: ThinkingLevel? = null,
    val timeoutMillis: Long? = null,
) {
    /** Ask for the strict question schema (responseSchema). */
    val questionSchema: Boolean get() = schema == ResponseSchema.QUESTIONS
}

/** Builds the Spanish prompts shared by every GeminiService implementation and parses their output. */
class GeminiPrompts(private val json: Json) {

    fun explainMistake(
        courseName: String,
        question: String,
        options: List<String>,
        correctAnswer: String,
        studentAnswer: String?,
    ) = GeminiPrompt(
        system = TUTOR_RULES,
        user = buildString {
            appendLine("Curso: $courseName")
            appendLine("Pregunta: $question")
            appendLine("Opciones:")
            options.forEach { appendLine("- $it") }
            appendLine("Respuesta correcta: $correctAnswer")
            appendLine("Respuesta del estudiante: ${studentAnswer ?: "(no respondió)"}")
            append(
                "Explica en máximo 120 palabras por qué la respuesta correcta lo es y, " +
                    "si respondió, en qué se equivocó. Termina con un consejo breve para repasar el tema."
            )
        },
    )

    fun studyTips(request: StudyTipsRequest) = GeminiPrompt(
        system = TUTOR_RULES,
        user = buildString {
            appendLine("Nivel de estrés de hoy (1 a 5): ${request.stressLevel}")
            appendLine("Minutos de Pomodoro completados esta semana: ${request.weeklyPomodoroMinutes}")
            appendLine("Pendientes ordenados por prioridad:")
            request.pendingItems
                .sortedByDescending { it.priorityScore }
                .forEach {
                    appendLine(
                        "- ${it.title} (${it.courseName}): prioridad ${"%.2f".format(it.priorityScore)}, " +
                            "faltan ${it.hoursUntilDue} h, horas de estudio pendientes ${it.pendingStudyHours}"
                    )
                }
            append(
                "Da 3 a 5 consejos concretos para hoy, en viñetas y máximo 150 palabras. " +
                    "Si el estrés es 4 o 5, incluye una pausa activa o técnica de respiración."
            )
        },
    )

    /**
     * The whole question bank of a course in one call (HU-15). Every question must come from the syllabus and the
     * PDF Markdown, whose concrete data and cases come first and are cited by slide or page. There is no second
     * review call: the model checks each question itself before returning it, with medium thinking.
     */
    fun generateQuestions(
        courseName: String,
        syllabus: String,
        count: Int,
        pdfMarkdown: String = "",
        avoidStatements: List<String> = emptyList(),
    ): GeminiPrompt {
        val mix = QuestionMix.of(count)
        return GeminiPrompt(
            system = QUESTION_WRITER_RULES,
            user = buildString {
                appendLine("Curso: $courseName")
                appendMaterial(syllabus, pdfMarkdown)
                if (avoidStatements.isNotEmpty()) {
                    appendLine()
                    appendLine("Ya existen estas preguntas; no repitas ninguna ni hagas variantes casi iguales:")
                    avoidStatements.forEach { appendLine("- ${it.take(160)}") }
                }
                appendLine()
                appendLine("Genera exactamente $count preguntas de simulacro con estas reglas:")
                appendLine("1. Basa CADA pregunta en el temario y en el contenido del PDF. Si hay contenido del PDF, prioriza sus datos numéricos, fórmulas y casos concretos.")
                appendLine("2. Tipos: ${mix.conceptual} conceptuales y ${mix.applied} de aplicación o cálculo (casos con datos numéricos cuando el tema lo permita). Las de aplicación van como caso: scenario con el enunciado del caso y statement con la pregunta.")
                appendLine("3. Dificultad: ${mix.easy} fácil(es), ${mix.medium} media(s) y ${mix.hard} difícil(es).")
                appendLine("4. Exactamente 4 opciones del mismo tipo y de longitud parecida. Los distractores deben ser plausibles y basados en errores comunes de estudiantes. Prohibido \"todas las anteriores\" y \"ninguna de las anteriores\".")
                appendLine("5. Una sola respuesta correcta, sin ambigüedad. Varía su posición: reparte la correcta entre las opciones 0, 1, 2 y 3.")
                appendLine("6. explanation: 2 a 4 oraciones con por qué la correcta es correcta y por qué el distractor más tentador es incorrecto. No menciones letras ni números de opción. Si la pregunta sale del contenido del PDF, termina con su fuente según la sección de donde salió: \"Fuente: diap. N\" para \"## Diapositiva N\" o \"Fuente: pág. N\" para \"## Página N\".")
                appendLine("7. Español neutro, sin errores de ortografía, unidades del Sistema Internacional.")
                appendLine("8. Reparte las preguntas por todo el material, en su orden, sin repetir temas ni hacer variantes casi iguales.")
                appendLine()
                appendLine("Antes de devolver cada pregunta, verifícala tú mismo contra el material:")
                appendLine("- la respuesta marcada es la única correcta y coincide con los datos del material,")
                appendLine("- el enunciado no es ambiguo y no admite otra respuesta defendible,")
                appendLine("- ningún distractor es obvio o absurdo y las opciones tienen longitud parecida,")
                appendLine("- la explicación justifica la respuesta y no contradice el material.")
                appendLine("Si una pregunta no pasa la verificación, corrígela o reemplázala por otra; nunca devuelvas una pregunta dudosa.")
                append("correctOptionIndex va de 0 a 3. scenario es null cuando la pregunta no es un caso.")
            },
            expectsJson = true,
            schema = ResponseSchema.QUESTIONS,
            thinking = ThinkingLevel.MEDIUM,
            timeoutMillis = RetryPolicy.BANK_TIMEOUT_MILLIS,
        )
    }

    private fun StringBuilder.appendMaterial(syllabus: String, pdfMarkdown: String) {
        if (pdfMarkdown.isNotBlank()) {
            appendLine("Contenido de los PDF del curso en Markdown. Cada \"## Diapositiva N\" o \"## Página N\" marca de dónde sale el texto:")
            appendLine(pdfMarkdown)
            appendLine("Basa las preguntas en este contenido; no inventes temas que no aparezcan en él.")
        }
        if (syllabus.isNotBlank()) {
            appendLine("Temario que entra en el examen:")
            appendLine(syllabus)
        }
    }

    /**
     * Reads every question in the answer, valid or not; validation happens in the domain.
     * Accepts `{"questions": [...]}` (the schema) or a bare array, with or without code fences.
     * Throws [AiError.InvalidResponse] when the text is not JSON of either shape.
     */
    fun parseQuestions(raw: String): List<GeneratedQuestion> =
        items(raw, "questions").mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<QuestionDto>(element).toDomain() }.getOrNull()
        }

    private fun items(raw: String, key: String): JsonArray {
        val payload = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (payload.isEmpty()) throw AiError.InvalidResponse("Empty answer")
        val root: JsonElement = try {
            json.parseToJsonElement(payload)
        } catch (e: Exception) {
            throw AiError.InvalidResponse("Not JSON: ${e.message?.take(120)}", e)
        }
        return when (root) {
            is JsonArray -> root
            is JsonObject -> (root[key] ?: root.values.firstOrNull { it is JsonArray }) as? JsonArray
                ?: throw AiError.InvalidResponse("JSON object without a $key array")
            else -> throw AiError.InvalidResponse("Unexpected JSON root")
        }
    }

    /** Question types and difficulty for one batch: 60/40 and 30/50/20 (a batch of 5: 3+2 and 1/3/1). */
    data class QuestionMix(val conceptual: Int, val applied: Int, val easy: Int, val medium: Int, val hard: Int) {
        companion object {
            fun of(count: Int): QuestionMix {
                val conceptual = (count * 6 + 5) / 10
                val easy = count * 3 / 10
                val hard = maxOf(if (count >= 3) 1 else 0, count * 2 / 10)
                return QuestionMix(conceptual, count - conceptual, easy, (count - easy - hard).coerceAtLeast(0), hard)
            }
        }
    }

    @Serializable
    private data class QuestionDto(
        val statement: String = "",
        val scenario: String? = null,
        val options: List<String> = emptyList(),
        val correctOptionIndex: Int = -1,
        val explanation: String = "",
    ) {
        fun toDomain() = GeneratedQuestion(
            statement = statement.trim(),
            options = options.map { it.trim() },
            correctOptionIndex = correctOptionIndex,
            explanation = explanation.trim(),
            scenario = scenario?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) },
        )
    }

    private companion object {
        // Business rule D-15: wellbeing first, never recommend all-nighters.
        val TUTOR_RULES = """
            Eres el tutor de estudio de Senda, una app para estudiantes de Tecsup en semana de exámenes.
            Responde siempre en español, con tono cercano y claro.
            Nunca recomiendes amanecidas ni estudiar entre las 23:00 y las 06:00; el sueño está protegido.
            No hagas diagnósticos de salud mental. Si el estudiante parece muy estresado, sugiere pausas y pedir apoyo.
        """.trimIndent()

        val QUESTION_WRITER_RULES = """
            Eres un docente de Tecsup que redacta preguntas de examen de alta calidad para el simulacro de Senda.
            Escribes en español neutro, con ortografía correcta y unidades del Sistema Internacional.
            Solo usas el temario y el material que te dan; nunca inventas datos que no estén en ellos.
            Nunca recomiendes amanecidas ni estudiar entre las 23:00 y las 06:00.
        """.trimIndent()
    }
}
