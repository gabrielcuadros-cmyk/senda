package com.fuerzasalvaje.senda.ui.evaluation

import com.fuerzasalvaje.senda.domain.engine.StudyHoursEstimator
import com.fuerzasalvaje.senda.domain.engine.ValidationError
import com.fuerzasalvaje.senda.domain.engine.Validators
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.LabModality
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class PhaseDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val date: LocalDate? = null,
    val time: LocalTime = LocalTime.of(23, 59),
    val status: PhaseStatus = PhaseStatus.PENDING,
) {
    val invalid: Boolean get() = name.isBlank() || date == null
}

data class RubricDraft(
    val key: String = UUID.randomUUID().toString(),
    val criterion: String = "",
    val score: String = "",
) {
    val invalid: Boolean get() = criterion.isBlank() || (Validators.parseNumber(score) ?: 0.0) <= 0.0
}

/** Reschedule confirmation (HU-09): old and new main dates. */
data class RescheduleRequest(val from: Instant?, val to: Instant?)

/**
 * Raw form input. Text fields keep what the user typed; [errors] are computed live
 * and shown once [showErrors] is set by the first save attempt.
 */
data class EvaluationFormUiState(
    val loading: Boolean = false,
    val isEdit: Boolean = false,
    val courses: List<Course> = emptyList(),
    val type: EvaluationType = EvaluationType.EXAM,
    val courseId: String? = null,
    val title: String = "",
    val difficulty: Int = 3,
    val weight: String = "",
    val studyHours: Double = StudyHoursEstimator.suggested(3),
    /** True once the student edits the hours: difficulty changes stop overwriting them. */
    val studyHoursEdited: Boolean = false,
    val status: EvaluationStatus = EvaluationStatus.PENDING,
    // Exam and lab
    val date: LocalDate? = null,
    val startTime: LocalTime = LocalTime.of(8, 0),
    val endTime: LocalTime? = null,
    val pdfPath: String? = null,
    val importingPdf: Boolean = false,
    /** "Contenido que leerá la IA": Markdown made on the device from the PDF, editable. */
    val pdfMarkdown: String = "",
    val pdfSummarized: Boolean = false,
    val extractingPdf: Boolean = false,
    /** The PDF has no readable text (scanned): nothing of it reaches the AI. */
    val pdfNoText: Boolean = false,
    // Exam
    val examType: ExamType = ExamType.PARTIAL,
    val room: String = "",
    val syllabus: String = "",
    // Lab
    val guideNumber: String = "",
    val tools: List<String> = emptyList(),
    val modality: LabModality = LabModality.INDIVIDUAL,
    // Project
    val members: List<String> = emptyList(),
    val phases: List<PhaseDraft> = Project.DEFAULT_PHASE_NAMES.map { PhaseDraft(name = it) },
    val rubric: List<RubricDraft> = emptyList(),
    // Flow
    val showErrors: Boolean = false,
    val courseWeightTotal: Double? = null,
    val reschedule: RescheduleRequest? = null,
    val confirmDelete: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val weightValue: Double? get() = Validators.parseNumber(weight)
    val suggestedHours: Double get() = StudyHoursEstimator.suggested(difficulty)

    fun startInstant(zone: ZoneId): Instant? = date?.atTime(startTime)?.atZone(zone)?.toInstant()
    fun endInstant(zone: ZoneId): Instant? = endTime?.let { date?.atTime(it)?.atZone(zone)?.toInstant() }

    fun errors(zone: ZoneId): Set<ValidationError> = buildSet {
        if (courseId == null) add(ValidationError.COURSE_REQUIRED)
        Validators.title(title)?.let(::add)
        Validators.weight(weightValue)?.let(::add)
        Validators.studyHours(studyHours)?.let(::add)
        when (type) {
            EvaluationType.EXAM, EvaluationType.LAB -> Validators.dateRange(startInstant(zone), endInstant(zone))?.let(::add)
            EvaluationType.PROJECT -> {
                if (phases.isEmpty()) add(ValidationError.PHASES_REQUIRED)
                if (phases.any { it.invalid }) add(ValidationError.PHASE_INVALID)
                if (rubric.any { it.invalid }) add(ValidationError.RUBRIC_INVALID)
            }
        }
    }

    fun visibleError(error: ValidationError, zone: ZoneId): String? =
        if (showErrors && error in errors(zone)) error.message else null
}
