package com.fuerzasalvaje.senda.ui.evaluation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.StudyHoursEstimator
import com.fuerzasalvaje.senda.domain.engine.Validators
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.LabModality
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.domain.model.RubricItem
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.repository.DocumentStorage
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCoursesUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.DeleteEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ObserveEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.RescheduleEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase
import com.fuerzasalvaje.senda.domain.model.localPdfPath
import com.fuerzasalvaje.senda.domain.model.mainDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** HU-01 to HU-04, HU-09 and HU-12: create, edit, reschedule and delete evaluations. */
@HiltViewModel
class EvaluationFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCourses: ObserveCoursesUseCase,
    private val observeEvaluation: ObserveEvaluationUseCase,
    private val saveEvaluation: SaveEvaluationUseCase,
    private val deleteEvaluation: DeleteEvaluationUseCase,
    private val storage: DocumentStorage,
    private val clock: Clock,
) : ViewModel() {

    private val evaluationId: String? = savedStateHandle.get<String>("evaluationId")
    private val zone: ZoneId get() = clock.zone
    private var original: Evaluation? = null
    private var pending: Evaluation? = null
    private var weightEdited = false

    private val _state = MutableStateFlow(
        EvaluationFormUiState(
            isEdit = evaluationId != null,
            loading = evaluationId != null,
            type = savedStateHandle.get<String>("type")
                ?.let { name -> EvaluationType.entries.firstOrNull { it.name == name } } ?: EvaluationType.EXAM,
        ).let { it.copy(weight = defaultWeight(it.type)) }
    )
    val state: StateFlow<EvaluationFormUiState> = _state.asStateFlow()

    val zoneId: ZoneId get() = zone

    init {
        viewModelScope.launch {
            observeCourses().collect { courses ->
                _state.update { s ->
                    s.copy(courses = courses, courseId = s.courseId ?: courses.singleOrNull()?.id)
                }
            }
        }
        if (evaluationId != null) viewModelScope.launch { load(evaluationId) }
    }

    private suspend fun load(id: String) {
        val evaluation = observeEvaluation(id).firstOrNull()
        original = evaluation
        if (evaluation == null) {
            _state.update { it.copy(loading = false, message = "No encontramos esta evaluación.") }
            return
        }
        weightEdited = true
        _state.update { s ->
            val common = s.copy(
                loading = false,
                type = evaluation.type,
                courseId = evaluation.courseId,
                title = evaluation.title,
                difficulty = evaluation.difficulty,
                weight = Validators.formatNumber(evaluation.weightPercent),
                studyHours = evaluation.studyHours,
                studyHoursEdited = evaluation.studyHours != StudyHoursEstimator.suggested(evaluation.difficulty),
                status = evaluation.status,
                pdfPath = evaluation.localPdfPath,
            )
            when (evaluation) {
                is Exam -> common.copy(
                    date = evaluation.startAt.atZone(zone).toLocalDate(),
                    startTime = evaluation.startAt.atZone(zone).toLocalTime(),
                    endTime = evaluation.endAt?.atZone(zone)?.toLocalTime(),
                    examType = evaluation.examType,
                    room = evaluation.room,
                    syllabus = evaluation.syllabus,
                )
                is Lab -> common.copy(
                    date = evaluation.dueAt.atZone(zone).toLocalDate(),
                    startTime = evaluation.dueAt.atZone(zone).toLocalTime(),
                    endTime = evaluation.endAt?.atZone(zone)?.toLocalTime(),
                    guideNumber = evaluation.guideNumber,
                    tools = evaluation.requiredTools,
                    modality = evaluation.modality,
                )
                is Project -> common.copy(
                    members = evaluation.members,
                    phases = evaluation.phases.map {
                        val local = it.dueAt.atZone(zone)
                        PhaseDraft(it.id, it.name, local.toLocalDate(), local.toLocalTime(), it.status)
                    },
                    rubric = evaluation.rubric.map { RubricDraft(criterion = it.criterion, score = Validators.formatNumber(it.maxScore)) },
                )
            }
        }
        refreshWeightWarning()
    }

    // Common fields

    fun onType(type: EvaluationType) {
        if (_state.value.isEdit) return
        _state.update { it.copy(type = type, weight = if (weightEdited) it.weight else defaultWeight(type)) }
        refreshWeightWarning()
    }

    fun onCourse(id: String) {
        _state.update { it.copy(courseId = id) }
        refreshWeightWarning()
    }

    fun onTitle(value: String) = _state.update { it.copy(title = value) }

    /** Difficulty also updates the suggested hours until the student edits them (D-7). */
    fun onDifficulty(stars: Int) = _state.update {
        val value = stars.coerceIn(SendaRules.DIFFICULTY_RANGE)
        it.copy(difficulty = value, studyHours = if (it.studyHoursEdited) it.studyHours else StudyHoursEstimator.suggested(value))
    }

    fun onWeight(value: String) {
        weightEdited = true
        _state.update { it.copy(weight = value) }
        refreshWeightWarning()
    }

    fun incrementHours() = _state.update { it.copy(studyHours = StudyHoursEstimator.increment(it.studyHours), studyHoursEdited = true) }
    fun decrementHours() = _state.update { it.copy(studyHours = StudyHoursEstimator.decrement(it.studyHours), studyHoursEdited = true) }
    fun useSuggestedHours() = _state.update { it.copy(studyHours = it.suggestedHours, studyHoursEdited = false) }
    fun onStatus(status: EvaluationStatus) = _state.update { it.copy(status = status) }

    // Exam and lab

    fun onDate(value: java.time.LocalDate) = _state.update { it.copy(date = value) }
    fun onStartTime(value: java.time.LocalTime) = _state.update { it.copy(startTime = value) }
    fun onEndTime(value: java.time.LocalTime?) = _state.update { it.copy(endTime = value) }
    fun onExamType(value: ExamType) = _state.update { it.copy(examType = value) }
    fun onRoom(value: String) = _state.update { it.copy(room = value) }
    fun onSyllabus(value: String) = _state.update { it.copy(syllabus = value) }
    fun onGuideNumber(value: String) = _state.update { it.copy(guideNumber = value) }
    fun onModality(value: LabModality) = _state.update { it.copy(modality = value) }
    fun addTool(value: String) {
        val tool = value.trim()
        if (tool.isNotEmpty()) _state.update { it.copy(tools = it.tools + tool) }
    }
    fun removeTool(index: Int) = _state.update { it.copy(tools = it.tools.filterIndexed { i, _ -> i != index }) }

    /** Copies the picked PDF into internal storage (D-11). */
    fun onPdfPicked(uri: String) {
        _state.update { it.copy(importingPdf = true) }
        viewModelScope.launch {
            val previous = _state.value.pdfPath
            storage.importPdf(uri).fold(
                onSuccess = { path ->
                    // A copy made in this session and replaced again is no longer needed.
                    if (previous != null && previous != original?.localPdfPath) storage.delete(previous)
                    _state.update { it.copy(importingPdf = false, pdfPath = path) }
                },
                onFailure = { _state.update { it.copy(importingPdf = false, message = "No se pudo copiar el PDF.") } },
            )
        }
    }

    fun removePdf() = _state.update { it.copy(pdfPath = null) }

    // Project

    fun addMember(value: String) {
        val member = value.trim()
        if (member.isNotEmpty()) _state.update { it.copy(members = it.members + member) }
    }
    fun removeMember(index: Int) = _state.update { it.copy(members = it.members.filterIndexed { i, _ -> i != index }) }
    fun addPhase() = _state.update { it.copy(phases = it.phases + PhaseDraft(name = "Fase ${it.phases.size + 1}")) }
    fun removePhase(id: String) = _state.update { it.copy(phases = it.phases.filterNot { phase -> phase.id == id }) }
    fun updatePhase(id: String, transform: (PhaseDraft) -> PhaseDraft) =
        _state.update { it.copy(phases = it.phases.map { phase -> if (phase.id == id) transform(phase) else phase }) }
    fun addRubricItem() = _state.update { it.copy(rubric = it.rubric + RubricDraft()) }
    fun removeRubricItem(key: String) = _state.update { it.copy(rubric = it.rubric.filterNot { item -> item.key == key }) }
    fun updateRubricItem(key: String, transform: (RubricDraft) -> RubricDraft) =
        _state.update { it.copy(rubric = it.rubric.map { item -> if (item.key == key) transform(item) else item }) }

    // Save, reschedule and delete

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.errors(zone).isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val evaluation = build(s)
        val old = original
        if (old != null && RescheduleEvaluationUseCase.isReschedule(old, evaluation)) {
            pending = evaluation
            _state.update { it.copy(showErrors = true, reschedule = RescheduleRequest(old.mainDate, evaluation.mainDate)) }
            return
        }
        persist(evaluation)
    }

    fun confirmReschedule() {
        val evaluation = pending ?: return
        pending = null
        _state.update { it.copy(reschedule = null) }
        persist(evaluation)
    }

    fun dismissReschedule() {
        pending = null
        _state.update { it.copy(reschedule = null) }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        val evaluation = original ?: return
        _state.update { it.copy(confirmDelete = false, saving = true) }
        viewModelScope.launch {
            deleteEvaluation(evaluation)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun persist(evaluation: Evaluation) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val replacedPdf = original?.localPdfPath?.takeIf { it != evaluation.localPdfPath }
            saveEvaluation(evaluation).fold(
                onSuccess = {
                    replacedPdf?.let { storage.delete(it) }
                    _state.update { it.copy(saving = false, finished = true) }
                },
                onFailure = { _state.update { it.copy(saving = false, message = "No se pudo guardar. Inténtalo de nuevo.") } },
            )
        }
    }

    private fun refreshWeightWarning() {
        val s = _state.value
        val courseId = s.courseId
        val weight = s.weightValue
        if (courseId == null || weight == null) {
            _state.update { it.copy(courseWeightTotal = null) }
            return
        }
        viewModelScope.launch {
            val total = saveEvaluation.courseWeightTotalIfExceeded(courseId, evaluationId, weight)
            _state.update { if (it.courseId == courseId && it.weightValue == weight) it.copy(courseWeightTotal = total) else it }
        }
    }

    private fun build(s: EvaluationFormUiState): Evaluation {
        val id = original?.id ?: UUID.randomUUID().toString()
        val createdAt = original?.createdAt ?: clock.instant()
        val courseId = requireNotNull(s.courseId)
        val weight = requireNotNull(s.weightValue)
        val title = s.title.trim()
        return when (s.type) {
            EvaluationType.EXAM -> Exam(
                id = id, courseId = courseId, title = title, weightPercent = weight, difficulty = s.difficulty,
                studyHours = s.studyHours, status = s.status, createdAt = createdAt,
                startAt = requireNotNull(s.startInstant(zone)), endAt = s.endInstant(zone),
                room = s.room.trim(), examType = s.examType, syllabus = s.syllabus.trim(), syllabusPdfPath = s.pdfPath,
            )
            EvaluationType.LAB -> Lab(
                id = id, courseId = courseId, title = title, weightPercent = weight, difficulty = s.difficulty,
                studyHours = s.studyHours, status = s.status, createdAt = createdAt,
                guideNumber = s.guideNumber.trim(), dueAt = requireNotNull(s.startInstant(zone)), endAt = s.endInstant(zone),
                requiredTools = s.tools, modality = s.modality, guidePdfPath = s.pdfPath,
            )
            EvaluationType.PROJECT -> Project(
                id = id, courseId = courseId, title = title, weightPercent = weight, difficulty = s.difficulty,
                studyHours = s.studyHours, createdAt = createdAt, members = s.members,
                phases = s.phases.map {
                    ProjectPhase(it.id, it.name.trim(), requireNotNull(it.date).atTime(it.time).atZone(zone).toInstant(), it.status)
                },
                rubric = s.rubric.map { RubricItem(it.criterion.trim(), requireNotNull(Validators.parseNumber(it.score))) },
            )
        }
    }

    private fun defaultWeight(type: EvaluationType): String =
        if (type == EvaluationType.EXAM) "" else Validators.formatNumber(SendaRules.DEFAULT_WEIGHT_PERCENT)
}
