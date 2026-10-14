package com.fuerzasalvaje.senda.domain.model

import java.time.Instant

enum class EvaluationType { EXAM, LAB, PROJECT }

enum class EvaluationStatus { PENDING, IN_PROGRESS, COMPLETED }

enum class PhaseStatus { PENDING, IN_PROGRESS, DELIVERED }

enum class ExamType { PARTIAL, FINAL, CASE_STUDY }

enum class LabModality { INDIVIDUAL, GROUP }

/**
 * Common contract of exams, labs and projects (HU-01 to HU-04).
 * Weight is the % of the course grade for every type (D-1); difficulty is 1 to 5 stars.
 */
sealed interface Evaluation {
    val id: String
    val courseId: String
    val title: String
    val weightPercent: Double
    val difficulty: Int
    val studyHours: Double
    val status: EvaluationStatus
    val createdAt: Instant
    val type: EvaluationType

    val difficultyLevel: DifficultyLevel get() = SendaRules.difficultyLevel(difficulty)
}

/** Main date: exam start, lab deadline or the next pending project phase. */
val Evaluation.mainDate: Instant?
    get() = when (this) {
        is Exam -> startAt
        is Lab -> dueAt
        is Project -> nextPendingPhase?.dueAt
    }

/** Local PDF of an exam (syllabus) or a lab (guide). */
val Evaluation.localPdfPath: String?
    get() = when (this) {
        is Exam -> syllabusPdfPath
        is Lab -> guidePdfPath
        is Project -> null
    }

private fun Evaluation.checkInvariants() {
    require(difficulty in SendaRules.DIFFICULTY_RANGE) { "Difficulty must be 1 to 5" }
    require(SendaRules.isValidWeight(weightPercent)) { "Weight must be > 0 and <= 100" }
    require(studyHours >= 0.0) { "Study hours cannot be negative" }
}

data class Exam(
    override val id: String,
    override val courseId: String,
    override val title: String,
    override val weightPercent: Double,
    override val difficulty: Int,
    override val studyHours: Double = SendaRules.suggestedStudyHours(difficulty),
    override val status: EvaluationStatus = EvaluationStatus.PENDING,
    override val createdAt: Instant,
    val startAt: Instant,
    /** Optional; defaults to 2 h when absent (D-8). */
    val endAt: Instant? = null,
    val room: String = "",
    val examType: ExamType,
    /** Main syllabus as text (D-11). */
    val syllabus: String = "",
    /** Local copy in the app's internal storage; never uploaded (D-11). */
    val syllabusPdfPath: String? = null,
) : Evaluation {
    override val type get() = EvaluationType.EXAM

    init {
        checkInvariants()
        require(endAt == null || endAt.isAfter(startAt)) { "Exam end must be after its start" }
    }
}

data class Lab(
    override val id: String,
    override val courseId: String,
    override val title: String,
    override val weightPercent: Double = SendaRules.DEFAULT_WEIGHT_PERCENT,
    override val difficulty: Int,
    override val studyHours: Double = SendaRules.suggestedStudyHours(difficulty),
    override val status: EvaluationStatus = EvaluationStatus.PENDING,
    override val createdAt: Instant,
    val guideNumber: String = "",
    /** Report deadline (HU-02). */
    val dueAt: Instant,
    /** Optional; defaults to 1 h when absent (D-8). */
    val endAt: Instant? = null,
    val requiredTools: List<String> = emptyList(),
    val modality: LabModality = LabModality.INDIVIDUAL,
    val guidePdfPath: String? = null,
) : Evaluation {
    override val type get() = EvaluationType.LAB

    init {
        checkInvariants()
        require(endAt == null || endAt.isAfter(dueAt)) { "Lab end must be after its start" }
    }
}

/** A "trabajo" is a project with a single phase (D-14). Its status derives from its phases (D-17). */
data class Project(
    override val id: String,
    override val courseId: String,
    override val title: String,
    override val weightPercent: Double = SendaRules.DEFAULT_WEIGHT_PERCENT,
    override val difficulty: Int,
    override val studyHours: Double = SendaRules.suggestedStudyHours(difficulty),
    override val createdAt: Instant,
    val members: List<String> = emptyList(),
    val phases: List<ProjectPhase>,
    val rubric: List<RubricItem> = emptyList(),
) : Evaluation {
    override val type get() = EvaluationType.PROJECT

    override val status: EvaluationStatus
        get() = when {
            phases.all { it.status == PhaseStatus.DELIVERED } -> EvaluationStatus.COMPLETED
            phases.any { it.status != PhaseStatus.PENDING } -> EvaluationStatus.IN_PROGRESS
            else -> EvaluationStatus.PENDING
        }

    /** Earliest phase not yet delivered; null when the project is complete. */
    val nextPendingPhase: ProjectPhase?
        get() = phases.filter { it.status != PhaseStatus.DELIVERED }.minByOrNull { it.dueAt }

    init {
        checkInvariants()
        require(phases.isNotEmpty()) { "A project needs at least one phase" }
    }

    companion object {
        val DEFAULT_PHASE_NAMES = listOf("Avance 1", "Avance 2", "Entrega Final")
    }
}

data class ProjectPhase(
    val id: String,
    val name: String,
    val dueAt: Instant,
    val status: PhaseStatus = PhaseStatus.PENDING,
)

data class RubricItem(
    val criterion: String,
    val maxScore: Double,
)
