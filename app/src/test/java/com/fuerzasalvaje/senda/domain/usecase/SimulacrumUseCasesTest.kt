package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.usecase.simulacrum.CourseSyllabusUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ExplainMistakeUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.MissingSyllabusException
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.NoValidQuestionsException
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrepareQuestionsUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.StartSimulacrumUseCase
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeGeminiService
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class SimulacrumUseCasesTest {

    private val courses = FakeCourseRepository().apply { seed(course()) }
    private val evaluations = FakeEvaluationRepository()
    private val questions = FakeQuestionRepository()
    private val simulacra = FakeSimulacrumRepository()
    private val gemini = FakeGeminiService()
    private val recommendations = FakeRecommendationRepository()

    private val prepare = PrepareQuestionsUseCase(questions, courses, CourseSyllabusUseCase(evaluations), gemini, testClock)

    @Test
    fun `an existing bank is reused without calling the AI`() = runTest {
        questions.seed(*(1..10).map { question("q$it") }.toTypedArray())

        assertEquals(10, prepare("c1", 10).getOrThrow().size)
        assertEquals(0, gemini.generateCalls)
    }

    @Test
    fun `without syllabus the student must type the topics`() = runTest {
        assertTrue(prepare("c1", 10).exceptionOrNull() is MissingSyllabusException)

        prepare("c1", 10, topics = "Leyes de Newton").getOrThrow()
        assertEquals("Leyes de Newton", gemini.lastSyllabus)
    }

    @Test
    fun `questions are generated from the exam syllabus and only valid ones are stored`() = runTest {
        evaluations.seed(exam("Parcial 1", 30).copy(syllabus = "Cinemática y dinámica"))
        gemini.questionsResult = Result.success(
            (1..8).map(::generated) + generated(9).copy(options = listOf("A", "B")) + generated(10).copy(explanation = "")
        )

        val bank = prepare("c1", 10).getOrThrow()

        assertEquals("Parcial 1: Cinemática y dinámica", gemini.lastSyllabus)
        assertEquals(8, bank.size)
        assertEquals(8, questions.items.value.size)
        assertTrue(questions.items.value.values.any { it.isCase })
    }

    @Test
    fun `no valid question is an explicit failure`() = runTest {
        evaluations.seed(exam("Parcial 1", 30).copy(syllabus = "Óptica"))
        gemini.questionsResult = Result.success(listOf(generated(1).copy(options = emptyList())))

        assertTrue(prepare("c1", 10).exceptionOrNull() is NoValidQuestionsException)
        assertTrue(questions.items.value.isEmpty())
    }

    @Test
    fun `a new attempt takes the configured number of questions and minutes`() = runTest {
        questions.seed(*(1..15).map { question("q$it") }.toTypedArray())
        val start = StartSimulacrumUseCase(prepare, simulacra, testClock, Random(7))

        val attempt = start("c1", questionCount = 5, minutes = 10).getOrThrow()

        assertEquals(5, attempt.questionIds.distinct().size)
        assertEquals(10, attempt.timeLimitMinutes)
        assertEquals(TEST_NOW, attempt.startedAt)
        assertEquals(attempt, simulacra.items.value.getValue(attempt.id))
    }

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
    }
}
