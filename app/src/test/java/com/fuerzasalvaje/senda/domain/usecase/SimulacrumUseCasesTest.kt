package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.engine.PdfMarkdown
import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PdfText
import com.fuerzasalvaje.senda.domain.model.QuestionReport
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.SimulacrumAnswer
import com.fuerzasalvaje.senda.domain.repository.AiQuota
import com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.ExtractPdfMarkdownUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.AnswerQuestionUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.CourseMaterialUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ExplainMistakeUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateCourseQuestionsUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateQuestionSetUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.MissingSyllabusException
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.NoValidQuestionsException
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ObserveSimulacrumUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrefetchJob
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrefetchOutcome
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrepareQuestionsUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.QuestionBankUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.RefillQuestionBankUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ReportQuestionUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.RunPrefetchUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.StartSimulacrumUseCase
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.testing.FakeAiUsageStore
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeGeminiService
import com.fuerzasalvaje.senda.testing.FakePdfTextExtractor
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private val extractor = FakePdfTextExtractor()
    private val usage = FakeAiUsageStore()

    private val material = CourseMaterialUseCase(evaluations, ExtractPdfMarkdownUseCase(extractor, logger, testClock))
    private val bank = QuestionBankUseCase(questions, simulacra)
    private val generate = GenerateCourseQuestionsUseCase(
        questions, courses, material, GenerateQuestionSetUseCase(gemini, logger, Random(3)), usage, testClock, logger,
    )
    private val prepare = PrepareQuestionsUseCase(generate)
    private val refill = RefillQuestionBankUseCase(bank, material, generate, usage, logger)
    private val start = StartSimulacrumUseCase(bank, prepare, simulacra, prefetcher, testClock, Random(7))

    /** Gemini answers with as many distinct questions about [topic] as requested. */
    private fun answers(topic: String = "cinemática", offset: Int = 0) {
        gemini.questionsFor = { request -> Result.success((1..request.count).map { generated(offset + it, topic) }) }
    }

    private fun syllabus(text: String = "Cinemática y dinámica de partículas") =
        evaluations.seed(exam("Parcial 1", 30).copy(syllabus = text))

    private fun finished(id: String, questionIds: List<String>, right: Set<String>, minutesAgo: Long = 30) = Simulacrum(
        id = id, courseId = "c1", questionIds = questionIds,
        startedAt = TEST_NOW - Duration.ofMinutes(minutesAgo + 20), finishedAt = TEST_NOW - Duration.ofMinutes(minutesAgo),
        answers = questionIds.map { SimulacrumAnswer(it, if (it in right) 1 else 0, it in right) },
    )

    // 1. One call per bank

    @Test
    fun `the whole bank of 15 questions comes from ONE Gemini call, with no review call`() = runTest {
        syllabus()
        answers()

        val created = generate("c1", 15).getOrThrow()

        assertEquals(1, gemini.generateCalls)
        assertEquals(15, gemini.requests.single().count)
        assertEquals(15, created.size)
        assertEquals(15, questions.items.value.size)
        assertTrue(logger.lines.any { it.contains("in 1 call") })
    }

    @Test
    fun `a failed call reports its error and stores nothing`() = runTest {
        syllabus()
        gemini.questionsFor = { Result.failure(AiError.Configuration("API key not valid")) }

        val error = generate("c1", 15).exceptionOrNull()

        assertTrue(error is AiError.Configuration && error.invalidKey)
        assertTrue(questions.items.value.isEmpty())
    }

    @Test
    fun `duplicates, unrelated questions and catch-all options are still discarded in code`() = runTest {
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
    fun `answers all in the same position are spread over A, B, C and D`() = runTest {
        syllabus()
        gemini.questionsFor = { Result.success((1..5).map { generated(it).copy(correctOptionIndex = 0) }) }

        val created = generate("c1", 5).getOrThrow()

        val top = created.groupingBy { it.correctOptionIndex }.eachCount().values.max()
        assertTrue("most repeated position: $top of 5", top * 2 <= 5)
        created.forEach { q -> assertTrue(q.options[q.correctOptionIndex].startsWith("A")) }
    }

    // Material (text only)

    @Test
    fun `without syllabus the student must type the topics`() = runTest {
        assertTrue(prepare("c1").exceptionOrNull() is MissingSyllabusException)

        answers(topic = "leyes de newton")
        prepare("c1", topics = "Leyes de Newton y fuerzas").getOrThrow()
        assertEquals("Leyes de Newton y fuerzas", gemini.lastSyllabus)
    }

    @Test
    fun `exam syllabus, lab guides and the PDF Markdown are the material, as text only`() = runTest {
        evaluations.seed(
            exam("Parcial 1", 30).copy(
                syllabus = "Unidad 2", syllabusPdfPath = "/files/pdfs/temario.pdf",
                syllabusPdfMarkdown = "## Diapositiva 3\n- Ley de Ohm: V = I x R",
            ),
            Lab(id = "lab", courseId = "c1", title = "Péndulo", guideNumber = "3", difficulty = 2, createdAt = TEST_NOW,
                dueAt = TEST_NOW + Duration.ofDays(2), guidePdfPath = "/files/pdfs/guia.pdf",
                guidePdfMarkdown = "## Página 2\n- Periodo T = 2 s con L = 1 m"),
        )
        answers(topic = "péndulo")

        prepare("c1").getOrThrow()

        val request = gemini.lastRequest!!
        assertEquals("Parcial 1: Unidad 2\nLaboratorio 3 Péndulo", request.syllabus)
        assertTrue(request.pdfMarkdown.contains("# PDF de \"Parcial 1\"\n## Diapositiva 3\n- Ley de Ohm: V = I x R"))
        assertTrue(request.pdfMarkdown.contains("## Página 2\n- Periodo T = 2 s con L = 1 m"))
        assertTrue(extractor.calls.isEmpty())
    }

    @Test
    fun `a PDF attached before the Markdown existed is read once on the device and kept`() = runTest {
        evaluations.seed(exam("Parcial 1", 30).copy(syllabus = "Ley de Ohm", syllabusPdfPath = "/files/pdfs/temario.pdf"))
        answers(topic = "resistencia")

        prepare("c1").getOrThrow()
        generate("c1", 15).getOrThrow()

        assertEquals(listOf("/files/pdfs/temario.pdf"), extractor.calls)
        val stored = evaluations.items.value.values.single() as Exam
        assertTrue(stored.syllabusPdfMarkdown.startsWith("## Diapositiva 1\n- Ley de Ohm"))
        assertTrue(gemini.lastRequest!!.pdfMarkdown.contains("## Diapositiva 2"))
    }

    @Test
    fun `a PDF without readable text is never sent and questions come from the syllabus`() = runTest {
        extractor.result = Result.success(PdfText(listOf("12", "", "Tecsup")))
        evaluations.seed(exam("Parcial 1", 30).copy(syllabus = "Cinemática y dinámica", syllabusPdfPath = "/files/pdfs/escaneado.pdf"))
        answers()

        prepare("c1").getOrThrow()

        assertEquals("", gemini.lastRequest!!.pdfMarkdown)
        assertEquals("Parcial 1: Cinemática y dinámica", gemini.lastRequest!!.syllabus)
    }

    @Test
    fun `several PDFs share the 12000 character budget`() = runTest {
        val long = (1..120).joinToString("\n\n") { "## Diapositiva $it\n- Tema $it: velocidad v = $it m/s en el tramo $it" }
        evaluations.seed(exam("Parcial 1", 30).copy(syllabusPdfMarkdown = long), exam("Parcial 2", 30).copy(syllabusPdfMarkdown = long))
        answers(topic = "velocidad")

        prepare("c1").getOrThrow()

        val headers = "# PDF de \"Parcial 1\"\n".length * 2 + 2
        assertTrue(gemini.lastRequest!!.pdfMarkdown.length <= PdfMarkdown.MAX_CHARS + headers)
    }

    @Test
    fun `fewer than 3 valid questions is an error`() = runTest {
        syllabus()
        gemini.questionsFor = { Result.success(listOf(generated(1), generated(2))) }

        val error = prepare("c1").exceptionOrNull() as NoValidQuestionsException
        assertEquals(2, error.valid)
    }

    // 2. Prefetch only when needed

    @Test
    fun `refill calls Gemini only when the material changed or fewer than 5 unused questions are left`() = runTest {
        syllabus()
        answers()

        assertEquals(15, refill("c1").getOrThrow())           // new material
        assertEquals(0, refill("c1").getOrThrow())            // same material, 15 unused
        assertEquals(1, gemini.generateCalls)

        // Dates, weights or titles do not change the material.
        val exam = evaluations.items.value.values.single() as Exam
        evaluations.save(exam.copy(title = "Parcial 1 (aula 302)", weightPercent = 35.0, startAt = exam.startAt + Duration.ofDays(1)))
        assertEquals(0, refill("c1").getOrThrow())
        assertEquals(1, gemini.generateCalls)

        // 11 of the 15 asked: only 4 unused are left.
        val ids = questions.items.value.keys.take(11)
        simulacra.seed(finished("s1", ids, right = ids.toSet()))
        answers(offset = 100)
        assertEquals(15, refill("c1").getOrThrow())
        assertEquals(2, gemini.generateCalls)

        // A new syllabus is new material, even with a full bank.
        evaluations.save(exam.copy(syllabus = "Cinemática, dinámica y energía mecánica"))
        answers(offset = 200)
        refill("c1").getOrThrow()
        assertEquals(3, gemini.generateCalls)
        assertTrue(logger.lines.any { it.contains("Refill skipped for course c1: 15 unused questions and the same material") })
    }

    @Test
    fun `saving an evaluation queues the refill only when the syllabus or the PDF content changed`() = runTest {
        val save = SaveEvaluationUseCase(evaluations, prefetcher)
        val withSyllabus = exam("e1", 30).copy(syllabus = "Cinemática")

        save(exam("e0", 30)).getOrThrow()                                         // no material
        save(withSyllabus).getOrThrow()                                            // new syllabus
        save(withSyllabus.copy(title = "Parcial 1", weightPercent = 40.0)).getOrThrow() // title and weight
        save(withSyllabus.copy(startAt = withSyllabus.startAt + Duration.ofDays(2))).getOrThrow() // new date
        save(withSyllabus.copy(status = com.fuerzasalvaje.senda.domain.model.EvaluationStatus.COMPLETED)).getOrThrow()
        save(withSyllabus.copy(syllabusPdfMarkdown = "## Diapositiva 1\n- v = d / t")).getOrThrow() // new PDF content

        assertEquals(listOf("c1", "c1"), prefetcher.refills)
    }

    @Test
    fun `background prefetch stops at the daily limit and while the AI rests, without retries`() = runTest {
        syllabus()
        answers()
        var allowed = false
        var resting = false
        val quota = object : AiQuota by AiQuota.None {
            override suspend fun allowsBackground() = allowed && !resting
            override suspend fun isResting() = resting
        }
        val run = RunPrefetchUseCase(refill, quota, logger)

        assertEquals(PrefetchOutcome.SUCCESS, run(PrefetchJob("c1")))
        assertEquals(0, gemini.generateCalls)
        assertTrue(logger.lines.any { it.contains("Prefetch skipped for course c1") })

        allowed = true
        gemini.questionsFor = { Result.failure(AiError.Resting()) }
        assertEquals(PrefetchOutcome.SUCCESS, run(PrefetchJob("c1"))) // no retry: the bank keeps serving
        gemini.questionsFor = { Result.failure(AiError.Server(503, "busy")) }
        assertEquals(PrefetchOutcome.RETRY, run(PrefetchJob("c1")))
        gemini.questionsFor = { Result.failure(AiError.Configuration("API key not valid")) }
        assertEquals(PrefetchOutcome.FAIL, run(PrefetchJob("c1")))
        assertEquals(PrefetchOutcome.FAIL, run(PrefetchJob("other-course")))
    }

    // 3. Attempts come from the bank

    @Test
    fun `a full bank starts at once without calling the AI and without refilling while 5 or more are unused`() = runTest {
        questions.seed(*(1..15).map { question("q$it") }.toTypedArray())

        val attempt = start("c1", questionCount = 10, minutes = 10).getOrThrow()

        assertEquals(10, attempt.questionIds.distinct().size)
        assertEquals(10, attempt.timeLimitMinutes)
        assertTrue(attempt.optionOrders.isEmpty()) // new questions keep their order
        assertEquals(0, gemini.generateCalls)
        assertTrue(prefetcher.refills.isEmpty()) // 5 unused left

        start("c1", questionCount = 5).getOrThrow()
        assertEquals(listOf("c1"), prefetcher.refills) // 0 unused left
        assertEquals(0, gemini.generateCalls)
    }

    @Test
    fun `when every question was used they are reused, failed ones first, in new order and with mixed options`() = runTest {
        questions.seed(*(1..6).map { question("q$it") }.toTypedArray())
        simulacra.seed(finished("s1", listOf("q1", "q2", "q3", "q4", "q5", "q6"), right = setOf("q4", "q5", "q6")))

        val attempt = start("c1", questionCount = 3).getOrThrow()

        assertEquals(setOf("q1", "q2", "q3"), attempt.questionIds.toSet())
        assertEquals(setOf("q1", "q2", "q3"), attempt.optionOrders.keys)
        attempt.optionOrders.values.forEach { assertNotEquals(listOf(0, 1, 2, 3), it) }
        assertEquals(0, gemini.generateCalls)

        // The attempt shows the mixed options; the right answer is still the same text.
        val shown = ObserveSimulacrumUseCase(simulacra, questions)(attempt.id).first()!!.questions
        shown.forEach { q ->
            val stored = questions.items.value.getValue(q.id)
            assertEquals(stored.options[stored.correctOptionIndex], q.options[q.correctOptionIndex])
            assertNotEquals(stored.options, q.options)
        }
        // And it is graded on the order shown.
        val first = shown.first()
        val answered = AnswerQuestionUseCase(simulacra).apply(attempt, first, first.correctOptionIndex)
        assertTrue(answered.answers.single { it.questionId == first.id }.correct)
    }

    @Test
    fun `with fewer than 3 questions the whole bank is generated in one call before starting`() = runTest {
        syllabus()
        answers()
        questions.seed(question("q1"))

        val attempt = start("c1", questionCount = 5).getOrThrow()

        assertEquals(5, attempt.questionIds.size)
        assertEquals(1, gemini.generateCalls)
        assertEquals(15, gemini.requests.single().count)
    }

    @Test
    fun `reporting a question keeps the stored options and refills only when the bank is low`() = runTest {
        questions.seed(*(1..6).map { question("q$it") }.toTypedArray())
        val report = ReportQuestionUseCase(questions, bank, prefetcher, logger)

        // The attempt showed q1 with its options mixed.
        report(question("q1").withOptionOrder(listOf(3, 2, 1, 0)), QuestionReport.CONFUSING).getOrThrow()

        val stored = questions.items.value.getValue("q1")
        assertEquals(QuestionReport.CONFUSING, stored.reportReason)
        assertEquals(listOf("A", "B", "C", "D"), stored.options)
        assertEquals(5, bank.unused("c1").size)
        assertTrue(prefetcher.refills.isEmpty())

        report(question("q2"), QuestionReport.WRONG).getOrThrow()
        assertEquals(listOf("c1"), prefetcher.refills)
    }

    // 4. Cached explanations

    @Test
    fun `an explanation is asked once per question and answer, whatever order the options were shown in`() = runTest {
        val explain = ExplainMistakeUseCase(gemini, recommendations, courses, testClock)
        val q = question("q1")

        assertEquals("La respuesta correcta es **B**.", explain(q, 2).getOrThrow()) // answered "C"
        explain(q, 2)
        explain(q.withOptionOrder(listOf(2, 0, 1, 3)), 0) // "C" again, shown first in a later attempt
        assertEquals(1, gemini.explainCalls)

        explain(q, null) // a different answer is a different explanation
        assertEquals(2, gemini.explainCalls)
    }

    @Test
    fun `simultaneous requests for the same explanation call Gemini once`() = runTest {
        val explain = ExplainMistakeUseCase(gemini, recommendations, courses, testClock)
        gemini.explainDelayMillis = 1_000

        listOf(async { explain(question("q9"), 0) }, async { explain(question("q9"), 0) }).awaitAll()

        assertEquals(1, gemini.explainCalls)
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
