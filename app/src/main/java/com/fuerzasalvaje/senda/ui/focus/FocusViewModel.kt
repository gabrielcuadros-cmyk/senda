package com.fuerzasalvaje.senda.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.ExamRoutine
import com.fuerzasalvaje.senda.domain.engine.PomodoroEngine
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroRun
import com.fuerzasalvaje.senda.domain.repository.PomodoroTimer
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCoursesUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObservePomodoroConfigUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveTodayExamRoutinesUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveTodayPomodorosUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.SavePomodoroConfigUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveStudyProgressUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.StudyProgress
import com.fuerzasalvaje.senda.ui.navigation.DeepLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** A pending evaluation the Pomodoro can be linked to. */
data class FocusTarget(val evaluationId: String, val courseId: String, val title: String, val courseName: String) {
    val label: String get() = if (courseName.isBlank()) title else "$title · $courseName"
}

data class FocusUiState(
    val config: PomodoroConfig = PomodoroConfig(),
    val run: PomodoroRun = PomodoroEngine.idle(PomodoroConfig()),
    val now: Instant = Instant.EPOCH,
    val todayPomodoros: Int = 0,
    val targets: List<FocusTarget> = emptyList(),
    val progress: StudyProgress? = null,
    val routines: List<ExamRoutine> = emptyList(),
) {
    val remaining: Duration get() = run.remainingAt(now)
    val fraction: Float get() = run.fractionAt(now)
    val untilLongBreak: Int get() = PomodoroEngine.untilLongBreak(run.step, config)
    val target: FocusTarget? get() = targets.firstOrNull { it.evaluationId == run.evaluationId }
}

private data class FocusData(
    val config: PomodoroConfig,
    val run: PomodoroRun,
    val today: Int,
    val targets: List<FocusTarget>,
    val progress: Map<String, StudyProgress>,
)

/** KB-05, D-13, D-15: the shared Pomodoro timer, linked to an evaluation, plus today's exam routines. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusViewModel @Inject constructor(
    observeConfig: ObservePomodoroConfigUseCase,
    observeTodayPomodoros: ObserveTodayPomodorosUseCase,
    observePriorities: ObservePrioritiesUseCase,
    observeCourses: ObserveCoursesUseCase,
    observeProgress: ObserveStudyProgressUseCase,
    observeRoutines: ObserveTodayExamRoutinesUseCase,
    private val saveConfig: SavePomodoroConfigUseCase,
    private val timer: PomodoroTimer,
    private val clock: Clock,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone

    private val targets: Flow<List<FocusTarget>> =
        combine(observePriorities(), observeCourses()) { priorities, courses ->
            val names = courses.associate { it.id to it.name }
            priorities.map { it.evaluation }.distinctBy { it.id }.map {
                FocusTarget(it.id, it.courseId, it.title, names[it.courseId].orEmpty())
            }
        }

    /** Current instant, refreshed while the timer runs so the countdown moves. */
    private val now: Flow<Instant> = timer.run.flatMapLatest { run ->
        if (!run.running) flowOf(clock.instant()) else flow {
            while (true) {
                emit(clock.instant())
                delay(TICK_MILLIS)
            }
        }
    }

    private val data = combine(observeConfig(), timer.run, observeTodayPomodoros(), targets, observeProgress()) { cfg, run, today, list, progress ->
        FocusData(cfg, run, today, list, progress)
    }

    val state: StateFlow<FocusUiState> = combine(data, now, observeRoutines()) { d, instant, routines ->
        FocusUiState(
            config = d.config,
            run = d.run,
            now = instant,
            todayPomodoros = d.today,
            targets = d.targets,
            progress = d.run.evaluationId?.let(d.progress::get),
            routines = routines,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState(run = timer.run.value, now = clock.instant()))

    init {
        // A study block alert opens Focus already linked to its evaluation.
        viewModelScope.launch {
            DeepLinks.pendingFocusEvaluation.filterNotNull().collect { id ->
                DeepLinks.consumeFocusEvaluation()
                targets.first().firstOrNull { it.evaluationId == id }?.let(::select)
            }
        }
    }

    fun start() = timer.start()
    fun pause() = timer.pause()
    fun stop() = timer.stop()
    fun skip() = timer.skip()

    /** Null unlinks the Pomodoro: its minutes are still counted for the day and the week. */
    fun select(target: FocusTarget?) = timer.link(target?.evaluationId, target?.courseId, target?.title)

    fun saveConfig(newConfig: PomodoroConfig) {
        viewModelScope.launch { saveConfig.invoke(newConfig.sanitized()) }
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
