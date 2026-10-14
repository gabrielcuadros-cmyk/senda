package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.mainDate
import java.time.LocalDate
import java.time.ZoneId

data class CourseLoad(
    val courseId: String,
    val course: Course?,
    val hours: Double,
    /** Share of the week's total study hours, 0 to 1. */
    val share: Double,
    /** Above 40 % of the week, and only when at least two courses have hours (one course is always 100 %). */
    val isCritical: Boolean = false,
)

data class CourseLoadReport(val loads: List<CourseLoad>, val totalHours: Double) {
    val critical: List<CourseLoad> get() = loads.filter { it.isCritical }
}

/**
 * HU-13: estimated study hours per course for the week. Counts pending evaluations due that week
 * (exam date, lab deadline or next project phase) and flags courses above 40 % of the total.
 */
object CriticalCourseAnalyzer {

    fun analyze(
        evaluations: List<Evaluation>,
        courses: List<Course>,
        weekStart: LocalDate,
        zone: ZoneId,
    ): CourseLoadReport {
        val from = weekStart.atStartOfDay(zone).toInstant()
        val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant()
        val hoursByCourse = evaluations
            .filter { it.status != EvaluationStatus.COMPLETED }
            .filter { evaluation -> evaluation.mainDate?.let { !it.isBefore(from) && it.isBefore(to) } == true }
            .groupBy { it.courseId }
            .mapValues { (_, list) -> list.sumOf { it.studyHours } }
            .filterValues { it > 0.0 }

        val total = hoursByCourse.values.sum()
        val byId = courses.associateBy { it.id }
        val canBeCritical = hoursByCourse.size >= SendaRules.MIN_COURSES_FOR_CRITICAL
        val loads = hoursByCourse
            .map { (courseId, hours) ->
                val share = if (total == 0.0) 0.0 else hours / total
                CourseLoad(courseId, byId[courseId], hours, share, canBeCritical && share > SendaRules.CRITICAL_COURSE_SHARE)
            }
            .sortedByDescending { it.hours }
        return CourseLoadReport(loads, total)
    }
}
