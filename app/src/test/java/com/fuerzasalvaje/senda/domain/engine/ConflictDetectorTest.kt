package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.ConflictType
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class ConflictDetectorTest {

    private val courses = listOf(course())
    private val today = LocalDate.of(2026, 10, 12)

    private fun detect(vararg evaluations: com.fuerzasalvaje.senda.domain.model.Evaluation) =
        ConflictDetector.detect(CalendarPlanner.entries(evaluations.toList(), courses), TEST_ZONE)

    private fun lab(id: String, inHours: Long) = Lab(
        id = id, courseId = "c1", title = id, difficulty = 2, createdAt = TEST_NOW, dueAt = TEST_NOW + Duration.ofHours(inHours),
    )

    @Test
    fun `overlapping exams are a red clash and the day is not yellow`() {
        val conflicts = detect(exam("a", inHours = 1, durationHours = 2), exam("b", inHours = 2, durationHours = 1))

        assertEquals(listOf(ConflictType.CLASH), conflicts.map { it.type })
        assertEquals(setOf("a", "b"), conflicts.single().entries.map { it.id }.toSet())
        assertEquals(today, conflicts.single().date)
    }

    @Test
    fun `end equal to start is not a clash but makes a busy day`() {
        val conflicts = detect(exam("a", inHours = 1, durationHours = 2), exam("b", inHours = 3, durationHours = 1))

        assertEquals(listOf(ConflictType.BUSY_DAY), conflicts.map { it.type })
        assertEquals(2, conflicts.single().entries.size)
    }

    @Test
    fun `default durations are 2 hours for exams and 1 hour for labs`() {
        // Exam 09:00–11:00 by default; a lab at 10:30 overlaps it.
        assertEquals(ConflictType.CLASH, detect(exam("a", inHours = 1), lab("l", inHours = 2).copy(dueAt = TEST_NOW + Duration.ofMinutes(150))).first().type)
        // Exam at 09:00 ends 11:00 by default: an exam at 11:00 does not overlap.
        assertEquals(listOf(ConflictType.BUSY_DAY), detect(exam("a", inHours = 1), exam("b", inHours = 3)).map { it.type })
    }

    @Test
    fun `project deliveries never clash but count for a busy day`() {
        val project = Project(
            id = "p", courseId = "c1", title = "Proyecto", difficulty = 3, createdAt = TEST_NOW,
            phases = listOf(ProjectPhase("f1", "Avance 1", TEST_NOW + Duration.ofMinutes(90))),
        )

        val conflicts = detect(exam("a", inHours = 1, durationHours = 2), project)

        assertEquals(listOf(ConflictType.BUSY_DAY), conflicts.map { it.type })
    }

    @Test
    fun `clash and busy day can happen on the same day`() {
        val conflicts = detect(
            exam("a", inHours = 1, durationHours = 2),
            exam("b", inHours = 2, durationHours = 2),
            exam("c", inHours = 6, durationHours = 1),
        )

        assertEquals(listOf(ConflictType.CLASH, ConflictType.BUSY_DAY), conflicts.map { it.type })
        assertEquals(3, conflicts.last().entries.size)
        assertEquals(setOf("a", "b"), ConflictDetector.clashingIds(conflicts))
    }

    @Test
    fun `completed evaluations and delivered phases are ignored`() {
        val done = exam("done", inHours = 1, durationHours = 2).copy(status = EvaluationStatus.COMPLETED)
        val delivered = Project(
            id = "p", courseId = "c1", title = "P", difficulty = 3, createdAt = TEST_NOW,
            phases = listOf(ProjectPhase("f", "Entrega", TEST_NOW + Duration.ofHours(4), PhaseStatus.DELIVERED)),
        )

        assertTrue(detect(done, exam("b", inHours = 2, durationHours = 1), delivered).isEmpty())
    }

    @Test
    fun `evaluations on different days do not conflict`() {
        assertTrue(detect(exam("a", inHours = 1), exam("b", inHours = 25)).isEmpty())
    }
}
