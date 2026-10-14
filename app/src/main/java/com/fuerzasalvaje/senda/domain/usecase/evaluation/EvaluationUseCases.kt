package com.fuerzasalvaje.senda.domain.usecase.evaluation

import com.fuerzasalvaje.senda.domain.engine.Validators
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.localPdfPath
import com.fuerzasalvaje.senda.domain.repository.DocumentStorage
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class ObserveEvaluationUseCase @Inject constructor(private val repository: EvaluationRepository) {
    operator fun invoke(id: String): Flow<Evaluation?> = repository.observeById(id)
}

class ObserveEvaluationsUseCase @Inject constructor(private val repository: EvaluationRepository) {
    operator fun invoke(): Flow<List<Evaluation>> = repository.observeAll()
}

/** Result of a save: [courseWeightTotal] is set when the course weights exceed 100 % (D-4). */
data class SaveOutcome(val courseWeightTotal: Double? = null)

/** Creates or edits an evaluation. Exceeding 100 % in the course only warns (D-4). */
class SaveEvaluationUseCase @Inject constructor(private val repository: EvaluationRepository) {
    suspend operator fun invoke(evaluation: Evaluation): Result<SaveOutcome> {
        val otherWeights = repository.observeByCourse(evaluation.courseId).first()
            .filter { it.id != evaluation.id }
            .map { it.weightPercent }
        return repository.save(evaluation).map {
            SaveOutcome(Validators.courseWeightTotalIfExceeded(otherWeights, evaluation.weightPercent))
        }
    }

    /** Course total for the form warning before saving. */
    suspend fun courseWeightTotalIfExceeded(courseId: String, evaluationId: String?, weight: Double): Double? {
        val others = repository.observeByCourse(courseId).first().filter { it.id != evaluationId }.map { it.weightPercent }
        return Validators.courseWeightTotalIfExceeded(others, weight)
    }
}

/** HU-09: moves an exam or lab to a new start, keeping its duration. Projects move phase by phase. */
class RescheduleEvaluationUseCase @Inject constructor(private val repository: EvaluationRepository) {

    fun reschedule(evaluation: Evaluation, newStart: Instant): Evaluation = when (evaluation) {
        is Exam -> evaluation.copy(
            startAt = newStart,
            endAt = evaluation.endAt?.let { newStart + Duration.between(evaluation.startAt, it) },
        )
        is Lab -> evaluation.copy(
            dueAt = newStart,
            endAt = evaluation.endAt?.let { newStart + Duration.between(evaluation.dueAt, it) },
        )
        is Project -> evaluation
    }

    suspend operator fun invoke(evaluation: Evaluation, newStart: Instant): Result<Unit> =
        repository.save(reschedule(evaluation, newStart))

    companion object {
        /** True when an edit changes any date, so the form must ask for confirmation. */
        fun isReschedule(old: Evaluation, new: Evaluation): Boolean = when {
            old is Exam && new is Exam -> old.startAt != new.startAt || old.endAt != new.endAt
            old is Lab && new is Lab -> old.dueAt != new.dueAt || old.endAt != new.endAt
            old is Project && new is Project ->
                old.phases.associate { it.id to it.dueAt } != new.phases.associate { it.id to it.dueAt }
            else -> false
        }
    }
}

/** HU-09: deletes the evaluation, its local PDF and the study blocks planned for it (and so their alerts). */
class DeleteEvaluationUseCase @Inject constructor(
    private val repository: EvaluationRepository,
    private val storage: DocumentStorage,
    private val studyBlocks: StudyBlockRepository,
) {
    suspend operator fun invoke(evaluation: Evaluation): Result<Unit> {
        evaluation.localPdfPath?.let { storage.delete(it) }
        studyBlocks.observeAll().first().filter { it.evaluationId == evaluation.id }.forEach { studyBlocks.delete(it.id) }
        return repository.delete(evaluation.id)
    }
}

/** HU-08: quick status change. Project status derives from its phases (D-17). */
class ChangeEvaluationStatusUseCase @Inject constructor(private val repository: EvaluationRepository) {

    suspend operator fun invoke(evaluation: Evaluation, status: EvaluationStatus): Result<Unit> = when (evaluation) {
        is Exam -> repository.save(evaluation.copy(status = status))
        is Lab -> repository.save(evaluation.copy(status = status))
        is Project -> repository.save(
            evaluation.copy(phases = evaluation.phases.map { it.copy(status = status.toPhaseStatus()) })
        )
    }

    suspend fun changePhase(project: Project, phaseId: String, status: PhaseStatus): Result<Unit> =
        repository.save(project.copy(phases = project.phases.map { if (it.id == phaseId) it.copy(status = status) else it }))

    private fun EvaluationStatus.toPhaseStatus() = when (this) {
        EvaluationStatus.PENDING -> PhaseStatus.PENDING
        EvaluationStatus.IN_PROGRESS -> PhaseStatus.IN_PROGRESS
        EvaluationStatus.COMPLETED -> PhaseStatus.DELIVERED
    }
}
