package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.engine.PriorityEngine
import com.fuerzasalvaje.senda.domain.model.ClassSlot
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import com.fuerzasalvaje.senda.domain.usecase.plan.AcceptStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.SuggestStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.pendingStudyMinutes
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

    private fun suggest() = SuggestStudyBlocksUseCase(
        evaluations, courses, blocks, FakeAvailabilityRepository(), PriorityEngine(testClock), testClock,
    )

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
}
