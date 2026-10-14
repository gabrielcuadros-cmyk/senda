package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroRun
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.PomodoroStep
import com.fuerzasalvaje.senda.domain.model.SendaRules
import java.time.Duration
import java.time.Instant

/**
 * Result of bringing a run up to date. [completed] is the record to save when a focus block just ended:
 * one record per block, covering the block's own interval, so it can be compared with study blocks.
 */
data class SettledRun(val run: PomodoroRun, val completed: PomodoroSession? = null)

/**
 * Pomodoro cycle (D-13, D-15): focus, then a short break, with a long break after every
 * [PomodoroConfig.cyclesBeforeLongBreak] focus blocks. Every 2nd Pomodoro the break suggests an active pause.
 * Every transition is a pure function of the run and the current instant.
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

    /** A fresh timer that keeps the chosen evaluation. */
    fun idle(config: PomodoroConfig, previous: PomodoroRun? = null): PomodoroRun = PomodoroRun(
        step = first(config),
        remaining = minutes(config.focusMinutes),
        evaluationId = previous?.evaluationId,
        courseId = previous?.courseId,
        targetTitle = previous?.targetTitle,
    )

    /** Starts or resumes. A focus block opens a session when none is open. */
    fun start(run: PomodoroRun, now: Instant, newSessionId: () -> String, config: PomodoroConfig): PomodoroRun {
        if (run.running) return run
        val session = run.session ?: if (run.step.phase == PomodoroPhase.FOCUS) {
            PomodoroSession(
                id = newSessionId(),
                courseId = run.courseId,
                evaluationId = run.evaluationId,
                startedAt = now,
                focusMinutes = run.step.minutes,
                shortBreakMinutes = config.shortBreakMinutes,
                longBreakMinutes = config.longBreakMinutes,
            )
        } else null
        return run.copy(endsAt = now + run.remaining, session = session)
    }

    fun pause(run: PomodoroRun, now: Instant): PomodoroRun =
        if (!run.running) run else run.copy(endsAt = null, remaining = run.remainingAt(now))

    /** Ends the current step now. A skipped focus block is not credited. */
    fun skip(run: PomodoroRun, config: PomodoroConfig): PomodoroRun {
        val next = next(run.step, config)
        return run.copy(step = next, remaining = minutes(next.minutes), endsAt = null)
    }

    /**
     * When the running step has already ended (even while the app was closed), moves to the next step,
     * which waits for the student, and credits the finished focus block to the session.
     */
    fun settle(run: PomodoroRun, now: Instant, config: PomodoroConfig): SettledRun {
        val endsAt = run.endsAt ?: return SettledRun(run)
        if (endsAt.isAfter(now)) return SettledRun(run)
        val next = next(run.step, config)
        val finishedFocus = run.step.phase == PomodoroPhase.FOCUS
        val session = if (finishedFocus) {
            run.session?.copy(completedCycles = run.session.completedCycles + 1, endedAt = endsAt)
        } else {
            run.session
        }
        val settled = run.copy(step = next, remaining = minutes(next.minutes), endsAt = null, session = session)
        val record = if (finishedFocus && session != null) {
            session.copy(
                id = "${session.id}-${session.completedCycles}",
                startedAt = endsAt - minutes(run.step.minutes),
                endedAt = endsAt,
                focusMinutes = run.step.minutes,
                completedCycles = 1,
            )
        } else null
        return SettledRun(settled, completed = record)
    }

    /** Changes the evaluation (or course) the next focus minutes go to; only while the timer is stopped. */
    fun link(run: PomodoroRun, evaluationId: String?, courseId: String?, title: String?): PomodoroRun =
        if (run.running) run else run.copy(evaluationId = evaluationId, courseId = courseId, targetTitle = title, session = null)

    /**
     * New durations apply to a step that has not started; a step in progress keeps its length.
     * A session with another focus length is closed so its minutes stay exact.
     */
    fun applyConfig(run: PomodoroRun, config: PomodoroConfig): PomodoroRun {
        if (run.started) return run
        val session = run.session?.takeIf { it.focusMinutes == config.focusMinutes }
        val minutes = when (run.step.phase) {
            PomodoroPhase.FOCUS -> config.focusMinutes
            PomodoroPhase.SHORT_BREAK -> config.shortBreakMinutes
            PomodoroPhase.LONG_BREAK -> config.longBreakMinutes
        }
        return run.copy(step = run.step.copy(minutes = minutes), remaining = minutes(minutes), session = session)
    }

    private fun minutes(value: Int): Duration = Duration.ofMinutes(value.toLong())
}
