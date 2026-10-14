package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

data class TimeRange(val start: Instant, val end: Instant) {
    fun overlaps(other: TimeRange): Boolean = start.isBefore(other.end) && other.start.isBefore(end)
}

/** Study still needed for one evaluation, in priority order. */
data class StudyTask(
    val evaluationId: String,
    val courseId: String,
    val dueAt: Instant,
    val pendingMinutes: Int,
)

data class PlannerConfig(
    val studyMinutes: Int = 50,
    val pauseMinutes: Int = 10,
    val maxDailyStudyMinutes: Int = 6 * 60,
    /** Study of one evaluation on a single day; the rest goes to other days. */
    val maxDailyMinutesPerEvaluation: Int = 3 * 60,
    /** Back-to-back blocks allowed before a longer rest. */
    val maxConsecutiveBlocks: Int = 2,
    /** Free time required after [maxConsecutiveBlocks] back-to-back blocks. */
    val restAfterRunMinutes: Int = 60,
    /** Share of an evaluation's hours that may land on the day before it. */
    val dayBeforeMaxShare: Double = 0.30,
    val horizonDays: Long = 14,
)

data class StudyPlan(
    val blocks: List<StudyBlock>,
    /** Minutes that did not fit, by evaluation id. */
    val shortfallMinutes: Map<String, Int>,
)

/**
 * HU-14: spreads pending study hours over free time.
 * - 50-minute blocks followed by a 10-minute pause, inside the daily availability.
 * - Never overlaps classes, evaluations or already scheduled blocks; never uses 23:00–06:00.
 * - Higher-priority tasks pick first; each task is spread round-robin over the days before it.
 * - At most 6 study hours per day, 3 of them for the same evaluation, and no more than 30 % of a task on the
 *   day before it.
 * - After 2 back-to-back blocks (only the 10-minute pause between them) comes at least 1 free hour.
 * - Blocks always end before the deadline and never on the deadline's day.
 */
object FreeSlotPlanner {

    fun plan(
        tasks: List<StudyTask>,
        busy: List<TimeRange>,
        availability: Availability,
        now: Instant,
        zone: ZoneId,
        /** Accepted or done blocks: they count for the daily limits and the back-to-back rule. */
        scheduled: List<StudyBlock> = emptyList(),
        config: PlannerConfig = PlannerConfig(),
        newId: () -> String = { UUID.randomUUID().toString() },
    ): StudyPlan {
        val window = availability.sanitized()
        val start = roundUpToTenMinutes(now)
        val lastDue = tasks.maxOfOrNull { it.dueAt } ?: return StudyPlan(emptyList(), emptyMap())
        val today = start.atZone(zone).toLocalDate()
        val lastDay = minOf(lastDue.atZone(zone).toLocalDate(), today.plusDays(config.horizonDays))

        val sortedBusy = busy.sortedBy { it.start }
        val slotsByDay = generateSequence(today) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .associateWith { day -> freeSlots(day, window, start, sortedBusy, zone, config).toMutableList() }
        val dayOf = { block: StudyBlock -> block.startAt.atZone(zone).toLocalDate() }
        val usedByDay = scheduled.groupBy(dayOf).mapValues { (_, list) -> list.sumOf(::minutesOf) }.toMutableMap()
        val studyByDay = scheduled.groupBy(dayOf)
            .mapValues { (_, list) -> list.map { TimeRange(it.startAt, it.endAt) }.toMutableList() }
            .toMutableMap()

        val blocks = mutableListOf<StudyBlock>()
        val shortfall = mutableMapOf<String, Int>()

        for (task in tasks) {
            if (task.pendingMinutes <= 0) continue
            val needed = ceil(task.pendingMinutes / config.studyMinutes.toDouble()).toInt()
            val dueDay = task.dueAt.atZone(zone).toLocalDate()
            val dayBefore = dueDay.minusDays(1)
            val dayBeforeCap = floor(task.pendingMinutes * config.dayBeforeMaxShare / config.studyMinutes).toInt()
            val days = slotsByDay.keys.filter { it.isBefore(dueDay) }.sorted()
            val usedForTask = scheduled.filter { it.evaluationId == task.evaluationId }
                .groupBy(dayOf).mapValues { (_, list) -> list.sumOf(::minutesOf) }.toMutableMap()

            var placed = 0
            var placedDayBefore = 0
            while (placed < needed) {
                var progress = false
                for (day in days) {
                    if (placed == needed) break
                    if (day == dayBefore && placedDayBefore >= dayBeforeCap) continue
                    if ((usedByDay[day] ?: 0) + config.studyMinutes > config.maxDailyStudyMinutes) continue
                    if ((usedForTask[day] ?: 0) + config.studyMinutes > config.maxDailyMinutesPerEvaluation) continue
                    val slots = slotsByDay.getValue(day)
                    val dayStudy = studyByDay.getOrPut(day) { mutableListOf() }
                    val slot = slots.firstOrNull { !it.end.isAfter(task.dueAt) && keepsRest(it, dayStudy, config) } ?: continue
                    slots.remove(slot)
                    dayStudy += slot
                    blocks += StudyBlock(
                        id = newId(),
                        courseId = task.courseId,
                        evaluationId = task.evaluationId,
                        startAt = slot.start,
                        endAt = slot.end,
                        status = StudyBlockStatus.SUGGESTED,
                    )
                    usedByDay[day] = (usedByDay[day] ?: 0) + config.studyMinutes
                    usedForTask[day] = (usedForTask[day] ?: 0) + config.studyMinutes
                    placed++
                    if (day == dayBefore) placedDayBefore++
                    progress = true
                }
                if (!progress) break
            }
            val missing = task.pendingMinutes - placed * config.studyMinutes
            if (missing > 0) shortfall[task.evaluationId] = missing
        }
        return StudyPlan(blocks.sortedBy { it.startAt }, shortfall)
    }

    /**
     * True if adding [slot] to the day's study keeps every run of back-to-back blocks within
     * [PlannerConfig.maxConsecutiveBlocks]. Two blocks are back-to-back when less than
     * [PlannerConfig.restAfterRunMinutes] separate them.
     */
    private fun keepsRest(slot: TimeRange, dayStudy: List<TimeRange>, config: PlannerConfig): Boolean {
        val rest = Duration.ofMinutes(config.restAfterRunMinutes.toLong())
        val sorted = (dayStudy + slot).sortedBy { it.start }
        val index = sorted.indexOf(slot)
        var first = index
        while (first > 0 && sorted[first].start.isBefore(sorted[first - 1].end + rest)) first--
        var last = index
        while (last < sorted.lastIndex && sorted[last + 1].start.isBefore(sorted[last].end + rest)) last++
        return last - first + 1 <= config.maxConsecutiveBlocks
    }

    private fun minutesOf(block: StudyBlock): Int = Duration.between(block.startAt, block.endAt).toMinutes().toInt()

    /** Free 50-minute slots of one day, each followed by its 10-minute pause. */
    private fun freeSlots(
        day: LocalDate,
        availability: Availability,
        notBefore: Instant,
        busy: List<TimeRange>,
        zone: ZoneId,
        config: PlannerConfig,
    ): List<TimeRange> {
        val dayStart = maxOf(day.atTime(availability.start).atZone(zone).toInstant(), notBefore)
        val dayEnd = day.atTime(availability.end).atZone(zone).toInstant()
        if (!dayStart.isBefore(dayEnd)) return emptyList()

        // Free windows: the availability minus every busy range of that day.
        val windows = mutableListOf<TimeRange>()
        var cursor = dayStart
        for (range in busy) {
            if (!range.end.isAfter(cursor) || !range.start.isBefore(dayEnd)) continue
            if (range.start.isAfter(cursor)) windows += TimeRange(cursor, minOf(range.start, dayEnd))
            cursor = maxOf(cursor, range.end)
            if (!cursor.isBefore(dayEnd)) break
        }
        if (cursor.isBefore(dayEnd)) windows += TimeRange(cursor, dayEnd)

        val study = Duration.ofMinutes(config.studyMinutes.toLong())
        val step = Duration.ofMinutes((config.studyMinutes + config.pauseMinutes).toLong())
        return windows.flatMap { window ->
            generateSequence(window.start) { it + step }
                .takeWhile { !(it + study).isAfter(window.end) }
                .map { TimeRange(it, it + study) }
                .toList()
        }
    }

    private fun roundUpToTenMinutes(instant: Instant): Instant {
        val step = 600L
        val seconds = instant.epochSecond + if (instant.nano > 0) 1 else 0
        return Instant.ofEpochSecond((seconds + step - 1) / step * step)
    }
}
