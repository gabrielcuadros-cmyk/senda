package com.fuerzasalvaje.senda.data.mapper

import com.fuerzasalvaje.senda.data.model.AnswerData
import com.fuerzasalvaje.senda.data.model.ClassSlotData
import com.fuerzasalvaje.senda.data.model.PhaseData
import com.fuerzasalvaje.senda.data.model.RubricItemData
import com.fuerzasalvaje.senda.domain.model.ClassSlot
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.domain.model.RubricItem
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.SimulacrumAnswer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

internal fun Instant.toMillis(): Long = toEpochMilli()
internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

/** Unknown names (e.g. written by a newer app version) fall back to [default]. */
internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

// Remote data is not trusted to respect the domain invariants.
internal fun sanitizeDifficulty(value: Int): Int = value.coerceIn(SendaRules.DIFFICULTY_RANGE)
internal fun sanitizeWeight(value: Double): Double =
    if (SendaRules.isValidWeight(value)) value else SendaRules.DEFAULT_WEIGHT_PERCENT

internal fun ClassSlot.toData() = ClassSlotData(
    day = day.value,
    start = start.toString(),
    end = end.toString(),
    room = room,
)

internal fun ClassSlotData.toDomain() = ClassSlot(
    day = DayOfWeek.of(day.coerceIn(1, 7)),
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
    room = room,
)

internal fun ProjectPhase.toData() = PhaseData(
    id = id,
    name = name,
    dueAt = dueAt.toMillis(),
    status = status.name,
)

internal fun PhaseData.toDomain() = ProjectPhase(
    id = id,
    name = name,
    dueAt = dueAt.toInstant(),
    status = enumOrDefault(status, PhaseStatus.PENDING),
)

internal fun RubricItem.toData() = RubricItemData(criterion = criterion, maxScore = maxScore)
internal fun RubricItemData.toDomain() = RubricItem(criterion = criterion, maxScore = maxScore)

internal fun SimulacrumAnswer.toData() = AnswerData(
    questionId = questionId,
    selectedOptionIndex = selectedOptionIndex,
    correct = correct,
)

internal fun AnswerData.toDomain() = SimulacrumAnswer(
    questionId = questionId,
    selectedOptionIndex = selectedOptionIndex,
    correct = correct,
)
