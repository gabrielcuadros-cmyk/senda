package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.engine.PriorityEngine
import com.fuerzasalvaje.senda.domain.model.ClassSlot
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import com.fuerzasalvaje.senda.domain.usecase.plan.AcceptStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.RemoveSuggestedStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.model.StudyBlockEntry
import kotlinx.coroutines.flow.first
import com.fuerzasalvaje.senda.domain.usecase.plan.SuggestStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.pendingStudyMinutes
import com.fuerzasalvaje.senda.domain.usecase.plan.StudyProgress
import com.fuerzasalvaje.senda.domain.usecase.plan.studyProgress
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.testing.FakePomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.SaveStressCheckInUseCase
import com.fuerzasalvaje.senda.testing.FakeAvailabilityRepository
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeStressRepository
import com.fuerzasalvaje.senda.testing.FakeStudyBlockRepository
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.testing.testClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class PlanUseCasesTest {

    private val courses = FakeCourseRepository().apply {
        seed(course().copy(schedule = listOf(ClassSlot(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)))))
    }
    private val evaluations = FakeEvaluationRepository()
    private val blocks = FakeStudyBlockRepository()

    private val sessions = FakePomodoroSessionRepository()

    private fun suggest() = SuggestStudyBlocksUseCase(
        evaluations, courses, blocks, FakeAvailabilityRepository(), sessions, PriorityEngine(testClock), testClock,
    )

    @Test
    fun `completed pomodoros linked to the evaluation reduce the pending minutes`() {
        val target = exam("e", inHours = 50).copy(studyHours = 3.0)
        val block = StudyBlock("b", "c1", "e", TEST_NOW, TEST_NOW + Duration.ofMinutes(50), StudyBlockStatus.SCHEDULED)
        // Two hours after the block, so none of its minutes overlap it.
        val linked = PomodoroSession(
            id = "p1", evaluationId = "e", startedAt = TEST_NOW + Duration.ofHours(2), focusMinutes = 25, completedCycles = 2,
        )
        val other = linked.copy(id = "p2", evaluationId = "x")
        val unlinked = linked.copy(id = "p3", evaluationId = null)

        val progress = studyProgress(target, listOf(block), listOf(linked, other, unlinked))

        assertEquals(StudyProgress(totalMinutes = 180, scheduledMinutes = 50, focusMinutes = 50), progress)
        assertEquals(80, progress.pendingMinutes)
        assertEquals(80, pendingStudyMinutes(target, listOf(block), listOf(linked)))
    }

    @Test
    fun `a pomodoro inside an agreed block of the same evaluation counts once`() {
        val target = exam("e", inHours = 50).copy(studyHours = 3.0)
        // Block 08:00–09:00; Pomodoro 08:20–08:45 inside it, another 09:30–09:55 outside it.
        val block = StudyBlock("b", "c1", "e", TEST_NOW, TEST_NOW + Duration.ofHours(1), StudyBlockStatus.SCHEDULED)
        val inside = PomodoroSession(
            id = "in", evaluationId = "e", startedAt = TEST_NOW + Duration.ofMinutes(20),
            endedAt = TEST_NOW + Duration.ofMinutes(45), focusMinutes = 25, completedCycles = 1,
        )
        val outside = inside.copy(id = "out", startedAt = TEST_NOW + Duration.ofMinutes(90), endedAt = TEST_NOW + Duration.ofMinutes(115))
        // Half inside: 08:50–09:15 overlaps the block for 10 minutes.
        val half = inside.copy(id = "half", startedAt = TEST_NOW + Duration.ofMinutes(50), endedAt = TEST_NOW + Duration.ofMinutes(75))

        assertEquals(0, studyProgress(target, listOf(block), listOf(inside)).focusMinutes)
        assertEquals(25, studyProgress(target, listOf(block), listOf(outside)).focusMinutes)
        assertEquals(15, studyProgress(target, listOf(block), listOf(half)).focusMinutes)
        assertEquals(180 - 60 - 25 - 15, studyProgress(target, listOf(block), listOf(inside, outside, half)).pendingMinutes)
    }

    @Test
    fun `a pomodoro of another evaluation inside the block still counts for its own`() {
        val other = exam("x", inHours = 50).copy(studyHours = 2.0)
        val block = StudyBlock("b", "c1", "e", TEST_NOW, TEST_NOW + Duration.ofHours(1), StudyBlockStatus.SCHEDULED)
        val session = PomodoroSession(
            id = "p", evaluationId = "x", startedAt = TEST_NOW, endedAt = TEST_NOW + Duration.ofMinutes(25),
            focusMinutes = 25, completedCycles = 1,
        )

        assertEquals(25, studyProgress(other, listOf(block), listOf(session)).focusMinutes)
    }

    @Test
    fun `pending minutes never go below zero`() {
        val target = exam("e", inHours = 50).copy(studyHours = 1.0)
        val many = PomodoroSession(id = "p", evaluationId = "e", startedAt = TEST_NOW, focusMinutes = 25, completedCycles = 4)

        assertEquals(0, pendingStudyMinutes(target, emptyList(), listOf(many)))
    }

    @Test
    fun `the planner proposes less time after pomodoros`() = kotlinx.coroutines.test.runTest {
        evaluations.seed(exam("e", inHours = 50).copy(studyHours = 2.0))
        val before = suggest()().plan.blocks.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
        sessions.seed(PomodoroSession(id = "p", evaluationId = "e", startedAt = TEST_NOW, focusMinutes = 30, completedCycles = 2))
        val after = suggest()().plan.blocks.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }

        assertTrue(after < before)
    }

    @Test
    fun `scheduled blocks reduce the pending minutes`() {
        val target = exam("e", inHours = 50).copy(studyHours = 3.0)
        val done = StudyBlock("b", "c1", "e", TEST_NOW, TEST_NOW + Duration.ofMinutes(50), StudyBlockStatus.SCHEDULED)
        val suggested = done.copy(id = "s", status = StudyBlockStatus.SUGGESTED)

        assertEquals(130, pendingStudyMinutes(target, listOf(done, suggested)))
    }

    @Test
    fun `suggestion avoids weekly classes, evaluations and existing blocks`() = runTest {
        // Exam on Wednesday 10:00 (inHours = 50 from Monday 08:00), 2 h of study.
        evaluations.seed(exam("e", inHours = 50).copy(studyHours = 2.0), exam("other", inHours = 4, durationHours = 2).copy(studyHours = 0.0))
        blocks.seed(StudyBlock("old", "c1", "x", at(0, 15), at(0, 15, 50), StudyBlockStatus.SCHEDULED))

        val proposal = suggest()()

        val planned = proposal.plan.blocks
        assertEquals(3, planned.size)
        assertEquals(at(0, 10), planned.first().startAt) // after the 08:00–10:00 class
        assertTrue(planned.none { it.startAt < at(0, 14) && it.endAt > at(0, 12) }) // the other exam 12:00–14:00
        assertTrue(planned.none { it.startAt == at(0, 15) })
        assertTrue(planned.all { it.status == StudyBlockStatus.SUGGESTED && it.evaluationId == "e" })
    }

    @Test
    fun `accepting saves the blocks as scheduled`() = runTest {
        val proposal = listOf(StudyBlock("n", "c1", "e", at(0, 10), at(0, 10, 50)))

        assertEquals(1, AcceptStudyBlocksUseCase(blocks)(proposal).getOrThrow())
        assertEquals(StudyBlockStatus.SCHEDULED, blocks.items.value.getValue("n").status)
    }

    @Test
    fun `one stress check-in per day, replaced when saved again`() = runTest {
        val stress = FakeStressRepository()
        val save = SaveStressCheckInUseCase(stress, testClock)

        save(2)
        save(5)

        assertEquals(1, stress.items.value.size)
        assertEquals(5, stress.items.value.getValue(LocalDate.of(2026, 10, 12).toString()).level)
        assertTrue(save(9).isFailure)
    }

    private fun at(day: Long, hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 10, 12).plusDays(day).atTime(hour, minute).atZone(TEST_ZONE).toInstant()

    @Test
    fun `removing all suggested blocks keeps the ones already done`() = runTest {
        blocks.seed(
            StudyBlock("s1", "c1", "e", at(0, 10), at(0, 10, 50), StudyBlockStatus.SCHEDULED),
            StudyBlock("s2", "c1", "e", at(1, 10), at(1, 10, 50), StudyBlockStatus.SCHEDULED),
            StudyBlock("d1", "c1", "e", at(0, 8), at(0, 8, 50), StudyBlockStatus.DONE),
        )
        val remove = RemoveSuggestedStudyBlocksUseCase(blocks)
        assertEquals(2, remove.pendingCount().first())

        assertEquals(2, remove().getOrThrow())
        assertEquals(setOf("d1"), blocks.items.value.keys)
        assertEquals(0, remove.pendingCount().first())
    }

    @Test
    fun `a study block reads as study, not as an evaluation`() {
        val block = StudyBlock("b", "c1", "e", at(0, 10), at(0, 10, 50), StudyBlockStatus.SCHEDULED)
        val entry = StudyBlockEntry(block, exam("e", inHours = 50).copy(title = "Parcial de Física"), null)

        assertEquals("Estudiar: Parcial de Física", entry.title)
        assertEquals(
            "Bloque de estudio sugerido para Parcial de Física. Puedes marcarlo como hecho o quitarlo.",
            entry.explanation,
        )
        assertEquals("Bloque de estudio", StudyBlockEntry(block, null, null).title)
    }
}
