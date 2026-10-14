package com.fuerzasalvaje.senda.domain.model

/** Pomodoro durations (D-13): 25/5 with a 15 min long break every 4 cycles, configurable. */
data class PomodoroConfig(
    val focusMinutes: Int = SendaRules.POMODORO_FOCUS_MINUTES,
    val shortBreakMinutes: Int = SendaRules.POMODORO_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = SendaRules.POMODORO_LONG_BREAK_MINUTES,
    val cyclesBeforeLongBreak: Int = SendaRules.POMODORO_CYCLES_BEFORE_LONG_BREAK,
) {
    val isValid: Boolean
        get() = focusMinutes in FOCUS_RANGE &&
            shortBreakMinutes in BREAK_RANGE &&
            longBreakMinutes in BREAK_RANGE &&
            cyclesBeforeLongBreak in CYCLES_RANGE

    /** Brings every value back into its allowed range. */
    fun sanitized(): PomodoroConfig = PomodoroConfig(
        focusMinutes = focusMinutes.coerceIn(FOCUS_RANGE),
        shortBreakMinutes = shortBreakMinutes.coerceIn(BREAK_RANGE),
        longBreakMinutes = longBreakMinutes.coerceIn(BREAK_RANGE),
        cyclesBeforeLongBreak = cyclesBeforeLongBreak.coerceIn(CYCLES_RANGE),
    )

    companion object {
        val FOCUS_RANGE = 5..90
        val BREAK_RANGE = 1..60
        val CYCLES_RANGE = 2..8
    }
}

enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

/**
 * One step of the Pomodoro cycle. [completedFocus] counts the focus blocks already finished;
 * [suggestActivePause] marks the breaks that follow every 2nd Pomodoro (D-15).
 */
data class PomodoroStep(
    val phase: PomodoroPhase,
    val minutes: Int,
    val completedFocus: Int,
    val suggestActivePause: Boolean = false,
) {
    val isBreak: Boolean get() = phase != PomodoroPhase.FOCUS
}
