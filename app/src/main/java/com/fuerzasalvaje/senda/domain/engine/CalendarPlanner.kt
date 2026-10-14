package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.WeeklyProgress
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Turns evaluations into calendar blocks using the default durations of D-8. */
object CalendarPlanner {

    fun entries(evaluations: List<Evaluation>, courses: List<Course>): List<CalendarEntry> {
        val byId = courses.associateBy { it.id }
        return evaluations.flatMap { evaluation ->
            val course = byId[evaluation.courseId]
            when (evaluation) {
                is Exam -> listOf(
                    CalendarEntry(
                        evaluation, course, evaluation.startAt,
                        evaluation.endAt ?: (evaluation.startAt + SendaRules.defaultDuration(evaluation.type)),
                    )
                )
                is Lab -> listOf(
                    CalendarEntry(
                        evaluation, course, evaluation.dueAt,
                        evaluation.endAt ?: (evaluation.dueAt + SendaRules.defaultDuration(evaluation.type)),
                    )
                )
                is Project -> evaluation.phases.map { phase ->
                    CalendarEntry(evaluation, course, phase.dueAt, phase.dueAt, phase)
                }
            }
        }.sortedBy { it.start }
    }

    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Blocks that start between Monday 00:00 and Sunday 23:59 of the given week. */
    fun inWeek(entries: List<CalendarEntry>, weekStart: LocalDate, zone: ZoneId): List<CalendarEntry> {
        val from = weekStart.atStartOfDay(zone).toInstant()
        val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant()
        return entries.filter { !it.start.isBefore(from) && it.start.isBefore(to) }
    }

    /** HU-08: share of the week's blocks already completed (or delivered, for project phases). */
    fun weeklyProgress(weekEntries: List<CalendarEntry>) =
        WeeklyProgress(completed = weekEntries.count { it.isDone }, total = weekEntries.size)
}
