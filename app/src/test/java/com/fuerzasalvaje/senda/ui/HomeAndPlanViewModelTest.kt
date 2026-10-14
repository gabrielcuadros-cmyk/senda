package com.fuerzasalvaje.senda.ui

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.fuerzasalvaje.senda.domain.engine.PriorityEngine
import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import com.fuerzasalvaje.senda.domain.usecase.auth.ObserveSessionUseCase
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObserveWeekUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ChangeEvaluationStatusUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ObserveEvaluationsUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveTodayExamRoutinesUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.AcceptStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObservePendingStudyUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveStudyProgressUseCase
import com.fuerzasalvaje.senda.testing.FakePomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveAvailabilityUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveCourseLoadUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.SaveAvailabilityUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.SuggestStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.GenerateStudyTipsUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.ObserveLatestTipUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.ObserveTodayStressUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.SaveStressCheckInUseCase
import com.fuerzasalvaje.senda.testing.FakeAuthRepository
import com.fuerzasalvaje.senda.testing.FakeAvailabilityRepository
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeGeminiService
import com.fuerzasalvaje.senda.testing.FakeRecommendationRepository
import com.fuerzasalvaje.senda.testing.FakeStressRepository
import com.fuerzasalvaje.senda.testing.FakeStudyBlockRepository
import com.fuerzasalvaje.senda.testing.MainDispatcherRule
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.testing.testClock
import com.fuerzasalvaje.senda.ui.home.HomeUiState
import com.fuerzasalvaje.senda.ui.home.HomeViewModel
import com.fuerzasalvaje.senda.ui.plan.StudyPlanUiState
import com.fuerzasalvaje.senda.ui.plan.StudyPlanViewModel
import com.fuerzasalvaje.senda.domain.usecase.plan.RemoveSuggestedStudyBlocksUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.LocalTime

class HomeAndPlanViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val courses = FakeCourseRepository().apply { seed(course("c1", "Física"), course("c2", "Química")) }
    private val evaluations = FakeEvaluationRepository().apply {
        seed(
            exam("fisica", inHours = 50, courseId = "c1", difficulty = 5, weight = 30.0), // 10 h of study
            exam("quimica", inHours = 30, courseId = "c2", difficulty = 1, weight = 10.0), // 2 h of study
        )
    }
    private val blocks = FakeStudyBlockRepository()
    private val stress = FakeStressRepository()
    private val recommendations = FakeRecommendationRepository()
    private val gemini = FakeGeminiService()
    private val availability = FakeAvailabilityRepository()
    private val priorities = ObservePrioritiesUseCase(evaluations, courses, PriorityEngine(testClock))
    private val sessions = FakePomodoroSessionRepository()
    private val progress = ObserveStudyProgressUseCase(evaluations, blocks, sessions)

    private fun homeViewModel() = HomeViewModel(
        observeSession = ObserveSessionUseCase(FakeAuthRepository()),
        observeWeek = ObserveWeekUseCase(evaluations, courses, blocks, testClock),
        observePriorities = priorities,
        observeEvaluations = ObserveEvaluationsUseCase(evaluations),
        observeTodayStress = ObserveTodayStressUseCase(stress, testClock),
        observeCourseLoad = ObserveCourseLoadUseCase(evaluations, courses, testClock),
        observeLatestTip = ObserveLatestTipUseCase(recommendations),
        observeStudyProgress = progress,
        observeRoutines = ObserveTodayExamRoutinesUseCase(evaluations, courses, testClock),
        saveStress = SaveStressCheckInUseCase(stress, testClock),
        generateTips = GenerateStudyTipsUseCase(gemini, priorities, courses, blocks, sessions, recommendations, testClock),
        changeStatus = ChangeEvaluationStatusUseCase(evaluations),
        clock = testClock,
        editPdfMarkdown = com.fuerzasalvaje.senda.domain.usecase.pdf.EditPdfMarkdownUseCase(
            com.fuerzasalvaje.senda.domain.usecase.pdf.ExtractPdfMarkdownUseCase(
                com.fuerzasalvaje.senda.testing.FakePdfTextExtractor(), com.fuerzasalvaje.senda.testing.FakeAiLogger(), testClock,
            ),
            com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase(evaluations),
        ),
    )

    private fun planViewModel() = StudyPlanViewModel(
        observeCourseLoad = ObserveCourseLoadUseCase(evaluations, courses, testClock),
        observeAvailability = ObserveAvailabilityUseCase(availability),
        observePendingStudy = ObservePendingStudyUseCase(priorities, courses, progress),
        saveAvailability = SaveAvailabilityUseCase(availability),
        suggestBlocks = SuggestStudyBlocksUseCase(evaluations, courses, blocks, availability, sessions, PriorityEngine(testClock), testClock),
        acceptBlocks = AcceptStudyBlocksUseCase(blocks),
        removeSuggested = RemoveSuggestedStudyBlocksUseCase(blocks),
        clock = testClock,
    )

    private suspend fun ReceiveTurbine<HomeUiState>.home(predicate: (HomeUiState) -> Boolean): HomeUiState {
        while (true) awaitItem().let { if (!it.loading && predicate(it)) return it }
    }

    private suspend fun ReceiveTurbine<StudyPlanUiState>.plan(predicate: (StudyPlanUiState) -> Boolean): StudyPlanUiState {
        while (true) awaitItem().let { if (!it.loading && predicate(it)) return it }
    }

    @Test
    fun `home shows ranked priorities, the critical course and the first name`() = runTest {
        val vm = homeViewModel()
        vm.state.test {
            val state = home { it.priorities.size == 2 }
            assertEquals("Ana", state.firstName)
            assertEquals("fisica", state.priorities.first().evaluation.id) // 5 x 30 / 50 h beats 1 x 10 / 30 h
            assertEquals("Física", state.criticalCourse?.course?.name) // 10 of 12 hours
            assertFalse(state.canGenerateTip)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stress check-in enables the tutor and tips are saved`() = runTest {
        val vm = homeViewModel()
        vm.state.test {
            home { true }
            vm.checkIn(4)
            assertTrue(home { it.stressLevel == 4 }.canGenerateTip)

            vm.generateTip()
            val withTip = home { it.tip != null && !it.tipState.loading }
            assertEquals("- Estudia **Física** primero", withTip.tip?.content)
            assertEquals(4, withTip.tip?.stressLevel)
            cancelAndIgnoreRemainingEvents()
        }
        val request = gemini.lastTipsRequest!!
        assertEquals(4, request.stressLevel)
        assertEquals(listOf("fisica", "quimica"), request.pendingItems.map { it.title })
        assertEquals(10.0, request.pendingItems.first().pendingStudyHours, 0.0)
        assertEquals(1, recommendations.items.value.size)
    }

    @Test
    fun `tips receive this week's pomodoro minutes and pending hours discount them`() = runTest {
        sessions.seed(
            PomodoroSession(id = "now", evaluationId = "fisica", startedAt = testClock.instant(), focusMinutes = 25, completedCycles = 2),
            // Sunday, the week before: not part of this week's minutes.
            PomodoroSession(id = "old", startedAt = testClock.instant().minus(Duration.ofDays(1)), focusMinutes = 25, completedCycles = 1),
        )
        val vm = homeViewModel()
        vm.state.test {
            home { true }
            vm.checkIn(3)
            home { it.stressLevel == 3 }
            vm.generateTip()
            home { it.tip != null && !it.tipState.loading }
            cancelAndIgnoreRemainingEvents()
        }
        val request = gemini.lastTipsRequest!!
        assertEquals(50, request.weeklyPomodoroMinutes)
        assertEquals((600 - 50) / 60.0, request.pendingItems.first { it.title == "fisica" }.pendingStudyHours, 0.001)
    }

    @Test
    fun `home shows pending study hours after pomodoros`() = runTest {
        sessions.seed(PomodoroSession(id = "p", evaluationId = "quimica", startedAt = testClock.instant(), focusMinutes = 30, completedCycles = 1))
        homeViewModel().state.test {
            val state = home { it.studyProgress.isNotEmpty() }
            assertEquals(90, state.studyProgress.getValue("quimica").pendingMinutes)
            assertEquals(600, state.studyProgress.getValue("fisica").pendingMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed tip shows a retry and the retry succeeds`() = runTest {
        gemini.tipsResult = Result.failure(IOException("offline"))
        val vm = homeViewModel()
        vm.state.test {
            home { true }
            vm.checkIn(3)
            home { it.stressLevel == 3 }
            vm.generateTip()
            assertEquals(HomeViewModel.TIP_ERROR, home { it.tipState.error != null }.tipState.error)

            gemini.tipsResult = Result.success("Toma una pausa activa.")
            vm.generateTip()
            val recovered = home { it.tip != null && !it.tipState.loading }
            assertNull(recovered.tipState.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `plan suggests blocks and accepting puts them on the calendar`() = runTest {
        val vm = planViewModel()
        vm.state.test {
            val initial = plan { it.report.loads.isNotEmpty() }
            assertEquals(listOf("c1"), initial.report.critical.map { it.courseId })
            assertNull(initial.proposal.blocks)

            vm.suggest()
            val proposed = plan { it.proposal.blocks?.isNotEmpty() == true }.proposal.blocks!!
            assertTrue(blocks.items.value.isEmpty()) // nothing saved before accepting

            vm.accept()
            val accepted = plan { it.proposal.message != null }
            assertNull(accepted.proposal.blocks)
            assertEquals(proposed.size, blocks.items.value.size)
            assertTrue(blocks.items.value.values.all { it.status == StudyBlockStatus.SCHEDULED })
            assertTrue(proposed.all { it.title.startsWith("Estudiar: ") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing all suggested blocks empties the calendar`() = runTest {
        val vm = planViewModel()
        vm.state.test {
            plan { true }
            vm.suggest()
            plan { it.proposal.blocks?.isNotEmpty() == true }
            vm.accept()
            val accepted = plan { it.scheduledBlocks > 0 }.scheduledBlocks
            vm.consumeMessage()

            vm.removeAllSuggested()
            val removed = plan { it.scheduledBlocks == 0 && it.proposal.message != null }
            assertEquals("Quitamos $accepted bloques de estudio de tu calendario.", removed.proposal.message)
            assertTrue(blocks.items.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discarding clears the proposal and invalid availability is rejected`() = runTest {
        val vm = planViewModel()
        vm.state.test {
            plan { true }
            vm.suggest()
            plan { it.proposal.blocks != null }
            vm.discard()
            assertNull(plan { it.proposal.message == "Propuesta descartada." }.proposal.blocks)

            vm.onEnd(LocalTime.of(23, 30))
            assertEquals(SaveAvailabilityUseCase.INVALID_MESSAGE, plan { it.proposal.availabilityError != null }.proposal.availabilityError)
            assertEquals(Availability(), availability.current.value)

            vm.onStart(LocalTime.of(9, 0))
            assertEquals(LocalTime.of(9, 0), plan { it.availability.start == LocalTime.of(9, 0) }.availability.start)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
