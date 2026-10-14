package com.fuerzasalvaje.senda.data.mapper

import com.fuerzasalvaje.senda.data.local.entity.EvaluationEntity
import com.fuerzasalvaje.senda.data.remote.dto.EvaluationDto
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.LabModality
import com.fuerzasalvaje.senda.domain.model.Project

fun Evaluation.toEntity(ownerUid: String): EvaluationEntity {
    val exam = this as? Exam
    val lab = this as? Lab
    val project = this as? Project
    return EvaluationEntity(
        id = id,
        ownerUid = ownerUid,
        courseId = courseId,
        type = type.name,
        title = title,
        weightPercent = weightPercent,
        difficulty = difficulty,
        studyHours = studyHours,
        status = status.name,
        createdAt = createdAt.toMillis(),
        startAt = (exam?.startAt ?: lab?.dueAt ?: project?.nextPendingPhase?.dueAt)?.toMillis(),
        endAt = (exam?.endAt ?: lab?.endAt)?.toMillis(),
        room = exam?.room,
        examType = exam?.examType?.name,
        syllabus = exam?.syllabus,
        syllabusPdfPath = exam?.syllabusPdfPath,
        pdfMarkdown = (exam?.syllabusPdfMarkdown ?: lab?.guidePdfMarkdown)?.takeIf { it.isNotEmpty() },
        pdfSummarized = exam?.syllabusPdfSummarized ?: lab?.guidePdfSummarized ?: false,
        guideNumber = lab?.guideNumber,
        requiredTools = lab?.requiredTools.orEmpty(),
        modality = lab?.modality?.name,
        guidePdfPath = lab?.guidePdfPath,
        members = project?.members.orEmpty(),
        phases = project?.phases.orEmpty().map { it.toData() },
        rubric = project?.rubric.orEmpty().map { it.toData() },
    )
}

fun EvaluationEntity.toDomain(): Evaluation = when (enumOrDefault(type, EvaluationType.EXAM)) {
    EvaluationType.EXAM -> Exam(
        id = id,
        courseId = courseId,
        title = title,
        weightPercent = weightPercent,
        difficulty = difficulty,
        studyHours = studyHours,
        status = enumOrDefault(status, EvaluationStatus.PENDING),
        createdAt = createdAt.toInstant(),
        startAt = requireNotNull(startAt) { "Exam without date" }.toInstant(),
        endAt = endAt?.toInstant(),
        room = room.orEmpty(),
        examType = enumOrDefault(examType, ExamType.PARTIAL),
        syllabus = syllabus.orEmpty(),
        syllabusPdfPath = syllabusPdfPath,
        syllabusPdfMarkdown = pdfMarkdown.orEmpty(),
        syllabusPdfSummarized = pdfSummarized,
    )
    EvaluationType.LAB -> Lab(
        id = id,
        courseId = courseId,
        title = title,
        weightPercent = weightPercent,
        difficulty = difficulty,
        studyHours = studyHours,
        status = enumOrDefault(status, EvaluationStatus.PENDING),
        createdAt = createdAt.toInstant(),
        guideNumber = guideNumber.orEmpty(),
        dueAt = requireNotNull(startAt) { "Lab without deadline" }.toInstant(),
        endAt = endAt?.toInstant(),
        requiredTools = requiredTools,
        modality = enumOrDefault(modality, LabModality.INDIVIDUAL),
        guidePdfPath = guidePdfPath,
        guidePdfMarkdown = pdfMarkdown.orEmpty(),
        guidePdfSummarized = pdfSummarized,
    )
    EvaluationType.PROJECT -> Project(
        id = id,
        courseId = courseId,
        title = title,
        weightPercent = weightPercent,
        difficulty = difficulty,
        studyHours = studyHours,
        createdAt = createdAt.toInstant(),
        members = members,
        phases = phases.map { it.toDomain() },
        rubric = rubric.map { it.toDomain() },
    )
}

/** PDF paths are local only and are not sent to Firestore (D-11); the PDF Markdown is. */
fun Evaluation.toDto(): EvaluationDto {
    val entity = toEntity(ownerUid = "")
    return EvaluationDto(
        courseId = entity.courseId,
        type = entity.type,
        title = entity.title,
        weightPercent = entity.weightPercent,
        difficulty = entity.difficulty,
        studyHours = entity.studyHours,
        status = entity.status,
        createdAt = entity.createdAt,
        startAt = entity.startAt,
        endAt = entity.endAt,
        room = entity.room,
        examType = entity.examType,
        syllabus = entity.syllabus,
        pdfMarkdown = entity.pdfMarkdown,
        pdfSummarized = entity.pdfSummarized,
        guideNumber = entity.guideNumber,
        requiredTools = entity.requiredTools,
        modality = entity.modality,
        members = entity.members,
        phases = entity.phases,
        rubric = entity.rubric,
    )
}

/**
 * Builds the domain object from a remote document. [localPdfPath] keeps the file that only
 * exists on this device (exam syllabus or lab guide).
 */
fun EvaluationDto.toDomain(id: String, localPdfPath: String? = null): Evaluation = EvaluationEntity(
    id = id,
    ownerUid = "",
    courseId = courseId,
    type = type,
    title = title,
    weightPercent = sanitizeWeight(weightPercent),
    difficulty = sanitizeDifficulty(difficulty),
    studyHours = studyHours.coerceAtLeast(0.0),
    status = status,
    createdAt = createdAt,
    startAt = startAt,
    endAt = endAt,
    room = room,
    examType = examType,
    syllabus = syllabus,
    syllabusPdfPath = localPdfPath,
    pdfMarkdown = pdfMarkdown,
    pdfSummarized = pdfSummarized,
    guideNumber = guideNumber,
    requiredTools = requiredTools,
    modality = modality,
    guidePdfPath = localPdfPath,
    members = members,
    phases = phases,
    rubric = rubric,
).toDomain()

/** The local PDF of an evaluation, whichever type it is. */
val EvaluationEntity.localPdfPath: String? get() = syllabusPdfPath ?: guidePdfPath
