package com.fuerzasalvaje.senda.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * One block on the calendar: an exam, a lab or one phase of a project.
 * Project phases are deadlines: they start and end at the same instant (D-8).
 */
data class CalendarEntry(
    val evaluation: Evaluation,
    val course: Course?,
    val start: Instant,
    val end: Instant,
    val phase: ProjectPhase? = null,
) {
    val id: String get() = phase?.let { "${evaluation.id}:${it.id}" } ?: evaluation.id
    val type: EvaluationType get() = evaluation.type
    val title: String get() = phase?.let { "${evaluation.title} · ${it.name}" } ?: evaluation.title
    val isDeadline: Boolean get() = start == end
    val isDone: Boolean
        get() = phase?.let { it.status == PhaseStatus.DELIVERED } ?: (evaluation.status == EvaluationStatus.COMPLETED)
}

enum class ConflictType {
    /** RED: two evaluations overlap in time. */
    CLASH,
    /** YELLOW: two or more evaluations on the same day without overlapping. */
    BUSY_DAY,
}

data class Conflict(
    val type: ConflictType,
    val date: LocalDate,
    val entries: List<CalendarEntry>,
)

data class WeeklyProgress(val completed: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
    val percent: Int get() = (fraction * 100).toInt()
}

/** Calendar filter: no types selected means "Todos". Filters combine (types AND course). */
data class EvaluationFilter(
    val types: Set<EvaluationType> = emptySet(),
    val courseId: String? = null,
) {
    fun matches(entry: CalendarEntry): Boolean =
        (types.isEmpty() || entry.type in types) && (courseId == null || entry.evaluation.courseId == courseId)

    val isActive: Boolean get() = types.isNotEmpty() || courseId != null
}

data class WeekAgenda(
    val weekStart: LocalDate,
    val entries: List<CalendarEntry>,
    val conflicts: List<Conflict>,
    val progress: WeeklyProgress,
    val courses: List<Course>,
) {
    val days: List<LocalDate> get() = (0L until 7L).map(weekStart::plusDays)
}
