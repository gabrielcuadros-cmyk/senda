package com.fuerzasalvaje.senda.ui

import androidx.lifecycle.SavedStateHandle
import com.fuerzasalvaje.senda.domain.engine.SimulacrumTimer
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.AnswerQuestionUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.FinishSimulacrumUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ObserveSimulacrumUseCase
import com.fuerzasalvaje.senda.testing.FakeQuestionRepository
import com.fuerzasalvaje.senda.testing.FakeSimulacrumRepository
import com.fuerzasalvaje.senda.testing.MainDispatcherRule
import com.fuerzasalvaje.senda.testing.SchedulerClock
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.question
import com.fuerzasalvaje.senda.ui.simulacra.SimulacrumViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SimulacrumViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val questions = FakeQuestionRepository().apply { seed(question("q1", correct = 1), question("q2", correct = 3), question("q3")) }
    private val simulacra = FakeSimulacrumRepository()
    private val clock = SchedulerClock(mainRule.dispatcher.scheduler)

    private fun attempt(minutes: Int, startedAt: java.time.Instant = TEST_NOW) =
        Simulacrum(id = "s1", courseId = "c1", questionIds = listOf("q1", "q2", "q3"), timeLimitMinutes = minutes, startedAt = startedAt)

    private fun viewModel() = SimulacrumViewModel(
        savedStateHandle = SavedStateHandle(mapOf("simulacrumId" to "s1")),
        observeSimulacrum = ObserveSimulacrumUseCase(simulacra, questions),
        answerQuestion = AnswerQuestionUseCase(simulacra),
        finishSimulacrum = FinishSimulacrumUseCase(simulacra, clock),
        clock = clock,
    )

    private val saved get() = simulacra.items.value.getValue("s1")

    @Test
    fun `the attempt is submitted automatically when the countdown reaches zero`() = runTest(mainRule.dispatcher) {
        simulacra.seed(attempt(minutes = 1))
        val vm = viewModel()
        vm.select(1) // q1 correct

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(30, vm.state.value.remainingSeconds)
        assertFalse(vm.state.value.finished)

        advanceTimeBy(31_000)
        runCurrent()
        assertTrue(vm.state.value.finished)
        assertEquals(SimulacrumTimer.deadline(saved), saved.finishedAt)
        assertEquals(1, saved.correctCount)
        assertEquals(2, saved.unansweredCount)
    }

    @Test
    fun `remaining time comes from timestamps, so a recreated screen keeps counting`() = runTest(mainRule.dispatcher) {
        simulacra.seed(attempt(minutes = 20, startedAt = TEST_NOW - Duration.ofMinutes(5)))

        assertEquals(15 * 60L, viewModel().state.value.remainingSeconds)
    }

    @Test
    fun `an attempt that expired while the app was closed is submitted on open`() = runTest(mainRule.dispatcher) {
        simulacra.seed(attempt(minutes = 10, startedAt = TEST_NOW - Duration.ofMinutes(30)))

        val vm = viewModel()
        runCurrent()

        assertTrue(vm.state.value.finished)
        assertNotNull(saved.finishedAt)
    }

    @Test
    fun `every answer change is saved immediately`() = runTest(mainRule.dispatcher) {
        simulacra.seed(attempt(minutes = 20))
        val vm = viewModel()

        vm.select(0)
        vm.next()
        vm.select(3)
        vm.previous()
        vm.select(1) // change the first answer
        runCurrent()

        assertEquals(mapOf("q1" to 1, "q2" to 3), saved.answers.associate { it.questionId to it.selectedOptionIndex })
        assertEquals(2, saved.correctCount)
        assertEquals(2, vm.state.value.answeredCount)
    }

    @Test
    fun `finishing early needs confirmation`() = runTest(mainRule.dispatcher) {
        simulacra.seed(attempt(minutes = 20))
        val vm = viewModel()

        vm.requestFinish()
        assertTrue(vm.state.value.confirmFinish)
        vm.dismissFinish()
        assertFalse(vm.state.value.finished)

        vm.requestFinish()
        vm.confirmFinish()
        runCurrent()
        assertTrue(vm.state.value.finished)
        assertEquals(TEST_NOW, saved.finishedAt)
        assertEquals(3, saved.unansweredCount)
    }
}
