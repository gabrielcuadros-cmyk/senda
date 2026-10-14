package com.fuerzasalvaje.senda.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.CourseLoad
import com.fuerzasalvaje.senda.domain.engine.ExamRoutine
import com.fuerzasalvaje.senda.domain.engine.PriorityItem
import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Conflict
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Plan
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.WeeklyProgress
import com.fuerzasalvaje.senda.domain.usecase.auth.ObserveSessionUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.EditPdfMarkdownUseCase
import com.fuerzasalvaje.senda.ui.components.AiContentController
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObserveWeekUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ChangeEvaluationStatusUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ObserveEvaluationsUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveTodayExamRoutinesUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveCourseLoadUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveStudyProgressUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.StudyProgress
import com.fuerzasalvaje.senda.ui.navigation.DeepLinks
import kotlinx.coroutines.flow.filterNotNull
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.GenerateStudyTipsUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.ObserveLatestTipUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.ObserveTodayStressUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.SaveStressCheckInUseCase
import com.fuerzasalvaje.senda.ui.components.aiErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class TipUiState(val loading: Boolean = false, val error: String? = null)

data class HomeUiState(
    val loading: Boolean = true,
    val firstName: String = "",
    val plan: Plan = Plan.LAUNCH,
    val today: LocalDate? = null,
    val greeting: String = "Hola",
    val progress: WeeklyProgress = WeeklyProgress(0, 0),
    val conflicts: List<Conflict> = emptyList(),
    val priorities: List<PriorityItem> = emptyList(),
    val courses: List<Course> = emptyList(),
    val hasEvaluations: Boolean = false,
    val selected: Evaluation? = null,
    val stressLevel: Int? = null,
    val criticalCourse: CourseLoad? = null,
    val tip: AIRecommendation? = null,
    val tipState: TipUiState = TipUiState(),
    /** Study progress by evaluation id: estimate minus blocks and Pomodoro minutes. */
    val studyProgress: Map<String, StudyProgress> = emptyMap(),
    val routines: List<ExamRoutine> = emptyList(),
) {
    fun courseOf(evaluation: Evaluation): Course? = courses.firstOrNull { it.id == evaluation.courseId }

    /** The tutor needs today's stress check-in (D-12). */
    val canGenerateTip: Boolean get() = stressLevel != null && !tipState.loading
}

private data class HomeData(
    val firstName: String,
    val plan: Plan,
    val progress: WeeklyProgress,
    val conflicts: List<Conflict>,
    val courses: List<Course>,
    val priorities: List<PriorityItem>,
    val evaluations: List<Evaluation>,
    val selectedId: String?,
)

/** HU-10 to HU-13 dashboard plus the D-12 check-in and the AI tutor tip. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observeWeek: ObserveWeekUseCase,
    observePriorities: ObservePrioritiesUseCase,
    observeEvaluations: ObserveEvaluationsUseCase,
    observeTodayStress: ObserveTodayStressUseCase,
    observeCourseLoad: ObserveCourseLoadUseCase,
    observeLatestTip: ObserveLatestTipUseCase,
    observeStudyProgress: ObserveStudyProgressUseCase,
    observeRoutines: ObserveTodayExamRoutinesUseCase,
    private val saveStress: SaveStressCheckInUseCase,
    private val generateTips: GenerateStudyTipsUseCase,
    private val changeStatus: ChangeEvaluationStatusUseCase,
    private val clock: Clock,
    editPdfMarkdown: EditPdfMarkdownUseCase,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone

    /** "Contenido que leerá la IA" in the detail sheet. */
    val aiContent = AiContentController(editPdfMarkdown, viewModelScope)
    private val selectedId = MutableStateFlow<String?>(null)
    private val tipState = MutableStateFlow(TipUiState())

    private val data = combine(
        observeSession(),
        observeWeek(observeWeek.currentWeekStart()),
        observePriorities(),
        observeEvaluations(),
        selectedId,
    ) { user, week, priorities, evaluations, selected ->
        HomeData(
            firstName = user?.name?.substringBefore(' ').orEmpty(),
            plan = user?.plan ?: Plan.LAUNCH,
            progress = week.progress,
            conflicts = week.conflicts,
            courses = week.courses,
            priorities = priorities,
            evaluations = evaluations,
            selectedId = selected,
        )
    }

    private val extras = combine(observeStudyProgress(), observeRoutines()) { progress, routines -> progress to routines }

    val state: StateFlow<HomeUiState> = combine(
        data,
        observeTodayStress(),
        observeCourseLoad(),
        combine(observeLatestTip(), tipState) { tip, tipUi -> tip to tipUi },
        extras,
    ) { d, stress, load, (tip, tipUi), (progress, routines) ->
        HomeUiState(
            loading = false,
            firstName = d.firstName,
            plan = d.plan,
            today = LocalDate.now(clock),
            greeting = greetingFor(LocalTime.now(clock)),
            progress = d.progress,
            conflicts = d.conflicts,
            priorities = d.priorities,
            courses = d.courses,
            hasEvaluations = d.evaluations.isNotEmpty(),
            selected = d.evaluations.firstOrNull { it.id == d.selectedId },
            stressLevel = stress?.level,
            criticalCourse = load.critical.firstOrNull(),
            tip = tip,
            tipState = tipUi,
            studyProgress = progress,
            routines = routines,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // An evaluation reminder opens this evaluation's detail sheet.
        viewModelScope.launch {
            DeepLinks.pendingEvaluationDetail.filterNotNull().collect { id ->
                DeepLinks.consumeEvaluationDetail()
                selectedId.value = id
            }
        }
    }

    fun checkIn(level: Int) {
        viewModelScope.launch { saveStress(level) }
    }

    /** Calls the AI tutor with today's stress and the ranked priorities; also used to retry. */
    fun generateTip() {
        val stress = state.value.stressLevel ?: return
        if (tipState.value.loading) return
        tipState.value = TipUiState(loading = true)
        viewModelScope.launch {
            generateTips(stress).fold(
                onSuccess = { tipState.value = TipUiState() },
                onFailure = { error -> tipState.update { TipUiState(error = aiErrorMessage(error, TIP_ERROR)) } },
            )
        }
    }

    fun open(evaluation: Evaluation) { selectedId.value = evaluation.id }
    fun close() {
        selectedId.value = null
        aiContent.clearNotice()
    }

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

    companion object {
        const val TIP_ERROR = "No pudimos generar el consejo. Revisa tu conexión e inténtalo de nuevo."
    }
}
