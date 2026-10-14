package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ReminderPlannerTest {

    private val courses = listOf(course())
    private val setting = ReminderSetting()

    private fun entries(vararg evaluations: Evaluation) = CalendarPlanner.entries(evaluations.toList(), courses)

    @Test
    fun `default reminders are 3 days, 1 day and 2 hours before`() {
        val reminders = ReminderPlanner.evaluationReminders(entries(exam("a", inHours = 100)), setting, TEST_NOW)

        assertEquals(listOf(Duration.ofDays(3), Duration.ofDays(1), Duration.ofHours(2)), reminders.map { it.offset })
        assertEquals(TEST_NOW + Duration.ofHours(100 - 72), reminders.first().triggerAt)
    }

    @Test
    fun `reminders already in the past are skipped`() {
        val reminders = ReminderPlanner.evaluationReminders(entries(exam("a", inHours = 10)), setting, TEST_NOW)

        assertEquals(listOf(Duration.ofHours(2)), reminders.map { it.offset })
    }

    @Test
    fun `completed evaluations and delivered phases get no reminders`() {
        val done = exam("a", inHours = 100).copy(status = EvaluationStatus.COMPLETED)
        val project = Project(
            id = "p", courseId = "c1", title = "Proyecto", difficulty = 3, createdAt = TEST_NOW,
            phases = listOf(
                ProjectPhase("f1", "Avance 1", TEST_NOW + Duration.ofHours(30), PhaseStatus.DELIVERED),
                ProjectPhase("f2", "Entrega Final", TEST_NOW + Duration.ofHours(30)),
            ),
        )

        val reminders = ReminderPlanner.evaluationReminders(entries(done, project), setting, TEST_NOW)

        assertEquals(setOf("p:f2"), reminders.map { it.entry.id }.toSet())
    }

    @Test
    fun `offsets are configurable and reminders can be turned off`() {
        val custom = setting.copy(evaluationOffsets = listOf(Duration.ofHours(5)))
        val list = entries(exam("a", inHours = 100))

        assertEquals(listOf(Duration.ofHours(5)), ReminderPlanner.evaluationReminders(list, custom, TEST_NOW).map { it.offset })
        assertTrue(ReminderPlanner.evaluationReminders(list, setting.copy(enabled = false), TEST_NOW).isEmpty())
    }

    @Test
    fun `reminder keys are unique per entry and offset`() {
        val reminders = ReminderPlanner.evaluationReminders(
            entries(exam("a", inHours = 100), exam("b", inHours = 100)), setting, TEST_NOW,
        )

        assertEquals(6, reminders.map { it.key }.toSet().size)
    }

    @Test
    fun `back to back exams on the same day get a routine when the first one ends`() {
        // 09:00–11:00 and 11:30–13:30 on the same day.
        val first = exam("a", inHours = 1)
        val second = exam("b", inHours = 1).copy(startAt = TEST_NOW + Duration.ofMinutes(210))

        val routine = ReminderPlanner.examRoutines(entries(first, second), TEST_ZONE, TEST_NOW).single()

        assertEquals(TEST_NOW + Duration.ofHours(3), routine.triggerAt)
        assertEquals("a" to "b", routine.finished.id to routine.next.id)
        assertEquals(Duration.ofMinutes(30), routine.gap)
    }

    @Test
    fun `overlapping exams, labs and exams on different days get no routine`() {
        val overlapping = entries(exam("a", inHours = 1), exam("b", inHours = 2))
        val otherDay = entries(exam("a", inHours = 1), exam("b", inHours = 30))
        val withLab = entries(
            exam("a", inHours = 1),
            Lab(id = "l", courseId = "c1", title = "Lab", difficulty = 2, createdAt = TEST_NOW, dueAt = TEST_NOW + Duration.ofHours(4)),
        )

        assertTrue(ReminderPlanner.examRoutines(overlapping, TEST_ZONE, TEST_NOW).isEmpty())
        assertTrue(ReminderPlanner.examRoutines(otherDay, TEST_ZONE, TEST_NOW).isEmpty())
        assertTrue(ReminderPlanner.examRoutines(withLab, TEST_ZONE, TEST_NOW).isEmpty())
    }
}
