package com.fuerzasalvaje.senda.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.ConflictDetector
import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.Conflict
import com.fuerzasalvaje.senda.domain.model.ConflictType
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationFilter
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.WeeklyProgress
import com.fuerzasalvaje.senda.domain.usecase.calendar.FilterCalendarUseCase
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObserveWeekUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ChangeEvaluationStatusUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.UpdateStudyBlockUseCase
import com.fuerzasalvaje.senda.domain.model.StudyBlockEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalendarUiState(
    val loading: Boolean = true,
    val weekStart: LocalDate,
    val today: LocalDate,
    /** Entries after applying the filter. */
    val entries: List<CalendarEntry> = emptyList(),
    /** Conflicts are computed on the whole week, regardless of the filter. */
    val conflicts: List<Conflict> = emptyList(),
    val progress: WeeklyProgress = WeeklyProgress(0, 0),
    val courses: List<Course> = emptyList(),
    val filter: EvaluationFilter = EvaluationFilter(),
    val selected: CalendarEntry? = null,
    /** Accepted study blocks; hidden while a type filter is active, they follow the course filter. */
    val studyBlocks: List<StudyBlockEntry> = emptyList(),
    val selectedStudy: StudyBlockEntry? = null,
) {
    val days: List<LocalDate> get() = (0L until 7L).map(weekStart::plusDays)
    val clashingIds: Set<String> get() = ConflictDetector.clashingIds(conflicts)
    fun clashOn(day: LocalDate) = conflicts.any { it.date == day && it.type == ConflictType.CLASH }
    fun busyOn(day: LocalDate) = conflicts.any { it.date == day && it.type == ConflictType.BUSY_DAY }
    fun entriesOn(day: LocalDate, zone: ZoneId) = entries.filter { it.start.atZone(zone).toLocalDate() == day }
    fun studyBlocksOn(day: LocalDate, zone: ZoneId) = studyBlocks.filter { it.block.startAt.atZone(zone).toLocalDate() == day }
}

/** HU-05 to HU-08: weekly grid, filters, conflicts and quick status changes. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observeWeek: ObserveWeekUseCase,
    private val filterCalendar: FilterCalendarUseCase,
    private val changeStatus: ChangeEvaluationStatusUseCase,
    private val updateStudyBlock: UpdateStudyBlockUseCase,
    private val clock: Clock,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone
    private val weekStart = MutableStateFlow(observeWeek.currentWeekStart())
    private val filter = MutableStateFlow(EvaluationFilter())
    private val selectedId = MutableStateFlow<String?>(null)
    private val selectedStudyId = MutableStateFlow<String?>(null)
    private val selection = combine(selectedId, selectedStudyId) { entry, study -> entry to study }

    val state: StateFlow<CalendarUiState> =
        combine(weekStart.flatMapLatest { observeWeek(it) }, filter, selection) { agenda, currentFilter, (selected, selectedStudy) ->
            val visibleStudy = if (currentFilter.types.isNotEmpty()) emptyList() else
                agenda.studyBlocks.filter { currentFilter.courseId == null || it.block.courseId == currentFilter.courseId }
            CalendarUiState(
                loading = false,
                weekStart = agenda.weekStart,
                today = LocalDate.now(clock),
                entries = filterCalendar(agenda.entries, currentFilter),
                conflicts = agenda.conflicts,
                progress = agenda.progress,
                courses = agenda.courses,
                filter = currentFilter,
                selected = agenda.entries.firstOrNull { it.id == selected },
                studyBlocks = visibleStudy,
                selectedStudy = agenda.studyBlocks.firstOrNull { it.block.id == selectedStudy },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarUiState(weekStart = weekStart.value, today = LocalDate.now(clock)),
        )

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }
    fun nextWeek() = weekStart.update { it.plusWeeks(1) }
    fun goToToday() { weekStart.value = observeWeek.currentWeekStart() }

    fun toggleType(type: EvaluationType) = filter.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }
    fun clearTypes() = filter.update { it.copy(types = emptySet()) }
    fun selectCourse(courseId: String?) = filter.update { it.copy(courseId = courseId) }

    fun open(entry: CalendarEntry) { selectedId.value = entry.id }
    fun close() { selectedId.value = null }

    fun openStudy(entry: StudyBlockEntry) { selectedStudyId.value = entry.block.id }
    fun closeStudy() { selectedStudyId.value = null }

    fun markStudyDone() = withSelectedStudy { updateStudyBlock.markDone(it) }
    fun removeStudy() = withSelectedStudy { updateStudyBlock.remove(it) }

    private fun withSelectedStudy(action: suspend (com.fuerzasalvaje.senda.domain.model.StudyBlock) -> Unit) {
        val block = state.value.selectedStudy?.block ?: return
        selectedStudyId.value = null
        viewModelScope.launch { action(block) }
    }

    fun setStatus(evaluation: Evaluation, status: EvaluationStatus) {
        viewModelScope.launch { changeStatus(evaluation, status) }
    }

    fun setPhaseStatus(project: Project, phaseId: String, status: PhaseStatus) {
        viewModelScope.launch { changeStatus.changePhase(project, phaseId, status) }
    }
}
