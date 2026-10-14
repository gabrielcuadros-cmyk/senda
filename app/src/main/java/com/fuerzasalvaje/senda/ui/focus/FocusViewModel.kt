package com.fuerzasalvaje.senda.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.ExamRoutine
import com.fuerzasalvaje.senda.domain.engine.PomodoroEngine
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.PomodoroStep
import com.fuerzasalvaje.senda.domain.repository.PomodoroAlarm
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCoursesUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObservePomodoroConfigUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveTodayExamRoutinesUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveTodayPomodorosUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.SavePomodoroConfigUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.SavePomodoroSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Timer state of the running Pomodoro; [endsAt] is set only while it runs. */
data class TimerState(
    val step: PomodoroStep,
    val remaining: Duration,
    val endsAt: Instant? = null,
    val courseId: String? = null,
) {
    val running: Boolean get() = endsAt != null
    val total: Duration get() = Duration.ofMinutes(step.minutes.toLong())
    val started: Boolean get() = running || remaining < total
    val fraction: Float get() = if (total.isZero) 0f else 1f - remaining.toMillis().toFloat() / total.toMillis()
}

data class FocusUiState(
    val config: PomodoroConfig = PomodoroConfig(),
    val timer: TimerState = TimerState(PomodoroEngine.first(PomodoroConfig()), Duration.ofMinutes(25)),
    val todayPomodoros: Int = 0,
    val courses: List<Course> = emptyList(),
    val routines: List<ExamRoutine> = emptyList(),
) {
    val untilLongBreak: Int get() = PomodoroEngine.untilLongBreak(timer.step, config)
}

/** KB-05, D-13, D-15: Pomodoro timer with active pauses, plus today's exam routines. */
@HiltViewModel
class FocusViewModel @Inject constructor(
    observeConfig: ObservePomodoroConfigUseCase,
    observeTodayPomodoros: ObserveTodayPomodorosUseCase,
    observeCourses: ObserveCoursesUseCase,
    observeRoutines: ObserveTodayExamRoutinesUseCase,
    private val saveConfig: SavePomodoroConfigUseCase,
    private val saveSession: SavePomodoroSessionUseCase,
    private val alarm: PomodoroAlarm,
    private val clock: Clock,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone
    private val config = MutableStateFlow(PomodoroConfig())
    private val timer = MutableStateFlow(TimerState(PomodoroEngine.first(PomodoroConfig()), Duration.ofMinutes(25)))
    private var session: PomodoroSession? = null
    private var ticker: Job? = null

    val state: StateFlow<FocusUiState> = combine(
        config, timer, observeTodayPomodoros(), observeCourses(), observeRoutines(),
    ) { cfg, t, today, courses, routines ->
        FocusUiState(cfg, t, today, courses, routines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        viewModelScope.launch {
            observeConfig().collect { saved ->
                config.value = saved
                // A step that has not started yet takes the new durations right away.
                timer.update { t -> if (t.started) t else t.withStep(t.step.copy(minutes = minutesFor(t.step.phase, saved))) }
            }
        }
    }

    fun start() {
        val t = timer.value
        if (t.running) return
        val endsAt = clock.instant() + t.remaining
        timer.value = t.copy(endsAt = endsAt)
        if (t.step.phase == PomodoroPhase.FOCUS && session == null) openSession(t.courseId)
        scheduleAlarm(t.step, endsAt)
        startTicker()
    }

    fun pause() {
        val t = timer.value
        val endsAt = t.endsAt ?: return
        ticker?.cancel()
        alarm.cancel()
        timer.value = t.copy(endsAt = null, remaining = remainingUntil(endsAt))
    }

    /** Back to the first focus block; closes the current session. */
    fun reset() {
        ticker?.cancel()
        alarm.cancel()
        closeSession()
        timer.value = TimerState(PomodoroEngine.first(config.value), minutes(config.value.focusMinutes), courseId = timer.value.courseId)
    }

    /** Ends the current step now. A skipped focus block is not saved as completed. */
    fun skip() {
        ticker?.cancel()
        alarm.cancel()
        val t = timer.value
        val next = PomodoroEngine.next(t.step, config.value)
        timer.value = TimerState(next, minutes(next.minutes), courseId = t.courseId)
    }

    fun selectCourse(courseId: String?) {
        if (timer.value.running) return
        timer.update { it.copy(courseId = courseId) }
        // A new subject starts a new session.
        closeSession()
    }

    fun saveConfig(newConfig: PomodoroConfig) {
        viewModelScope.launch { saveConfig.invoke(newConfig.sanitized()) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val t = timer.value
                val endsAt = t.endsAt ?: break
                val remaining = remainingUntil(endsAt)
                if (remaining.isZero) {
                    finishStep(t)
                    break
                }
                timer.value = t.copy(remaining = remaining)
                delay(TICK_MILLIS)
            }
        }
    }

    /** The finished step gives way to the next one, which waits for the student to start it. */
    private fun finishStep(t: TimerState) {
        val next = PomodoroEngine.next(t.step, config.value)
        if (t.step.phase == PomodoroPhase.FOCUS) recordCompletedFocus(next.completedFocus)
        timer.value = TimerState(next, minutes(next.minutes), courseId = t.courseId)
    }

    private fun openSession(courseId: String?) {
        val cfg = config.value
        session = PomodoroSession(
            id = UUID.randomUUID().toString(),
            courseId = courseId,
            startedAt = clock.instant(),
            focusMinutes = cfg.focusMinutes,
            shortBreakMinutes = cfg.shortBreakMinutes,
            longBreakMinutes = cfg.longBreakMinutes,
        )
    }

    private fun recordCompletedFocus(completed: Int) {
        val updated = (session ?: return).copy(completedCycles = completed, endedAt = clock.instant())
        session = updated
        viewModelScope.launch { saveSession(updated) }
    }

    private fun closeSession() {
        session = null
    }

    private fun scheduleAlarm(step: PomodoroStep, endsAt: Instant) {
        val next = PomodoroEngine.next(step, config.value)
        val (title, message) = if (step.phase == PomodoroPhase.FOCUS) {
            val pause = if (next.suggestActivePause) " Toca pausa activa: levántate y estírate." else ""
            "¡Pomodoro completado!" to "Toma ${next.minutes} min de pausa.$pause"
        } else {
            "Fin de la pausa" to "Vuelve a enfocarte: ${next.minutes} min."
        }
        alarm.schedule(endsAt, title, message)
    }

    private fun remainingUntil(endsAt: Instant): Duration =
        Duration.between(clock.instant(), endsAt).coerceAtLeast(Duration.ZERO)

    private fun TimerState.withStep(step: PomodoroStep) = copy(step = step, remaining = minutes(step.minutes))

    private fun minutesFor(phase: PomodoroPhase, cfg: PomodoroConfig) = when (phase) {
        PomodoroPhase.FOCUS -> cfg.focusMinutes
        PomodoroPhase.SHORT_BREAK -> cfg.shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> cfg.longBreakMinutes
    }

    private fun minutes(value: Int): Duration = Duration.ofMinutes(value.toLong())

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
