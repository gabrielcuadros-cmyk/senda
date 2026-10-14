package com.fuerzasalvaje.senda.data.focus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroRun
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.PomodoroStep
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the timer on the device (not in the cloud): only timestamps and counters, so the state can be
 * rebuilt after the system closes the app.
 */
@Singleton
class PomodoroRunStore @Inject constructor(@param:ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): PomodoroRun? {
        val phase = prefs.getString(PHASE, null)?.let { runCatching { PomodoroPhase.valueOf(it) }.getOrNull() } ?: return null
        val sessionId = prefs.getString(SESSION_ID, null)
        return runCatching {
            PomodoroRun(
                step = PomodoroStep(phase, prefs.getInt(MINUTES, 25), prefs.getInt(COMPLETED, 0), prefs.getBoolean(ACTIVE_PAUSE, false)),
                remaining = Duration.ofMillis(prefs.getLong(REMAINING, 0)),
                endsAt = prefs.getLong(ENDS_AT, NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli),
                evaluationId = prefs.getString(EVALUATION_ID, null),
                courseId = prefs.getString(COURSE_ID, null),
                targetTitle = prefs.getString(TITLE, null),
                session = sessionId?.let {
                    PomodoroSession(
                        id = it,
                        courseId = prefs.getString(COURSE_ID, null),
                        evaluationId = prefs.getString(EVALUATION_ID, null),
                        startedAt = Instant.ofEpochMilli(prefs.getLong(SESSION_STARTED, 0)),
                        endedAt = prefs.getLong(SESSION_ENDED, NONE).takeIf { v -> v != NONE }?.let(Instant::ofEpochMilli),
                        focusMinutes = prefs.getInt(SESSION_FOCUS, 25),
                        shortBreakMinutes = prefs.getInt(SESSION_SHORT, 5),
                        longBreakMinutes = prefs.getInt(SESSION_LONG, 15),
                        completedCycles = prefs.getInt(SESSION_CYCLES, 0),
                    )
                },
            )
        }.getOrNull()
    }

    fun save(run: PomodoroRun) = prefs.edit {
        putString(PHASE, run.step.phase.name)
        putInt(MINUTES, run.step.minutes)
        putInt(COMPLETED, run.step.completedFocus)
        putBoolean(ACTIVE_PAUSE, run.step.suggestActivePause)
        putLong(REMAINING, run.remaining.toMillis())
        putLong(ENDS_AT, run.endsAt?.toEpochMilli() ?: NONE)
        putString(EVALUATION_ID, run.evaluationId)
        putString(COURSE_ID, run.courseId)
        putString(TITLE, run.targetTitle)
        val session = run.session
        putString(SESSION_ID, session?.id)
        putLong(SESSION_STARTED, session?.startedAt?.toEpochMilli() ?: 0)
        putLong(SESSION_ENDED, session?.endedAt?.toEpochMilli() ?: NONE)
        putInt(SESSION_FOCUS, session?.focusMinutes ?: 0)
        putInt(SESSION_SHORT, session?.shortBreakMinutes ?: 0)
        putInt(SESSION_LONG, session?.longBreakMinutes ?: 0)
        putInt(SESSION_CYCLES, session?.completedCycles ?: 0)
    }

    fun loadConfig(): PomodoroConfig = PomodoroConfig(
        focusMinutes = prefs.getInt(CFG_FOCUS, PomodoroConfig().focusMinutes),
        shortBreakMinutes = prefs.getInt(CFG_SHORT, PomodoroConfig().shortBreakMinutes),
        longBreakMinutes = prefs.getInt(CFG_LONG, PomodoroConfig().longBreakMinutes),
        cyclesBeforeLongBreak = prefs.getInt(CFG_CYCLES, PomodoroConfig().cyclesBeforeLongBreak),
    ).sanitized()

    fun saveConfig(config: PomodoroConfig) = prefs.edit {
        putInt(CFG_FOCUS, config.focusMinutes)
        putInt(CFG_SHORT, config.shortBreakMinutes)
        putInt(CFG_LONG, config.longBreakMinutes)
        putInt(CFG_CYCLES, config.cyclesBeforeLongBreak)
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val FILE = "pomodoro_run"
        const val NONE = -1L
        const val PHASE = "phase"
        const val MINUTES = "minutes"
        const val COMPLETED = "completedFocus"
        const val ACTIVE_PAUSE = "activePause"
        const val REMAINING = "remainingMs"
        const val ENDS_AT = "endsAt"
        const val EVALUATION_ID = "evaluationId"
        const val COURSE_ID = "courseId"
        const val TITLE = "targetTitle"
        const val SESSION_ID = "sessionId"
        const val SESSION_STARTED = "sessionStarted"
        const val SESSION_ENDED = "sessionEnded"
        const val SESSION_FOCUS = "sessionFocus"
        const val SESSION_SHORT = "sessionShort"
        const val SESSION_LONG = "sessionLong"
        const val SESSION_CYCLES = "sessionCycles"
        const val CFG_FOCUS = "cfgFocus"
        const val CFG_SHORT = "cfgShort"
        const val CFG_LONG = "cfgLong"
        const val CFG_CYCLES = "cfgCycles"
    }
}
