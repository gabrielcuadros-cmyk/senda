package com.fuerzasalvaje.senda.ui

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.fuerzasalvaje.senda.domain.model.ConflictType
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.usecase.calendar.FilterCalendarUseCase
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObserveWeekUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ChangeEvaluationStatusUseCase
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeStudyBlockRepository
import com.fuerzasalvaje.senda.domain.usecase.plan.UpdateStudyBlockUseCase
import com.fuerzasalvaje.senda.testing.MainDispatcherRule
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.testing.testClock
import com.fuerzasalvaje.senda.ui.calendar.CalendarUiState
import com.fuerzasalvaje.senda.ui.calendar.CalendarViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class CalendarViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val courses = FakeCourseRepository().apply { seed(course("c1", "Física"), course("c2", "Química")) }
    private val evaluations = FakeEvaluationRepository().apply {
        seed(
            exam("clashA", inHours = 1, durationHours = 2),
            exam("clashB", inHours = 2, durationHours = 1, courseId = "c2"),
            Lab(id = "lab", courseId = "c2", title = "Lab", difficulty = 2, createdAt = TEST_NOW, dueAt = TEST_NOW + Duration.ofHours(30)),
            exam("nextWeek", inHours = 8 * 24),
        )
    }

    private val studyBlocks = FakeStudyBlockRepository()

    private fun viewModel() = CalendarViewModel(
        observeWeek = ObserveWeekUseCase(evaluations, courses, studyBlocks, testClock),
        filterCalendar = FilterCalendarUseCase(),
        changeStatus = ChangeEvaluationStatusUseCase(evaluations),
        updateStudyBlock = UpdateStudyBlockUseCase(studyBlocks),
        clock = testClock,
        observePdfDigests = com.fuerzasalvaje.senda.domain.usecase.pdf.ObservePdfDigestsUseCase(com.fuerzasalvaje.senda.testing.FakePdfDigestRepository()),
    )

    private suspend fun ReceiveTurbine<CalendarUiState>.awaitUntil(predicate: (CalendarUiState) -> Boolean): CalendarUiState {
        while (true) {
            val item = awaitItem()
            if (!item.loading && predicate(item)) return item
        }
    }

    @Test
    fun `shows the current week with its clash`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val state = awaitUntil { it.entries.isNotEmpty() }
            assertEquals(LocalDate.of(2026, 10, 12), state.weekStart)
            assertEquals(setOf("clashA", "clashB", "lab"), state.entries.map { it.id }.toSet())
            assertTrue(state.clashOn(LocalDate.of(2026, 10, 12)))
            assertEquals(ConflictType.CLASH, state.conflicts.single().type)
            assertEquals(setOf("clashA", "clashB"), state.clashingIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters combine but conflicts keep the whole week`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.entries.isNotEmpty() }
            vm.toggleType(EvaluationType.EXAM)
            assertEquals(setOf("clashA", "clashB"), awaitUntil { it.filter.types.isNotEmpty() }.entries.map { it.id }.toSet())

            vm.selectCourse("c2")
            val filtered = awaitUntil { it.filter.courseId == "c2" }
            assertEquals(listOf("clashB"), filtered.entries.map { it.id })
            assertEquals(1, filtered.conflicts.size)

            vm.clearTypes()
            assertEquals(setOf("clashB", "lab"), awaitUntil { it.filter.types.isEmpty() }.entries.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `week navigation moves the grid`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.entries.isNotEmpty() }
            vm.nextWeek()
            val next = awaitUntil { it.weekStart == LocalDate.of(2026, 10, 19) }
            assertEquals(listOf("nextWeek"), next.entries.map { it.id })
            vm.goToToday()
            awaitUntil { it.weekStart == LocalDate.of(2026, 10, 12) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `quick status change updates progress and removes the clash`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val state = awaitUntil { it.entries.isNotEmpty() }
            assertEquals(0, state.progress.completed)
            val entry = state.entries.first { it.id == "clashA" }
            vm.open(entry)
            assertEquals("clashA", awaitUntil { it.selected != null }.selected?.id)

            vm.setStatus(entry.evaluation, EvaluationStatus.COMPLETED)
            val updated = awaitUntil { it.progress.completed == 1 }
            assertEquals(3, updated.progress.total)
            assertTrue(updated.conflicts.none { it.type == ConflictType.CLASH })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
