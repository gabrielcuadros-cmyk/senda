package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.QuestionReport
import com.fuerzasalvaje.senda.domain.model.QuestionVerdict
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.SimulacrumAnswer
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.CourseMaterialUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ExplainMistakeUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateCourseQuestionsUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateQuestionBatchesUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.MissingSyllabusException
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.NoValidQuestionsException
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrefetchJob
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrefetchOutcome
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrepareQuestionsUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.QuestionBankUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.RefillQuestionBankUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ReportQuestionUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.RunPrefetchUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.StartSimulacrumUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.TopUpSimulacrumUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeGeminiService
import com.fuerzasalvaje.senda.testing.FakeQuestionPrefetcher
import com.fuerzasalvaje.senda.testing.FakeQuestionRepository
import com.fuerzasalvaje.senda.testing.FakeRecommendationRepository
import com.fuerzasalvaje.senda.testing.FakeSimulacrumRepository
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.testing.generated
import com.fuerzasalvaje.senda.testing.question
import com.fuerzasalvaje.senda.testing.testClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import kotlin.random.Random

class SimulacrumUseCasesTest {

    private val courses = FakeCourseRepository().apply { seed(course()) }
    private val evaluations = FakeEvaluationRepository()
    private val questions = FakeQuestionRepository()
    private val simulacra = FakeSimulacrumRepository()
    private val gemini = FakeGeminiService()
    private val recommendations = FakeRecommendationRepository()
    private val prefetcher = FakeQuestionPrefetcher()
    private val logger = FakeAiLogger()

    private val batches = GenerateQuestionBatchesUseCase(gemini, logger, Random(3))
    private val bank = QuestionBankUseCase(questions, simulacra)
    private val generate = GenerateCourseQuestionsUseCase(questions, courses, CourseMaterialUseCase(evaluations), batches, testClock)
    private val prepare = PrepareQuestionsUseCase(bank, generate)
    private val start = StartSimulacrumUseCase(bank, prepare, simulacra, prefetcher, testClock, Random(7))

    /** Each batch answers with its own distinct questions about the syllabus. */
    private fun perBatch(topic: String = "cinemática") {
        gemini.questionsFor = { request -> Result.success((1..request.count).map { generated(request.batchIndex * 10 + it, topic) }) }
    }

    private fun syllabus(text: String = "Cinemática y dinámica de partículas") =
        evaluations.seed(exam("Parcial 1", 30).copy(syllabus = text))

    // Generation in parallel batches

    @Test
    fun `questions are asked in parallel batches of 5`() = runTest {
        syllabus()
        perBatch()

        val created = generate("c1", 12).getOrThrow()

        assertEquals(listOf(5, 5, 2), gemini.requests.sortedBy { it.batchIndex }.map { it.count })
        assertTrue(gemini.requests.all { it.batchCount == 3 })
        assertEquals(12, created.size)
        assertEquals(12, questions.items.value.size)
        assertEquals(3, gemini.reviewCalls)
    }

    @Test
    fun `a failed batch is skipped and the others are used`() = runTest {
        syllabus()
        gemini.questionsFor = { request ->
            if (request.batchIndex == 1) Result.failure(AiError.Timeout())
            else Result.success((1..request.count).map { generated(request.batchIndex * 10 + it) })
        }

        val created = generate("c1", 15).getOrThrow()

        assertEquals(10, created.size)
        assertTrue(logger.lines.any { it.startsWith("W Batch 2/3 failed: Timeout") })
    }

    @Test
    fun `when every batch fails the error is reported, the invalid key first`() = runTest {
        syllabus()
        gemini.questionsFor = { request ->
            Result.failure(if (request.batchIndex == 1) AiError.Configuration("API key not valid") else AiError.Timeout())
        }

        val error = generate("c1", 10).exceptionOrNull()

        assertTrue(error is AiError.Configuration && error.invalidKey)
        assertTrue(questions.items.value.isEmpty())
    }

    @Test
    fun `duplicates, unrelated questions and catch-all options are discarded`() = runTest {
        syllabus()
        gemini.questionsFor = {
            Result.success(
                listOf(
                    generated(1),
                    generated(1).copy(statement = "pregunta 1 de cinemática: caso número 1!!"), // same statement
                    generated(2, topic = "mitología griega"),                                       // unrelated
                    generated(3).copy(options = listOf("A", "B", "C", "Todas las anteriores")),
                    generated(4),
                )
            )
        }

        val created = generate("c1", 5).getOrThrow()

        assertEquals(listOf("Pregunta 1 de cinemática: caso número 1", "Pregunta 4 de cinemática: caso número 4"), created.map { it.statement })
    }

    @Test
    fun `questions already in the bank are not generated again and are sent as do not repeat`() = runTest {
        syllabus()
        questions.seed(question("old").copy(statement = "Pregunta 1 de cinemática: caso número 1"))
        gemini.questionsFor = { Result.success(listOf(generated(1), generated(2))) }

        val created = generate("c1", 5).getOrThrow()

        assertEquals(listOf("Pregunta 2 de cinemática: caso número 2"), created.map { it.statement })
        assertTrue(gemini.requests.single().avoidStatements.contains("Pregunta 1 de cinemática: caso número 1"))
    }

    @Test
    fun `the review keeps approved and corrected questions only`() = runTest {
        syllabus()
        perBatch()
        gemini.reviewFor = { _, batch ->
            Result.success(
                listOf(
                    QuestionVerdict(0, approved = true),
                    QuestionVerdict(1, approved = false, corrected = batch[1].copy(statement = "Pregunta corregida de cinemática")),
                    QuestionVerdict(2, approved = false), // descartar
                    // 3 and 4 missing: not approved
                )
            )
        }

        val created = generate("c1", 5).getOrThrow()

        assertEquals(2, created.size)
        assertTrue(created.any { it.statement == "Pregunta corregida de cinemática" })
    }

    @Test
    fun `if the review cannot be done the checked questions are kept`() = runTest {
        syllabus()
        perBatch()
        gemini.reviewFor = { _, _ -> Result.failure(AiError.Timeout()) }

        assertEquals(5, generate("c1", 5).getOrThrow().size)
        assertTrue(logger.lines.any { it.contains("Review unavailable") })
    }

    @Test
    fun `answers all in the same position are spread over A, B, C and D`() = runTest {
        syllabus()
        gemini.questionsFor = { Result.success((1..5).map { generated(it).copy(correctOptionIndex = 0) }) }

        val created = generate("c1", 5).getOrThrow()

        val top = created.groupingBy { it.correctOptionIndex }.eachCount().values.max()
        assertTrue("most repeated position: $top of 5", top * 2 <= 5)
        // The text of the right answer travels with it.
        created.forEach { q -> assertTrue(q.options[q.correctOptionIndex].startsWith("A")) }
    }

    // Material

    @Test
    fun `without syllabus the student must type the topics`() = runTest {
        assertTrue(prepare("c1", 10).exceptionOrNull() is MissingSyllabusException)

        perBatch(topic = "leyes de newton")
        prepare("c1", 10, topics = "Leyes de Newton y fuerzas").getOrThrow()
        assertEquals("Leyes de Newton y fuerzas", gemini.lastSyllabus)
    }

    @Test
    fun `exam syllabus and PDFs and lab guides are the material`() = runTest {
        evaluations.seed(
            exam("Parcial 1", 30).copy(syllabus = "Unidad 2", syllabusPdfPath = "/files/pdfs/temario.pdf"),
            Lab(id = "lab", courseId = "c1", title = "Péndulo", guideNumber = "3", difficulty = 2, createdAt = TEST_NOW,
                dueAt = TEST_NOW + Duration.ofDays(2), guidePdfPath = "/files/pdfs/guia.pdf"),
        )
        perBatch()

        prepare("c1", 5).getOrThrow()

        assertEquals(listOf("/files/pdfs/temario.pdf", "/files/pdfs/guia.pdf"), gemini.lastRequest?.pdfPaths)
        assertEquals("Parcial 1: Unidad 2\nLaboratorio 3 Péndulo", gemini.lastRequest?.syllabus)
    }

    @Test
    fun `fewer than 3 valid questions is an error`() = runTest {
        syllabus()
        gemini.questionsFor = { Result.success(listOf(generated(1), generated(2))) }

        val error = prepare("c1", 5).exceptionOrNull() as NoValidQuestionsException
        assertEquals(2, error.valid)
    }

    // Starting an attempt

    @Test
    fun `a full bank starts at once without calling the AI and refills for the next attempt`() = runTest {
        questions.seed(*(1..15).map { question("q$it") }.toTypedArray())

        val attempt = start("c1", questionCount = 5, minutes = 10).getOrThrow()

        assertEquals(5, attempt.questionIds.distinct().size)
        assertEquals(10, attempt.timeLimitMinutes)
        assertEquals(TEST_NOW, attempt.startedAt)
        assertEquals(0, gemini.generateCalls)
        assertTrue(prefetcher.topUps.isEmpty())
        assertEquals(listOf("c1" to false), prefetcher.refills)
    }

    @Test
    fun `with 3 questions in the bank the attempt starts with them and asks for the rest`() = runTest {
        questions.seed(question("q1"), question("q2"), question("q3"))

        val attempt = start("c1", questionCount = 10).getOrThrow()

        assertEquals(setOf("q1", "q2", "q3"), attempt.questionIds.toSet())
        assertEquals(0, gemini.generateCalls)
        assertEquals(listOf(Triple("c1", attempt.id, 10)), prefetcher.topUps)
    }

    @Test
    fun `with fewer than 3 the missing questions are generated before starting`() = runTest {
        syllabus()
        perBatch()
        questions.seed(question("q1"))

        val attempt = start("c1", questionCount = 5).getOrThrow()

        assertEquals(5, attempt.questionIds.size)
        assertTrue(gemini.generateCalls > 0)
    }

    @Test
    fun `questions answered right in the last 2 attempts are not repeated`() = runTest {
        questions.seed(*(1..6).map { question("q$it") }.toTypedArray())
        fun finished(id: String, minutesAgo: Long, right: List<String>) = Simulacrum(
            id = id, courseId = "c1", questionIds = right, startedAt = TEST_NOW - Duration.ofMinutes(minutesAgo + 20),
            finishedAt = TEST_NOW - Duration.ofMinutes(minutesAgo), answers = right.map { SimulacrumAnswer(it, 1, true) },
        )
        simulacra.seed(finished("s1", 10, listOf("q1")), finished("s2", 20, listOf("q2")), finished("s3", 30, listOf("q3")))

        // q3 was right three attempts ago: it may come back.
        assertEquals(setOf("q3", "q4", "q5", "q6"), bank.available("c1").map { it.id }.toSet())
    }

    @Test
    fun `top up adds the missing questions to the running attempt`() = runTest {
        syllabus()
        perBatch()
        questions.seed(question("q1"), question("q2"), question("q3"))
        val attempt = start("c1", questionCount = 8).getOrThrow()

        TopUpSimulacrumUseCase(simulacra, bank, generate)(attempt.id, 8).getOrThrow()

        val stored = simulacra.items.value.getValue(attempt.id)
        assertEquals(8, stored.questionIds.distinct().size)
        assertEquals(attempt.questionIds, stored.questionIds.take(3))
    }

    // Background prefetch

    @Test
    fun `prefetch fills the bank up to 15 and retries or stops depending on the error`() = runTest {
        syllabus()
        perBatch()
        val run = RunPrefetchUseCase(RefillQuestionBankUseCase(bank, generate), TopUpSimulacrumUseCase(simulacra, bank, generate), logger)

        assertEquals(PrefetchOutcome.SUCCESS, run(PrefetchJob("c1")))
        assertEquals(15, bank.available("c1").size)
        assertEquals(PrefetchOutcome.SUCCESS, run(PrefetchJob("c1")))
        assertEquals(15, questions.items.value.size) // already full: nothing generated

        gemini.questionsFor = { Result.failure(AiError.Server(503, "busy")) }
        assertEquals(PrefetchOutcome.RETRY, run(PrefetchJob("c1", fresh = true)))
        gemini.questionsFor = { Result.failure(AiError.Configuration("API key not valid")) }
        assertEquals(PrefetchOutcome.FAIL, run(PrefetchJob("c1", fresh = true)))
        assertEquals(PrefetchOutcome.FAIL, run(PrefetchJob("other-course")))
    }

    @Test
    fun `saving an exam or lab with new material starts the prefetch`() = runTest {
        val save = SaveEvaluationUseCase(evaluations, prefetcher)
        val withSyllabus = exam("e1", 30).copy(syllabus = "Cinemática")

        save(exam("e0", 30)).getOrThrow()                    // no material
        save(withSyllabus).getOrThrow()                       // new material
        save(withSyllabus.copy(title = "Parcial 1")).getOrThrow() // same material
        save(Lab(id = "l1", courseId = "c1", title = "Lab", difficulty = 2, createdAt = TEST_NOW, dueAt = TEST_NOW, guidePdfPath = "/g.pdf")).getOrThrow()

        assertEquals(listOf("c1" to true, "c1" to true), prefetcher.refills)
    }

    // Feedback

    @Test
    fun `a reported question leaves the bank and is replaced`() = runTest {
        questions.seed(question("q1"), question("q2"))

        ReportQuestionUseCase(questions, prefetcher, logger)(question("q1"), QuestionReport.CONFUSING).getOrThrow()

        assertEquals(QuestionReport.CONFUSING, questions.items.value.getValue("q1").reportReason)
        assertEquals(listOf("q2"), bank.available("c1").map { it.id })
        assertEquals(listOf("c1" to false), prefetcher.refills)
    }

    // Explanations

    @Test
    fun `explanations are cached per question and answer`() = runTest {
        val explain = ExplainMistakeUseCase(gemini, recommendations, courses, testClock)
        val q = question("q1")

        assertEquals("La respuesta correcta es **B**.", explain(q, 2).getOrThrow())
        explain(q, 2)
        assertEquals(1, gemini.explainCalls)

        explain(q, null) // a different answer is a different explanation
        assertEquals(2, gemini.explainCalls)
    }

    @Test
    fun `a failed explanation is not cached`() = runTest {
        val explain = ExplainMistakeUseCase(gemini, recommendations, courses, testClock)
        gemini.explainResult = Result.failure(IOException("offline"))

        assertTrue(explain(question("q1"), 0).isFailure)
        gemini.explainResult = Result.success("Ahora sí")
        assertEquals("Ahora sí", explain(question("q1"), 0).getOrThrow())
        assertEquals(2, gemini.explainCalls)
        assertFalse(logger.lines.any { it.contains("offline") && it.contains("key") })
    }
}
