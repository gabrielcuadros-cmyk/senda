package com.fuerzasalvaje.senda.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.PriorityItem
import com.fuerzasalvaje.senda.domain.model.Conflict
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.WeeklyProgress
import com.fuerzasalvaje.senda.domain.usecase.auth.ObserveSessionUseCase
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObserveWeekUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ChangeEvaluationStatusUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ObserveEvaluationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val firstName: String = "",
    val today: LocalDate? = null,
    val greeting: String = "Hola",
    val progress: WeeklyProgress = WeeklyProgress(0, 0),
    val conflicts: List<Conflict> = emptyList(),
    val priorities: List<PriorityItem> = emptyList(),
    val courses: List<Course> = emptyList(),
    val hasEvaluations: Boolean = false,
    val selected: Evaluation? = null,
) {
    fun courseOf(evaluation: Evaluation): Course? = courses.firstOrNull { it.id == evaluation.courseId }
}

/** Basic dashboard: greeting, weekly progress, this week's conflicts and the urgency list. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observeWeek: ObserveWeekUseCase,
    observePriorities: ObservePrioritiesUseCase,
    observeEvaluations: ObserveEvaluationsUseCase,
    private val changeStatus: ChangeEvaluationStatusUseCase,
    private val clock: Clock,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone
    private val selectedId = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(
        observeSession(),
        observeWeek(observeWeek.currentWeekStart()),
        observePriorities(),
        observeEvaluations(),
        selectedId,
    ) { user, week, priorities, evaluations, selected ->
        HomeUiState(
            loading = false,
            firstName = user?.name?.substringBefore(' ').orEmpty(),
            today = LocalDate.now(clock),
            greeting = greetingFor(LocalTime.now(clock)),
            progress = week.progress,
            conflicts = week.conflicts,
            priorities = priorities,
            courses = week.courses,
            hasEvaluations = evaluations.isNotEmpty(),
            selected = evaluations.firstOrNull { it.id == selected },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun open(evaluation: Evaluation) { selectedId.value = evaluation.id }
    fun close() { selectedId.value = null }

    fun setStatus(evaluation: Evaluation, status: EvaluationStatus) {
        viewModelScope.launch { changeStatus(evaluation, status) }
    }

    fun setPhaseStatus(project: Project, phaseId: String, status: PhaseStatus) {
        viewModelScope.launch { changeStatus.changePhase(project, phaseId, status) }
    }

    private fun greetingFor(time: LocalTime) = when (time.hour) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}
