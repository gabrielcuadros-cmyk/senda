package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.PendingStudyItem
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.domain.usecase.pdf.ExtractPdfMarkdownUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.CourseMaterialUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateCourseQuestionsUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateQuestionBatchesUseCase
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakePdfTextExtractor
import com.fuerzasalvaje.senda.testing.FakeQuestionRepository
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.testing.testClock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

/**
 * Gemini never receives a PDF: every HTTP request the REST client builds, for every AI feature and for an exam that
 * has a real PDF file on the device, is checked for "application/pdf" and inline data.
 */
class NoPdfToGeminiTest {

    @get:Rule val folder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val logger = FakeAiLogger()

    /** Records each request body and answers like Gemini, without network. */
    private val fakeGemini = Interceptor { chain ->
        val body = Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
        bodies += body
        val text = when {
            body.contains("Revisa estas preguntas") -> """{"reviews":[{"index":0,"verdict":"ok"},{"index":1,"verdict":"ok"},{"index":2,"verdict":"ok"}]}"""
            body.contains("Genera exactamente") -> questionsJson()
            else -> "Repasa la ley de Ohm."
        }
        val envelope = buildJsonObject {
            putJsonArray("candidates") {
                add(buildJsonObject {
                    putJsonObject("content") { putJsonArray("parts") { add(buildJsonObject { put("text", text) }) } }
                    put("finishReason", "STOP")
                })
            }
        }
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(envelope.toString().toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun questionsJson(): String = buildJsonObject {
        put("questions", buildJsonArray {
            listOf(
                Triple("¿Qué corriente circula por una resistencia de 4 ohm conectada a 12 V?", listOf("3 A", "4 A", "12 A", "48 A"), "I = V / R = 12 / 4 = 3 A."),
                Triple("¿Qué potencia disipa la resistencia del caso de estudio?", listOf("36 W", "12 W", "48 W", "3 W"), "P = V x I = 12 x 3 = 36 W."),
                Triple("¿Qué unidad mide la tensión eléctrica según la diapositiva uno?", listOf("Voltio", "Amperio", "Ohmio", "Vatio"), "La tensión se mide en voltios."),
            ).forEach { (statement, options, why) ->
                add(buildJsonObject {
                    put("statement", statement)
                    putJsonArray("options") { options.forEach { add(it) } }
                    put("correctOptionIndex", 0)
                    put("explanation", "$why Las otras opciones confunden las magnitudes. Fuente: diap. 2")
                })
            }
        })
    }.toString()

    private val service = GeminiRestService(
        client = OkHttpClient.Builder().addInterceptor(fakeGemini).build(),
        json = json,
        config = GeminiConfig("test-key", "gemini-3-flash", GeminiConfig.PROVIDER_API_KEY),
        prompts = GeminiPrompts(json),
        logger = logger,
    )

    @Test
    fun `no request to Gemini carries a PDF, even for an exam with a PDF on the device`() = runBlocking {
        val pdf = folder.newFile("temario.pdf").apply { writeText("%PDF-1.4\n% binario del temario\n%%EOF") }
        val evaluations = FakeEvaluationRepository().apply {
            seed(exam("Parcial 1", inHours = 48).copy(syllabus = "Ley de Ohm y potencia", syllabusPdfPath = pdf.path))
        }
        val generate = GenerateCourseQuestionsUseCase(
            FakeQuestionRepository(),
            FakeCourseRepository().apply { seed(course()) },
            CourseMaterialUseCase(evaluations, ExtractPdfMarkdownUseCase(FakePdfTextExtractor(), logger, testClock)),
            GenerateQuestionBatchesUseCase(service, logger, Random(1)),
            testClock,
            logger,
        )

        val created = generate("c1", 3).getOrThrow()
        service.explainMistake("Física", "¿I?", listOf("3 A", "4 A"), "3 A", "4 A").getOrThrow()
        service.studyTips(StudyTipsRequest(3, listOf(PendingStudyItem("Parcial 1", "Física", 50.0, 20, 4.0)))).getOrThrow()

        assertTrue(created.isNotEmpty())
        assertTrue(bodies.size >= 4) // questions, review, explanation and tips
        // The PDF content arrives as Markdown text made on the device.
        assertTrue(bodies.any { it.contains("## Diapositiva 2") })
        bodies.forEach { body ->
            assertFalse(body.contains("application/pdf"))
            assertFalse(body.contains("inlineData"))
            assertFalse(body.contains("%PDF"))
        }
    }
}
