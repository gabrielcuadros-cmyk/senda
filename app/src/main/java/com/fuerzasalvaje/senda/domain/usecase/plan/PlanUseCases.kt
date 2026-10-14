package com.fuerzasalvaje.senda.domain.usecase.plan

import com.fuerzasalvaje.senda.domain.engine.CalendarPlanner
import com.fuerzasalvaje.senda.domain.engine.CourseLoadReport
import com.fuerzasalvaje.senda.domain.engine.CriticalCourseAnalyzer
import com.fuerzasalvaje.senda.domain.engine.FreeSlotPlanner
import com.fuerzasalvaje.senda.domain.engine.PriorityEngine
import com.fuerzasalvaje.senda.domain.engine.StudyPlan
import com.fuerzasalvaje.senda.domain.engine.StudyTask
import com.fuerzasalvaje.senda.domain.engine.TimeRange
import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import com.fuerzasalvaje.senda.domain.repository.AvailabilityRepository
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.PomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Study time of one evaluation: its estimate, the minutes already in accepted or done blocks and the
 * Pomodoro focus minutes credited to it.
 */
data class StudyProgress(val totalMinutes: Int, val scheduledMinutes: Int, val focusMinutes: Int) {
    val pendingMinutes: Int get() = (totalMinutes - scheduledMinutes - focusMinutes).coerceAtLeast(0)
}

fun studyProgress(evaluation: Evaluation, blocks: List<StudyBlock>, sessions: List<PomodoroSession> = emptyList()): StudyProgress {
    val agreed = blocks.filter { it.evaluationId == evaluation.id && it.status != StudyBlockStatus.SUGGESTED }
    val scheduled = agreed.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
    // A Pomodoro done inside an agreed block of the same evaluation is already counted by the block.
    val focus = sessions.filter { it.evaluationId == evaluation.id }.sumOf { session ->
        val start = session.startedAt
        val end = session.endedAt ?: (start + Duration.ofMinutes(session.focusedMinutes.toLong()))
        val overlap = agreed.sumOf { block -> overlapMinutes(start, end, block.startAt, block.endAt) }
        (session.focusedMinutes - overlap).coerceAtLeast(0L)
    }
    return StudyProgress((evaluation.studyHours * 60).toInt(), scheduled.toInt(), focus.toInt())
}

private fun overlapMinutes(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Long {
    val start = maxOf(aStart, bStart)
    val end = minOf(aEnd, bEnd)
    return if (end.isAfter(start)) Duration.between(start, end).toMinutes() else 0L
}

/** Study minutes still missing for [evaluation], after the agreed blocks and the completed Pomodoros. */
fun pendingStudyMinutes(evaluation: Evaluation, blocks: List<StudyBlock>, sessions: List<PomodoroSession> = emptyList()): Int =
    studyProgress(evaluation, blocks, sessions).pendingMinutes

/** Live study progress of every evaluation, by id (Home, Plan and Focus). */
class ObserveStudyProgressUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val studyBlocks: StudyBlockRepository,
    private val sessions: PomodoroSessionRepository,
) {
    operator fun invoke(): Flow<Map<String, StudyProgress>> =
        combine(evaluations.observeAll(), studyBlocks.observeAll(), sessions.observeAll()) { evaluationList, blocks, sessionList ->
            evaluationList.associate { it.id to studyProgress(it, blocks, sessionList) }
        }
}

/** One pending evaluation and how much of its study time is still missing. */
data class PendingStudyLine(val evaluationId: String, val title: String, val courseName: String, val progress: StudyProgress)

/** Pending evaluations in priority order with their study progress (Plan de estudio). */
class ObservePendingStudyUseCase @Inject constructor(
    private val priorities: ObservePrioritiesUseCase,
    private val courses: CourseRepository,
    private val progress: ObserveStudyProgressUseCase,
) {
    operator fun invoke(): Flow<List<PendingStudyLine>> =
        combine(priorities(), courses.observeAll(), progress()) { ranked, courseList, progressById ->
            val names = courseList.associate { it.id to it.name }
            ranked.map { it.evaluation }.distinctBy { it.id }.mapNotNull { evaluation ->
                progressById[evaluation.id]?.takeIf { it.totalMinutes > 0 }?.let {
                    PendingStudyLine(evaluation.id, evaluation.title, names[evaluation.courseId].orEmpty(), it)
                }
            }
        }
}

/** HU-13: hours per course this week and the critical course alert. */
class ObserveCourseLoadUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val clock: Clock,
) {
    operator fun invoke(weekStart: LocalDate = CalendarPlanner.weekStartOf(LocalDate.now(clock))): Flow<CourseLoadReport> =
        combine(evaluations.observeAll(), courses.observeAll()) { evaluationList, courseList ->
            CriticalCourseAnalyzer.analyze(evaluationList, courseList, weekStart, clock.zone)
        }
}

class ObserveAvailabilityUseCase @Inject constructor(private val repository: AvailabilityRepository) {
    operator fun invoke(): Flow<Availability> = repository.observe()
}

/** Saves the daily availability; values that would reach into sleep hours are rejected. */
class SaveAvailabilityUseCase @Inject constructor(private val repository: AvailabilityRepository) {
    suspend operator fun invoke(availability: Availability): Result<Unit> =
        if (!availability.isValid) Result.failure(IllegalArgumentException(INVALID_MESSAGE)) else repository.save(availability)

    companion object {
        const val INVALID_MESSAGE = "Elige un horario entre 06:00 y 23:00 de al menos 1 hora."
    }
}

/** A proposal ready to show: blocks plus what they are for. */
data class StudyProposal(
    val plan: StudyPlan,
    val evaluations: Map<String, Evaluation>,
    val courses: Map<String, Course>,
)

/** HU-14: proposes study blocks for free time, by priority. Nothing is saved until accepted. */
class SuggestStudyBlocksUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val studyBlocks: StudyBlockRepository,
    private val availability: AvailabilityRepository,
    private val sessions: PomodoroSessionRepository,
    private val priorityEngine: PriorityEngine,
    private val clock: Clock,
) {
    suspend operator fun invoke(): StudyProposal {
        val now = clock.instant()
        val zone = clock.zone
        val evaluationList = evaluations.observeAll().first()
        val courseList = courses.observeAll().first()
        val blocks = studyBlocks.observeAll().first().filter { it.status != StudyBlockStatus.SUGGESTED }
        val sessionList = sessions.observeAll().first()

        val tasks = priorityEngine.rank(evaluationList, courseList.associate { it.id to it.importance })
            .filter { !it.isOverdue }
            .map { StudyTask(it.evaluation.id, it.evaluation.courseId, it.dueAt, pendingStudyMinutes(it.evaluation, blocks, sessionList)) }
            .filter { it.pendingMinutes > 0 }

        val horizonEnd = tasks.maxOfOrNull { it.dueAt } ?: now
        val busy = classRanges(courseList, now, horizonEnd, zone) +
            CalendarPlanner.entries(evaluationList, courseList).filterNot { it.isDeadline }.map { TimeRange(it.start, it.end) } +
            blocks.map { TimeRange(it.startAt, it.endAt) }
        val minutesByDay = blocks.groupBy { it.startAt.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }.toInt() }

        val plan = FreeSlotPlanner.plan(
            tasks = tasks,
            busy = busy,
            availability = availability.observe().first(),
            now = now,
            zone = zone,
            alreadyScheduledMinutesByDay = minutesByDay,
        )
        return StudyProposal(plan, evaluationList.associateBy { it.id }, courseList.associateBy { it.id })
    }

    /** Weekly classes expanded to real dates between [from] and [to]. */
    private fun classRanges(courses: List<Course>, from: Instant, to: Instant, zone: ZoneId): List<TimeRange> {
        val firstDay = from.atZone(zone).toLocalDate()
        val lastDay = to.atZone(zone).toLocalDate()
        return generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .flatMap { day ->
                courses.asSequence().flatMap { it.schedule }.filter { it.day == day.dayOfWeek }.map { slot ->
                    TimeRange(day.atTime(slot.start).atZone(zone).toInstant(), day.atTime(slot.end).atZone(zone).toInstant())
                }
            }
            .toList()
    }
}

/** HU-14 "Aceptar y agendar": the proposal becomes scheduled blocks on the calendar. */
class AcceptStudyBlocksUseCase @Inject constructor(private val repository: StudyBlockRepository) {
    suspend operator fun invoke(blocks: List<StudyBlock>): Result<Int> {
        blocks.forEach { block ->
            repository.save(block.copy(status = StudyBlockStatus.SCHEDULED)).onFailure { return Result.failure(it) }
        }
        return Result.success(blocks.size)
    }
}

class UpdateStudyBlockUseCase @Inject constructor(private val repository: StudyBlockRepository) {
    suspend fun markDone(block: StudyBlock): Result<Unit> = repository.save(block.copy(status = StudyBlockStatus.DONE))
    suspend fun remove(block: StudyBlock): Result<Unit> = repository.delete(block.id)
}
