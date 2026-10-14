package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.SimulacrumAnswer
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.generated
import com.fuerzasalvaje.senda.testing.question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class SimulacrumEngineTest {

    private fun finished(id: String, correct: Int, total: Int = 10, minutesAgo: Long) = Simulacrum(
        id = id, courseId = "c1",
        questionIds = (1..total).map { "q$it" },
        answers = (1..total).map { SimulacrumAnswer("q$it", 0, it <= correct) },
        startedAt = TEST_NOW - Duration.ofMinutes(minutesAgo + 20),
        finishedAt = TEST_NOW - Duration.ofMinutes(minutesAgo),
    )

    @Test
    fun `generated questions need 4 distinct options, a valid answer and an explanation`() {
        assertTrue(QuestionValidator.isValid(generated(1)))
        assertFalse(QuestionValidator.isValid(generated(1).copy(options = listOf("A", "B", "C"))))
        assertFalse(QuestionValidator.isValid(generated(1).copy(options = listOf("A", "a", "C", "D"))))
        assertFalse(QuestionValidator.isValid(generated(1).copy(options = listOf("A", " ", "C", "D"))))
        assertFalse(QuestionValidator.isValid(generated(1).copy(correctOptionIndex = 4)))
        assertFalse(QuestionValidator.isValid(generated(1).copy(explanation = "")))
        assertFalse(QuestionValidator.isValid(generated(1).copy(statement = " ")))
    }

    @Test
    fun `grade is on the 0 to 20 scale with one decimal`() {
        assertEquals(14.0, SimulacrumScoring.roundedGrade(finished("a", correct = 7, minutesAgo = 1)), 0.0)
        assertEquals(13.3, SimulacrumScoring.roundedGrade(finished("b", correct = 2, total = 3, minutesAgo = 1)), 0.0)
        assertEquals(0.0, SimulacrumScoring.roundedGrade(finished("c", correct = 0, minutesAgo = 1)), 0.0)
    }

    @Test
    fun `answers are scored by the engine and finishing records the unanswered ones`() {
        val q1 = question("q1", correct = 2)
        val q2 = question("q2", correct = 0)
        var attempt = Simulacrum(id = "s", courseId = "c1", questionIds = listOf("q1", "q2", "q3"), startedAt = TEST_NOW)

        attempt = SimulacrumScoring.answer(attempt, q1, 1)
        attempt = SimulacrumScoring.answer(attempt, q1, 2) // changing the answer replaces it
        attempt = SimulacrumScoring.answer(attempt, q2, 3)
        val done = SimulacrumScoring.finish(attempt, TEST_NOW + Duration.ofHours(2))

        assertEquals(1, done.correctCount)
        assertEquals(1, done.incorrectCount)
        assertEquals(1, done.unansweredCount)
        assertEquals(SimulacrumTimer.deadline(attempt), done.finishedAt) // never after the deadline
    }

    @Test
    fun `timer counts down from timestamps and never goes negative`() {
        val attempt = Simulacrum(id = "s", courseId = "c1", questionIds = listOf("q1"), timeLimitMinutes = 20, startedAt = TEST_NOW)

        assertEquals(Duration.ofMinutes(15), SimulacrumTimer.remaining(attempt, TEST_NOW + Duration.ofMinutes(5)))
        assertEquals(Duration.ZERO, SimulacrumTimer.remaining(attempt, TEST_NOW + Duration.ofMinutes(30)))
        assertTrue(SimulacrumTimer.isExpired(attempt, TEST_NOW + Duration.ofMinutes(20)))
    }

    @Test
    fun `readiness weights the last three attempts 0_5, 0_3 and 0_2`() {
        val attempts = listOf(
            finished("oldest", correct = 0, minutesAgo = 400), // ignored: only the last 3 count
            finished("third", correct = 5, minutesAgo = 300),  // 50 %
            finished("second", correct = 6, minutesAgo = 200), // 60 %
            finished("latest", correct = 9, minutesAgo = 100), // 90 %
        )
        // 0.5 x 90 + 0.3 x 60 + 0.2 x 50 = 73
        assertEquals(73, ReadinessCalculator.readiness(attempts))
    }

    @Test
    fun `readiness rescales the weights when there are fewer attempts`() {
        // (0.5 x 80 + 0.3 x 40) / 0.8 = 65
        assertEquals(65, ReadinessCalculator.readiness(listOf(finished("a", 8, minutesAgo = 10), finished("b", 4, minutesAgo = 50))))
        assertEquals(80, ReadinessCalculator.readiness(listOf(finished("a", 8, minutesAgo = 10))))
        val running = Simulacrum(id = "r", courseId = "c1", questionIds = listOf("q1"), startedAt = TEST_NOW)
        assertNull(ReadinessCalculator.readiness(listOf(running)))
        assertNull(ReadinessCalculator.readiness(emptyList()))
    }
}
