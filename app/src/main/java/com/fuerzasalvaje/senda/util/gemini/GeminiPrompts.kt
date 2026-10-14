package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A provider-agnostic request: system instruction, user text and whether JSON output is expected. */
data class GeminiPrompt(
    val system: String,
    val user: String,
    val expectsJson: Boolean = false,
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

    fun generateQuestions(courseName: String, syllabus: String, count: Int) = GeminiPrompt(
        system = TUTOR_RULES,
        user = """
            Curso: $courseName
            Temario:
            $syllabus

            Genera exactamente $count preguntas de simulacro sobre este temario.
            Mezcla preguntas de opción múltiple y preguntas de caso (un escenario breve seguido de opción múltiple).
            Cada pregunta tiene 4 opciones y una sola correcta.
            Responde solo con un arreglo JSON con este formato:
            [{"statement": "...", "scenario": "... o null", "options": ["...", "...", "...", "..."],
              "correctOptionIndex": 0, "explanation": "..."}]
        """.trimIndent(),
        expectsJson = true,
    )

    /** Parses the model output, dropping malformed questions. Fails if nothing usable remains. */
    fun parseQuestions(raw: String): List<GeneratedQuestion> {
        val payload = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val dtos = try {
            json.decodeFromString<List<QuestionDto>>(payload)
        } catch (e: Exception) {
            throw GeminiException("The AI response is not a valid question list", e)
        }
        val questions = dtos.mapNotNull { it.toDomainOrNull() }
        if (questions.isEmpty()) throw GeminiException("The AI response has no valid questions")
        return questions
    }

    @Serializable
    private data class QuestionDto(
        val statement: String = "",
        val scenario: String? = null,
        val options: List<String> = emptyList(),
        val correctOptionIndex: Int = -1,
        val explanation: String = "",
    ) {
        fun toDomainOrNull(): GeneratedQuestion? {
            if (statement.isBlank() || options.size < 2 || correctOptionIndex !in options.indices) return null
            return GeneratedQuestion(
                statement = statement.trim(),
                options = options.map { it.trim() },
                correctOptionIndex = correctOptionIndex,
                explanation = explanation.trim(),
                scenario = scenario?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
            )
        }
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
