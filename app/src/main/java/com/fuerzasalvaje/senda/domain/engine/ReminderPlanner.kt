package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A notification about one calendar entry, [offset] before it starts (KB-02, KB-04, D-13). */
data class EvaluationReminder(
    val key: String,
    val triggerAt: Instant,
    val entry: CalendarEntry,
    val offset: Duration,
)

/** D-15: 15-minute routine between two exams on the same day, offered when the first one ends. */
data class ExamRoutine(
    val key: String,
    val triggerAt: Instant,
    val finished: CalendarEntry,
    val next: CalendarEntry,
) {
    val gap: Duration get() = Duration.between(finished.end, next.start)
}

object ReminderPlanner {

    /** One reminder per pending entry and offset, only those still in the future. */
    fun evaluationReminders(
        entries: List<CalendarEntry>,
        setting: ReminderSetting,
        now: Instant,
    ): List<EvaluationReminder> {
        if (!setting.enabled) return emptyList()
        return entries
            .filter { !it.isDone && it.start.isAfter(now) }
            .flatMap { entry ->
                setting.evaluationOffsets.distinct().mapNotNull { offset ->
                    val trigger = entry.start - offset
                    if (trigger.isAfter(now)) EvaluationReminder("${entry.id}@$offset", trigger, entry, offset) else null
                }
            }
            .sortedBy { it.triggerAt }
    }

    /**
     * Pending exams that follow each other on the same day without overlapping: the routine is offered
     * when the earlier one ends. Overlapping exams are a clash (HU-07), not a routine.
     */
    fun examRoutines(entries: List<CalendarEntry>, zone: ZoneId, now: Instant): List<ExamRoutine> =
        entries
            .filter { it.type == EvaluationType.EXAM && !it.isDone }
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .values
            .flatMap { day ->
                day.sortedBy { it.start }.zipWithNext().mapNotNull { (first, second) ->
                    if (second.start.isBefore(first.end)) null
                    else ExamRoutine("${first.id}>${second.id}", first.end, first, second)
                }
            }
            .filter { it.triggerAt.isAfter(now) }
            .sortedBy { it.triggerAt }
}
