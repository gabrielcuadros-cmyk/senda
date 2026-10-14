package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroStep
import com.fuerzasalvaje.senda.domain.model.SendaRules

/**
 * Pomodoro cycle (D-13, D-15): focus, then a short break, with a long break after every
 * [PomodoroConfig.cyclesBeforeLongBreak] focus blocks. Every 2nd Pomodoro the break suggests an active pause.
 */
object PomodoroEngine {

    fun first(config: PomodoroConfig): PomodoroStep =
        PomodoroStep(PomodoroPhase.FOCUS, config.focusMinutes, completedFocus = 0)

    /** The step that follows [current] once it is finished or skipped. */
    fun next(current: PomodoroStep, config: PomodoroConfig): PomodoroStep {
        if (current.isBreak) return PomodoroStep(PomodoroPhase.FOCUS, config.focusMinutes, current.completedFocus)
        val completed = current.completedFocus + 1
        val long = completed % config.cyclesBeforeLongBreak == 0
        return PomodoroStep(
            phase = if (long) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK,
            minutes = if (long) config.longBreakMinutes else config.shortBreakMinutes,
            completedFocus = completed,
            suggestActivePause = completed % SendaRules.ACTIVE_PAUSE_EVERY_POMODOROS == 0,
        )
    }

    /** Focus blocks left until the next long break, counting the current one. */
    fun untilLongBreak(step: PomodoroStep, config: PomodoroConfig): Int =
        config.cyclesBeforeLongBreak - step.completedFocus % config.cyclesBeforeLongBreak
}
