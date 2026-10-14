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

/** A PDF sent to the model as inline data. Not a data class: it holds a byte array. */
class PdfAttachment(val bytes: ByteArray, val name: String)

/** A provider-agnostic request: system instruction, user text, expected output and attachments. */
data class GeminiPrompt(
    val system: String,
    val user: String,
    val expectsJson: Boolean = false,
    /** Ask for the strict question schema (responseSchema). */
    val questionSchema: Boolean = false,
    val attachments: List<PdfAttachment> = emptyList(),
)

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

    /** With [attachments] the questions must come from the PDFs; the text is a guide of what enters. */
    fun generateQuestions(courseName: String, syllabus: String, count: Int, attachments: List<PdfAttachment> = emptyList()) =
        GeminiPrompt(
            system = TUTOR_RULES,
            user = buildString {
                appendLine("Curso: $courseName")
                if (attachments.isNotEmpty()) {
                    appendLine("Material adjunto: ${attachments.size} PDF del curso.")
                    appendLine("Basa todas las preguntas en el contenido de ese material; no inventes temas que no aparezcan en él.")
                }
                if (syllabus.isNotBlank()) {
                    appendLine("Temario que entra en el examen:")
                    appendLine(syllabus)
                }
                appendLine()
                appendLine("Genera exactamente $count preguntas de simulacro.")
                appendLine("Mezcla preguntas de opción múltiple y preguntas de caso (un escenario breve seguido de la pregunta).")
                appendLine("Cada pregunta tiene exactamente 4 opciones distintas y una sola correcta.")
                appendLine("correctOptionIndex va de 0 a 3. scenario es null cuando no es una pregunta de caso.")
                append("Incluye en explanation la solución fundamentada en 1 a 3 oraciones.")
            },
            expectsJson = true,
            questionSchema = true,
            attachments = attachments,
        )

    /**
     * Reads every question in the answer, valid or not; validation happens in the domain.
     * Accepts `{"questions": [...]}` (the schema) or a bare array, with or without code fences.
     * Throws [AiError.InvalidResponse] when the text is not JSON of either shape.
     */
    fun parseQuestions(raw: String): List<GeneratedQuestion> {
        val payload = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (payload.isEmpty()) throw AiError.InvalidResponse("Empty answer")
        val root: JsonElement = try {
            json.parseToJsonElement(payload)
        } catch (e: Exception) {
            throw AiError.InvalidResponse("Not JSON: ${e.message?.take(120)}", e)
        }
        val items: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["questions"] ?: root.values.firstOrNull { it is JsonArray }) as? JsonArray
                ?: throw AiError.InvalidResponse("JSON object without a questions array")
            else -> throw AiError.InvalidResponse("Unexpected JSON root")
        }
        return items.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<QuestionDto>(element).toDomain() }.getOrNull()
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
    }
}
