package com.fuerzasalvaje.senda.ui.courses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.ValidationError
import com.fuerzasalvaje.senda.domain.engine.ValidationException
import com.fuerzasalvaje.senda.domain.engine.Validators
import com.fuerzasalvaje.senda.domain.model.ClassSlot
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.usecase.course.DeleteCourseUseCase
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCourseUseCase
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCoursesUseCase
import com.fuerzasalvaje.senda.domain.usecase.course.SaveCourseUseCase
import com.fuerzasalvaje.senda.ui.theme.CourseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class CourseListItem(val course: Course, val evaluationCount: Int)

data class CourseListUiState(val loading: Boolean = true, val items: List<CourseListItem> = emptyList())

@HiltViewModel
class CourseListViewModel @Inject constructor(
    observeCourses: ObserveCoursesUseCase,
    evaluations: EvaluationRepository,
) : ViewModel() {
    val state: StateFlow<CourseListUiState> =
        combine(observeCourses(), evaluations.observeAll()) { courses, evaluationList ->
            val counts = evaluationList.groupingBy { it.courseId }.eachCount()
            CourseListUiState(loading = false, items = courses.map { CourseListItem(it, counts[it.id] ?: 0) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseListUiState())
}

/** One editable row of the weekly class schedule (D-9). */
data class SlotDraft(
    val key: String = UUID.randomUUID().toString(),
    val day: DayOfWeek = DayOfWeek.MONDAY,
    val start: LocalTime = LocalTime.of(8, 0),
    val end: LocalTime = LocalTime.of(10, 0),
    val room: String = "",
) {
    val invalid: Boolean get() = Validators.classSlot(start, end) != null
}

data class CourseFormUiState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val name: String = "",
    val teacher: String = "",
    val importance: Importance = Importance.MEDIUM,
    val colorArgb: Long = CourseColors.first(),
    val slots: List<SlotDraft> = emptyList(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val confirmDelete: Boolean = false,
    val evaluationCount: Int = 0,
    val error: String? = null,
) {
    val nameError: String? get() = if (showErrors) Validators.courseName(name)?.message else null
    val slotsError: String? get() = if (showErrors && slots.any { it.invalid }) ValidationError.CLASS_SLOT_INVALID.message else null
}

@HiltViewModel
class CourseFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeCourse: ObserveCourseUseCase,
    private val saveCourse: SaveCourseUseCase,
    private val deleteCourse: DeleteCourseUseCase,
    private val evaluations: EvaluationRepository,
    private val clock: Clock,
) : ViewModel() {

    private val courseId: String? = savedStateHandle.get<String>("courseId")
    private var original: Course? = null

    private val _state = MutableStateFlow(CourseFormUiState(isEdit = courseId != null, loading = courseId != null))
    val state: StateFlow<CourseFormUiState> = _state.asStateFlow()

    init {
        if (courseId != null) viewModelScope.launch { load(courseId) }
    }

    private suspend fun load(id: String) {
        val course = observeCourse(id).firstOrNull()
        original = course
        val count = evaluations.observeByCourse(id).first().size
        _state.update {
            if (course == null) it.copy(loading = false, error = "No encontramos este curso.")
            else it.copy(
                loading = false,
                name = course.name,
                teacher = course.teacher.orEmpty(),
                importance = course.importance,
                colorArgb = course.colorArgb,
                slots = course.schedule.map { slot -> SlotDraft(day = slot.day, start = slot.start, end = slot.end, room = slot.room) },
                evaluationCount = count,
            )
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value) }
    fun onTeacher(value: String) = _state.update { it.copy(teacher = value) }
    fun onImportance(value: Importance) = _state.update { it.copy(importance = value) }
    fun onColor(value: Long) = _state.update { it.copy(colorArgb = value) }
    fun addSlot() = _state.update { it.copy(slots = it.slots + SlotDraft()) }
    fun removeSlot(key: String) = _state.update { it.copy(slots = it.slots.filterNot { slot -> slot.key == key }) }
    fun updateSlot(key: String, transform: (SlotDraft) -> SlotDraft) =
        _state.update { it.copy(slots = it.slots.map { slot -> if (slot.key == key) transform(slot) else slot }) }

    fun save() {
        val s = _state.value
        if (Validators.courseName(s.name) != null || s.slots.any { it.invalid }) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val course = Course(
            id = original?.id ?: UUID.randomUUID().toString(),
            name = s.name,
            teacher = s.teacher,
            importance = s.importance,
            colorArgb = s.colorArgb,
            schedule = s.slots.map { ClassSlot(it.day, it.start, it.end, it.room.trim()) },
            createdAt = original?.createdAt ?: clock.instant(),
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = saveCourse(course)
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(saving = false, saved = true) },
                    onFailure = { e ->
                        it.copy(saving = false, showErrors = true, error = (e as? ValidationException)?.message ?: "No se pudo guardar el curso.")
                    },
                )
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id = original?.id ?: return
        _state.update { it.copy(confirmDelete = false, saving = true) }
        viewModelScope.launch {
            deleteCourse(id).fold(
                onSuccess = { _state.update { it.copy(saving = false, deleted = true) } },
                onFailure = { _state.update { it.copy(saving = false, error = "No se pudo eliminar el curso. Inténtalo de nuevo.") } },
            )
        }
    }
}
