package com.fuerzasalvaje.senda.data.mapper

import com.fuerzasalvaje.senda.data.local.entity.AIRecommendationEntity
import com.fuerzasalvaje.senda.data.local.entity.PomodoroSessionEntity
import com.fuerzasalvaje.senda.data.local.entity.QuestionEntity
import com.fuerzasalvaje.senda.data.local.entity.SimulacrumEntity
import com.fuerzasalvaje.senda.data.local.entity.StressCheckInEntity
import com.fuerzasalvaje.senda.data.local.entity.StudyBlockEntity
import com.fuerzasalvaje.senda.data.remote.dto.AIRecommendationDto
import com.fuerzasalvaje.senda.data.remote.dto.PomodoroSessionDto
import com.fuerzasalvaje.senda.data.remote.dto.QuestionDto
import com.fuerzasalvaje.senda.data.remote.dto.SimulacrumDto
import com.fuerzasalvaje.senda.data.remote.dto.StressCheckInDto
import com.fuerzasalvaje.senda.data.remote.dto.StudyBlockDto
import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.QuestionReport
import com.fuerzasalvaje.senda.domain.model.QuestionSource
import com.fuerzasalvaje.senda.domain.model.RecommendationType
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.StressCheckIn
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import java.time.LocalDate

// Question

fun Question.toEntity(ownerUid: String) = QuestionEntity(
    id = id, ownerUid = ownerUid, courseId = courseId, evaluationId = evaluationId,
    statement = statement, scenario = scenario, options = options,
    correctOptionIndex = correctOptionIndex, explanation = explanation,
    source = source.name, createdAt = createdAt.toMillis(), reportReason = reportReason?.name,
)

fun QuestionEntity.toDomain() = Question(
    id = id, courseId = courseId, evaluationId = evaluationId,
    statement = statement, scenario = scenario, options = options,
    correctOptionIndex = correctOptionIndex, explanation = explanation,
    source = enumOrDefault(source, QuestionSource.AI), createdAt = createdAt.toInstant(),
    reportReason = reportReason.toReport(),
)

fun Question.toDto() = QuestionDto(
    courseId = courseId, evaluationId = evaluationId, statement = statement, scenario = scenario,
    options = options, correctOptionIndex = correctOptionIndex, explanation = explanation,
    source = source.name, createdAt = createdAt.toMillis(), reportReason = reportReason?.name,
)

fun QuestionDto.toDomain(id: String) = Question(
    id = id, courseId = courseId, evaluationId = evaluationId, statement = statement,
    scenario = scenario, options = options, correctOptionIndex = correctOptionIndex,
    explanation = explanation, source = enumOrDefault(source, QuestionSource.AI),
    createdAt = createdAt.toInstant(), reportReason = reportReason.toReport(),
)

private fun String?.toReport(): QuestionReport? = this?.let { name -> QuestionReport.entries.firstOrNull { it.name == name } }

// Simulacrum

fun Simulacrum.toEntity(ownerUid: String) = SimulacrumEntity(
    id = id, ownerUid = ownerUid, courseId = courseId, evaluationId = evaluationId,
    questionIds = questionIds, answers = answers.map { it.toData() },
    timeLimitMinutes = timeLimitMinutes, startedAt = startedAt.toMillis(),
    finishedAt = finishedAt?.toMillis(), optionOrders = encodeOptionOrders(optionOrders),
)

fun SimulacrumEntity.toDomain() = Simulacrum(
    id = id, courseId = courseId, evaluationId = evaluationId, questionIds = questionIds,
    answers = answers.map { it.toDomain() }, timeLimitMinutes = timeLimitMinutes,
    startedAt = startedAt.toInstant(), finishedAt = finishedAt?.toInstant(),
    optionOrders = decodeOptionOrders(optionOrders),
)

fun Simulacrum.toDto() = SimulacrumDto(
    courseId = courseId, evaluationId = evaluationId, questionIds = questionIds,
    answers = answers.map { it.toData() }, timeLimitMinutes = timeLimitMinutes,
    startedAt = startedAt.toMillis(), finishedAt = finishedAt?.toMillis(),
    optionOrders = encodeOptionOrders(optionOrders),
)

fun SimulacrumDto.toDomain(id: String) = Simulacrum(
    id = id, courseId = courseId, evaluationId = evaluationId, questionIds = questionIds,
    answers = answers.map { it.toDomain() }, timeLimitMinutes = timeLimitMinutes,
    startedAt = startedAt.toInstant(), finishedAt = finishedAt?.toInstant(),
    optionOrders = decodeOptionOrders(optionOrders),
)

/** {"q1": [2, 0, 3, 1]} -> ["q1|2,0,3,1"] */
fun encodeOptionOrders(orders: Map<String, List<Int>>): List<String> =
    orders.map { (id, order) -> "$id|${order.joinToString(",")}" }

/** Unreadable entries are dropped: the question then shows its stored order. */
fun decodeOptionOrders(values: List<String>): Map<String, List<Int>> = values.mapNotNull { value ->
    val id = value.substringBeforeLast('|', "")
    val order = value.substringAfterLast('|').split(',').mapNotNull { it.trim().toIntOrNull() }
    if (id.isEmpty() || order.isEmpty()) null else id to order
}.toMap()

// AIRecommendation

fun AIRecommendation.toEntity(ownerUid: String) = AIRecommendationEntity(
    id = id, ownerUid = ownerUid, type = type.name, content = content, courseId = courseId,
    evaluationId = evaluationId, stressLevel = stressLevel, createdAt = createdAt.toMillis(),
)

fun AIRecommendationEntity.toDomain() = AIRecommendation(
    id = id, type = enumOrDefault(type, RecommendationType.STUDY_TIPS), content = content,
    courseId = courseId, evaluationId = evaluationId, stressLevel = stressLevel,
    createdAt = createdAt.toInstant(),
)

fun AIRecommendation.toDto() = AIRecommendationDto(
    type = type.name, content = content, courseId = courseId, evaluationId = evaluationId,
    stressLevel = stressLevel, createdAt = createdAt.toMillis(),
)

fun AIRecommendationDto.toDomain(id: String) = AIRecommendation(
    id = id, type = enumOrDefault(type, RecommendationType.STUDY_TIPS), content = content,
    courseId = courseId, evaluationId = evaluationId, stressLevel = stressLevel,
    createdAt = createdAt.toInstant(),
)

// PomodoroSession

fun PomodoroSession.toEntity(ownerUid: String) = PomodoroSessionEntity(
    id = id, ownerUid = ownerUid, courseId = courseId, evaluationId = evaluationId,
    startedAt = startedAt.toMillis(), endedAt = endedAt?.toMillis(), focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes, longBreakMinutes = longBreakMinutes,
    completedCycles = completedCycles,
)

fun PomodoroSessionEntity.toDomain() = PomodoroSession(
    id = id, courseId = courseId, evaluationId = evaluationId, startedAt = startedAt.toInstant(),
    endedAt = endedAt?.toInstant(), focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes, longBreakMinutes = longBreakMinutes,
    completedCycles = completedCycles,
)

fun PomodoroSession.toDto() = PomodoroSessionDto(
    courseId = courseId, evaluationId = evaluationId, startedAt = startedAt.toMillis(),
    endedAt = endedAt?.toMillis(), focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes, longBreakMinutes = longBreakMinutes,
    completedCycles = completedCycles,
)

fun PomodoroSessionDto.toDomain(id: String) = PomodoroSession(
    id = id, courseId = courseId, evaluationId = evaluationId, startedAt = startedAt.toInstant(),
    endedAt = endedAt?.toInstant(), focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes, longBreakMinutes = longBreakMinutes,
    completedCycles = completedCycles,
)

// StudyBlock

fun StudyBlock.toEntity(ownerUid: String) = StudyBlockEntity(
    id = id, ownerUid = ownerUid, courseId = courseId, evaluationId = evaluationId,
    startAt = startAt.toMillis(), endAt = endAt.toMillis(), status = status.name,
)

fun StudyBlockEntity.toDomain() = StudyBlock(
    id = id, courseId = courseId, evaluationId = evaluationId, startAt = startAt.toInstant(),
    endAt = endAt.toInstant(), status = enumOrDefault(status, StudyBlockStatus.SUGGESTED),
)

fun StudyBlock.toDto() = StudyBlockDto(
    courseId = courseId, evaluationId = evaluationId, startAt = startAt.toMillis(),
    endAt = endAt.toMillis(), status = status.name,
)

fun StudyBlockDto.toDomain(id: String) = StudyBlock(
    id = id, courseId = courseId, evaluationId = evaluationId, startAt = startAt.toInstant(),
    endAt = endAt.toInstant(), status = enumOrDefault(status, StudyBlockStatus.SUGGESTED),
)

// StressCheckIn (id = ISO date)

fun StressCheckIn.toEntity(ownerUid: String) = StressCheckInEntity(
    id = id, ownerUid = ownerUid, level = level, createdAt = createdAt.toMillis(),
)

fun StressCheckInEntity.toDomain() = StressCheckIn(
    date = LocalDate.parse(id), level = level, createdAt = createdAt.toInstant(),
)

fun StressCheckIn.toDto() = StressCheckInDto(level = level, createdAt = createdAt.toMillis())

fun StressCheckInDto.toDomain(id: String) = StressCheckIn(
    date = LocalDate.parse(id), level = level, createdAt = createdAt.toInstant(),
)
