package com.fuerzasalvaje.senda.domain.usecase.calendar

import com.fuerzasalvaje.senda.domain.engine.CalendarPlanner
import com.fuerzasalvaje.senda.domain.engine.ConflictDetector
import com.fuerzasalvaje.senda.domain.engine.PriorityEngine
import com.fuerzasalvaje.senda.domain.engine.PriorityItem
import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.Conflict
import com.fuerzasalvaje.senda.domain.model.EvaluationFilter
import com.fuerzasalvaje.senda.domain.model.StudyBlockEntry
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import com.fuerzasalvaje.senda.domain.model.WeekAgenda
import com.fuerzasalvaje.senda.domain.model.WeeklyProgress
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** HU-05: everything the weekly calendar needs, updated live from the local cache. */
class ObserveWeekUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val studyBlocks: StudyBlockRepository,
    private val clock: Clock,
) {
    operator fun invoke(weekStart: LocalDate): Flow<WeekAgenda> =
        combine(evaluations.observeAll(), courses.observeAll(), studyBlocks.observeAll()) { evaluationList, courseList, blocks ->
            val week = CalendarPlanner.inWeek(CalendarPlanner.entries(evaluationList, courseList), weekStart, clock.zone)
            val from = weekStart.atStartOfDay(clock.zone).toInstant()
            val to = weekStart.plusDays(7).atStartOfDay(clock.zone).toInstant()
            val evaluationsById = evaluationList.associateBy { it.id }
            val coursesById = courseList.associateBy { it.id }
            WeekAgenda(
                weekStart = weekStart,
                entries = week,
                conflicts = ConflictDetector.detect(week, clock.zone),
                progress = CalendarPlanner.weeklyProgress(week),
                courses = courseList,
                studyBlocks = blocks
                    .filter { it.status != StudyBlockStatus.SUGGESTED && !it.startAt.isBefore(from) && it.startAt.isBefore(to) }
                    .map { StudyBlockEntry(it, it.evaluationId?.let(evaluationsById::get), coursesById[it.courseId]) }
                    .sortedBy { it.block.startAt },
            )
        }

    fun currentWeekStart(): LocalDate = CalendarPlanner.weekStartOf(LocalDate.now(clock))
}

/** HU-06: combinable type and course filters. */
class FilterCalendarUseCase @Inject constructor() {
    operator fun invoke(entries: List<CalendarEntry>, filter: EvaluationFilter): List<CalendarEntry> =
        entries.filter(filter::matches)
}

/** HU-07: red clashes and yellow busy days. */
class DetectConflictsUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(entries: List<CalendarEntry>): List<Conflict> = ConflictDetector.detect(entries, clock.zone)
}

/** HU-08: weekly progress. */
class WeeklyProgressUseCase @Inject constructor() {
    operator fun invoke(weekEntries: List<CalendarEntry>): WeeklyProgress = CalendarPlanner.weeklyProgress(weekEntries)
}

/** HU-10 and HU-11: pending evaluations ranked by the priority engine. */
class ObservePrioritiesUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val engine: PriorityEngine,
) {
    operator fun invoke(): Flow<List<PriorityItem>> =
        combine(evaluations.observeAll(), courses.observeAll()) { evaluationList, courseList ->
            engine.rank(evaluationList, courseList.associate { it.id to it.importance })
        }
}
