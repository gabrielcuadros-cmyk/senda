package com.fuerzasalvaje.senda.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DomainModelTest {

    private val now = Instant.parse("2026-10-12T08:00:00Z")

    private fun project(vararg statuses: PhaseStatus) = Project(
        id = "p", courseId = "c", title = "P", difficulty = 3, createdAt = now,
        phases = statuses.mapIndexed { i, s -> ProjectPhase("f$i", "Fase $i", now + Duration.ofDays(i + 1L), s) },
    )

    @Test
    fun `difficulty colors follow D-3`() {
        assertEquals(DifficultyLevel.EASY, SendaRules.difficultyLevel(1))
        assertEquals(DifficultyLevel.EASY, SendaRules.difficultyLevel(2))
        assertEquals(DifficultyLevel.MEDIUM, SendaRules.difficultyLevel(3))
        assertEquals(DifficultyLevel.HARD, SendaRules.difficultyLevel(4))
        assertEquals(DifficultyLevel.HARD, SendaRules.difficultyLevel(5))
    }

    @Test
    fun `suggested study hours follow D-7`() {
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 10.0), (1..5).map(SendaRules::suggestedStudyHours))
    }

    @Test
    fun `weight must be greater than 0 and at most 100`() {
        assertFalse(SendaRules.isValidWeight(0.0))
        assertTrue(SendaRules.isValidWeight(0.5))
        assertTrue(SendaRules.isValidWeight(100.0))
        assertFalse(SendaRules.isValidWeight(100.1))
        assertTrue(SendaRules.courseWeightExceeded(listOf(60.0, 50.0)))
        assertFalse(SendaRules.courseWeightExceeded(listOf(60.0, 40.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exam rejects zero weight`() {
        Exam(id = "e", courseId = "c", title = "E", weightPercent = 0.0, difficulty = 3, createdAt = now,
            startAt = now, examType = ExamType.FINAL)
    }

    @Test
    fun `labs and projects default to 10 percent and suggested hours`() {
        val lab = Lab(id = "l", courseId = "c", title = "L", difficulty = 4, createdAt = now, dueAt = now)
        assertEquals(10.0, lab.weightPercent, 0.0)
        assertEquals(8.0, lab.studyHours, 0.0)
        assertEquals(10.0, project(PhaseStatus.PENDING).weightPercent, 0.0)
    }

    @Test
    fun `project status derives from its phases (D-17)`() {
        assertEquals(EvaluationStatus.PENDING, project(PhaseStatus.PENDING, PhaseStatus.PENDING).status)
        assertEquals(EvaluationStatus.IN_PROGRESS, project(PhaseStatus.DELIVERED, PhaseStatus.PENDING).status)
        assertEquals(EvaluationStatus.IN_PROGRESS, project(PhaseStatus.IN_PROGRESS).status)
        assertEquals(EvaluationStatus.COMPLETED, project(PhaseStatus.DELIVERED, PhaseStatus.DELIVERED, PhaseStatus.DELIVERED).status)
        assertEquals(EvaluationStatus.COMPLETED, project(PhaseStatus.DELIVERED).status)
    }

    @Test
    fun `simulacrum grade uses the 0 to 20 scale`() {
        val simulacrum = Simulacrum(
            id = "s", courseId = "c", startedAt = now,
            questionIds = listOf("q1", "q2", "q3", "q4"),
            answers = listOf(
                SimulacrumAnswer("q1", 0, true),
                SimulacrumAnswer("q2", 1, true),
                SimulacrumAnswer("q3", 2, false),
                SimulacrumAnswer("q4", null, false),
            ),
        )
        assertEquals(10.0, simulacrum.grade, 1e-9)
        assertEquals(2, simulacrum.correctCount)
        assertEquals(1, simulacrum.incorrectCount)
        assertEquals(1, simulacrum.unansweredCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stress check-in rejects values outside 1 to 5`() {
        StressCheckIn(date = java.time.LocalDate.of(2026, 10, 12), level = 6, createdAt = now)
    }
}
