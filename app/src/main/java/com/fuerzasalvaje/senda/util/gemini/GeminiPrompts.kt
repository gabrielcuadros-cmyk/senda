package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.QuestionVerdict
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** A PDF sent to the model as inline data. Not a data class: it holds a byte array. */
class PdfAttachment(val bytes: ByteArray, val name: String)

/** Strict JSON shape requested with responseSchema. */
enum class ResponseSchema { QUESTIONS, REVIEW }

/**
 * A provider-agnostic request: system instruction, user text, expected output, thinking and attachments.
 * [timeoutMillis] overrides the usual per-call limit (the PDF summary may take up to 120 s).
 */
data class GeminiPrompt(
    val system: String,
    val user: String,
    val expectsJson: Boolean = false,
    val schema: ResponseSchema? = null,
    val thinking: ThinkingLevel? = null,
    val attachments: List<PdfAttachment> = emptyList(),
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
     * Study summary of one PDF, made once in the background. The text arrives with "[Página N]" markers; a scanned
     * PDF arrives as an attachment instead. Every point keeps its page or slide so questions can cite it.
     */
    fun digestPdf(courseName: String, evaluationTitle: String, text: String, attachments: List<PdfAttachment> = emptyList()) =
        GeminiPrompt(
            system = DIGEST_RULES,
            user = buildString {
                appendLine("Curso: $courseName")
                appendLine("Evaluación: $evaluationTitle")
                if (text.isNotBlank()) {
                    appendLine("Texto extraído del PDF. Cada [Página N] marca una página; en presentaciones, cada página es una diapositiva:")
                    appendLine(text)
                } else {
                    appendLine("El PDF va adjunto (es escaneado, sin texto seleccionable). Lee su contenido.")
                }
                appendLine()
                appendLine("Escribe un resumen de estudio de este material para preparar preguntas de examen, con estas secciones:")
                appendLine("## Conceptos clave")
                appendLine("## Definiciones")
                appendLine("## Fórmulas")
                appendLine("## Datos numéricos")
                appendLine("## Casos y ejemplos")
                appendLine("Reglas:")
                appendLine("1. Cada punto es una viñeta breve y termina con su fuente entre paréntesis: (diap. N) o (pág. N), según el marcador.")
                appendLine("2. Copia literalmente cifras, unidades, fórmulas y nombres propios. No inventes nada que no esté en el material.")
                appendLine("3. Los casos y ejemplos conservan sus datos para poder plantear preguntas de aplicación.")
                appendLine("4. Omite las secciones sin contenido. Sin introducción ni conclusión.")
                append("5. Máximo 2500 palabras, en español.")
            },
            thinking = ThinkingLevel.LOW,
            attachments = attachments,
            timeoutMillis = RetryPolicy.DIGEST_TIMEOUT_MILLIS,
        )

    /**
     * One batch of questions (HU-15). Every question must come from the syllabus and the PDF study summaries, whose
     * concrete data and cases come first. Low thinking: speed matters, the review checks the result.
     */
    fun generateQuestions(
        courseName: String,
        syllabus: String,
        count: Int,
        studyDigest: String = "",
        batchIndex: Int = 0,
        batchCount: Int = 1,
        avoidStatements: List<String> = emptyList(),
    ): GeminiPrompt {
        val mix = QuestionMix.of(count)
        return GeminiPrompt(
            system = QUESTION_WRITER_RULES,
            user = buildString {
                appendLine("Curso: $courseName")
                appendMaterial(syllabus, studyDigest)
                if (batchCount > 1) {
                    appendLine()
                    appendLine(
                        "Esta es la tanda ${batchIndex + 1} de $batchCount, generadas en paralelo. " +
                            "Cubre sobre todo la parte ${batchIndex + 1} de $batchCount del material (en su orden), " +
                            "para que las tandas no repitan temas."
                    )
                }
                if (avoidStatements.isNotEmpty()) {
                    appendLine()
                    appendLine("Ya existen estas preguntas; no repitas ninguna ni hagas variantes casi iguales:")
                    avoidStatements.forEach { appendLine("- ${it.take(160)}") }
                }
                appendLine()
                appendLine("Genera exactamente $count preguntas de simulacro con estas reglas:")
                appendLine("1. Basa CADA pregunta en el temario y en el resumen del PDF. Si hay resumen, prioriza sus datos numéricos, fórmulas y casos concretos.")
                appendLine("2. Tipos: ${mix.conceptual} conceptuales y ${mix.applied} de aplicación o cálculo (casos con datos numéricos cuando el tema lo permita). Las de aplicación van como caso: scenario con el enunciado del caso y statement con la pregunta.")
                appendLine("3. Dificultad: ${mix.easy} fácil(es), ${mix.medium} media(s) y ${mix.hard} difícil(es).")
                appendLine("4. Exactamente 4 opciones del mismo tipo y de longitud parecida. Los distractores deben ser plausibles y basados en errores comunes de estudiantes. Prohibido \"todas las anteriores\" y \"ninguna de las anteriores\".")
                appendLine("5. Una sola respuesta correcta, sin ambigüedad. Varía su posición: reparte la correcta entre las opciones 0, 1, 2 y 3.")
                appendLine("6. explanation: 2 a 4 oraciones con por qué la correcta es correcta y por qué el distractor más tentador es incorrecto. No menciones letras ni números de opción. Si la pregunta sale del resumen del PDF, termina con su fuente tal como aparece en el resumen, por ejemplo \"Fuente: diap. 4\" o \"Fuente: pág. 12\".")
                appendLine("7. Español neutro, sin errores de ortografía, unidades del Sistema Internacional.")
                append("correctOptionIndex va de 0 a 3. scenario es null cuando la pregunta no es un caso.")
            },
            expectsJson = true,
            schema = ResponseSchema.QUESTIONS,
            thinking = ThinkingLevel.LOW,
        )
    }

    /** Second pass over a batch with the same material: approve, or return a corrected version. Medium thinking. */
    fun reviewQuestions(
        courseName: String,
        syllabus: String,
        questions: List<GeneratedQuestion>,
        studyDigest: String = "",
    ) = GeminiPrompt(
        system = REVIEWER_RULES,
        user = buildString {
            appendLine("Curso: $courseName")
            appendMaterial(syllabus, studyDigest)
            appendLine()
            appendLine("Revisa estas preguntas de simulacro (index empieza en 0):")
            appendLine(json.encodeToString(questions.mapIndexed { i, q -> ReviewInputDto.of(i, q) }))
            appendLine()
            appendLine("Para cada pregunta devuelve verdict \"ok\" si es correcta y clara, o \"corregir\" si tiene:")
            appendLine("- la respuesta marcada como correcta equivocada,")
            appendLine("- más de una respuesta defendible o un enunciado ambiguo,")
            appendLine("- un distractor obvio o absurdo, opciones de longitud muy distinta o \"todas/ninguna de las anteriores\",")
            appendLine("- datos que contradicen el material, o una explicación que no justifica la respuesta.")
            appendLine("Si es \"corregir\", incluye en question la versión corregida completa (4 opciones, correctOptionIndex de 0 a 3, explanation de 2 a 4 oraciones).")
            appendLine("Si la pregunta no tiene relación con el material y no se puede arreglar, usa verdict \"descartar\".")
            append("Incluye una entrada por cada index y un reason breve.")
        },
        expectsJson = true,
        schema = ResponseSchema.REVIEW,
        thinking = ThinkingLevel.MEDIUM,
    )

    private fun StringBuilder.appendMaterial(syllabus: String, studyDigest: String) {
        if (studyDigest.isNotBlank()) {
            appendLine("Resumen de estudio de los PDF del curso (cada punto indica su página o diapositiva):")
            appendLine(studyDigest)
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

    /**
     * Reads the review: one verdict per index. "ok" approves; "corregir" with a readable question is a correction;
     * anything else ("descartar", or a correction that cannot be read) rejects the question.
     */
    fun parseReview(raw: String): List<QuestionVerdict> =
        items(raw, "reviews").mapNotNull { element ->
            val dto = runCatching { json.decodeFromJsonElement<ReviewDto>(element) }.getOrNull() ?: return@mapNotNull null
            if (dto.index < 0) return@mapNotNull null
            when (dto.verdict.trim().lowercase()) {
                "ok", "aprobada", "aprobar" -> QuestionVerdict(dto.index, approved = true, reason = dto.reason)
                "corregir", "corregida" -> QuestionVerdict(dto.index, approved = false, corrected = dto.question?.toDomain(), reason = dto.reason)
                else -> QuestionVerdict(dto.index, approved = false, reason = dto.reason)
            }
        }.distinctBy { it.index }

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

    @Serializable
    private data class ReviewDto(
        val index: Int = -1,
        val verdict: String = "",
        val reason: String = "",
        val question: QuestionDto? = null,
    )

    @Serializable
    private data class ReviewInputDto(
        val index: Int,
        val scenario: String?,
        val statement: String,
        val options: List<String>,
        val correctOptionIndex: Int,
        val explanation: String,
    ) {
        companion object {
            fun of(index: Int, q: GeneratedQuestion) =
                ReviewInputDto(index, q.scenario, q.statement, q.options, q.correctOptionIndex, q.explanation)
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

        val QUESTION_WRITER_RULES = """
            Eres un docente de Tecsup que redacta preguntas de examen de alta calidad para el simulacro de Senda.
            Escribes en español neutro, con ortografía correcta y unidades del Sistema Internacional.
            Solo usas el temario y el material que te dan; nunca inventas datos que no estén en ellos.
            Nunca recomiendes amanecidas ni estudiar entre las 23:00 y las 06:00.
        """.trimIndent()

        val DIGEST_RULES = """
            Eres un docente de Tecsup que prepara resúmenes de estudio fieles al material del curso.
            Escribes en español neutro, con ortografía correcta y unidades del Sistema Internacional.
            Solo usas el material que te dan; nunca inventas datos, cifras ni ejemplos.
        """.trimIndent()

        val REVIEWER_RULES = """
            Eres un revisor exigente de preguntas de examen de Tecsup. Compruebas cada pregunta contra el material dado:
            que la respuesta marcada sea la única correcta, que no haya ambigüedad y que los distractores sean plausibles.
            Corriges en español neutro, sin errores de ortografía y con unidades del Sistema Internacional.
        """.trimIndent()
    }
}
