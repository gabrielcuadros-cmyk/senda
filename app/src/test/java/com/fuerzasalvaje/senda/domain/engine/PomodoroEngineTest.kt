package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroEngineTest {

    private val config = PomodoroConfig()

    private fun run(steps: Int): List<PomodoroStep> =
        generateSequence(PomodoroEngine.first(config)) { PomodoroEngine.next(it, config) }.take(steps).toList()

    @Test
    fun `default cycle is 25 focus, 5 break and 15 long break after the 4th pomodoro`() {
        val steps = run(9)

        assertEquals(listOf(25, 5, 25, 5, 25, 5, 25, 15, 25), steps.map { it.minutes })
        assertEquals(PomodoroPhase.LONG_BREAK, steps[7].phase)
        assertEquals(4, steps[7].completedFocus)
        assertEquals(PomodoroPhase.FOCUS, steps[8].phase)
    }

    @Test
    fun `active pause is suggested after every second pomodoro`() {
        val steps = run(16)

        assertEquals(
            listOf(false, true, false, true, false, true, false, true),
            steps.filter { it.isBreak }.map { it.suggestActivePause },
        )
        assertFalse(steps.filterNot { it.isBreak }.any { it.suggestActivePause })
    }

    @Test
    fun `custom config changes durations and long break frequency`() {
        val custom = PomodoroConfig(focusMinutes = 50, shortBreakMinutes = 10, longBreakMinutes = 30, cyclesBeforeLongBreak = 2)
        val first = PomodoroEngine.first(custom)
        val shortBreak = PomodoroEngine.next(first, custom)
        val longBreak = PomodoroEngine.next(PomodoroEngine.next(shortBreak, custom), custom)

        assertEquals(50, first.minutes)
        assertEquals(PomodoroPhase.SHORT_BREAK to 10, shortBreak.phase to shortBreak.minutes)
        assertEquals(PomodoroPhase.LONG_BREAK to 30, longBreak.phase to longBreak.minutes)
    }

    @Test
    fun `focus blocks until the long break count the current one`() {
        val focus = run(8).filterNot { it.isBreak }

        assertEquals(listOf(4, 3, 2, 1), focus.map { PomodoroEngine.untilLongBreak(it, config) })
    }

    @Test
    fun `config is validated and sanitized`() {
        assertTrue(PomodoroConfig().isValid)
        val broken = PomodoroConfig(focusMinutes = 0, shortBreakMinutes = -3, longBreakMinutes = 500, cyclesBeforeLongBreak = 1)

        assertFalse(broken.isValid)
        assertEquals(PomodoroConfig(5, 1, 60, 2), broken.sanitized())
    }
}
