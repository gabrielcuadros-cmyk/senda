package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class FreeSlotPlannerTest {

    private val monday = LocalDate.of(2026, 10, 12) // TEST_NOW is Monday 08:00 in Lima

    private fun at(day: Long, hour: Int, minute: Int = 0): Instant =
        monday.plusDays(day).atTime(hour, minute).atZone(TEST_ZONE).toInstant()

    private fun task(id: String, dueAt: Instant, minutes: Int) = StudyTask(id, "c1", dueAt, minutes)

    private fun plan(
        tasks: List<StudyTask>,
        busy: List<TimeRange> = emptyList(),
        availability: Availability = Availability(),
        alreadyScheduled: Map<LocalDate, Int> = emptyMap(),
    ) = FreeSlotPlanner.plan(tasks, busy, availability, TEST_NOW, TEST_ZONE, alreadyScheduled)

    private fun StudyBlock.localStart(): LocalTime = startAt.atZone(TEST_ZONE).toLocalTime()
    private fun StudyBlock.day(): LocalDate = startAt.atZone(TEST_ZONE).toLocalDate()

    @Test
    fun `blocks are 50 minutes with a 10 minute pause inside the availability`() {
        // Due Wednesday; Tuesday is the day before and 30 % of 120 min is less than one block.
        val result = plan(listOf(task("e", at(2, 12), 120)))

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0)), result.blocks.map { it.localStart() })
        assertTrue(result.blocks.all { Duration.between(it.startAt, it.endAt) == Duration.ofMinutes(50) })
        assertTrue(result.shortfallMinutes.isEmpty())
    }

    @Test
    fun `never plans inside the protected sleep window`() {
        val result = plan(listOf(task("e", at(4, 12), 3000)), availability = Availability(LocalTime.of(5, 0), LocalTime.of(23, 59)))

        assertTrue(result.blocks.isNotEmpty())
        result.blocks.forEach {
            val start = it.localStart()
            val end = it.endAt.atZone(TEST_ZONE).toLocalTime()
            assertTrue("starts at $start", !start.isBefore(LocalTime.of(6, 0)))
            assertTrue("ends at $end", !end.isAfter(LocalTime.of(23, 0)))
        }
    }

    @Test
    fun `skips classes and evaluations`() {
        val busy = listOf(TimeRange(at(0, 8), at(0, 10)), TimeRange(at(0, 10, 30), at(0, 12, 30)))
        val result = plan(listOf(task("e", at(3, 12), 100)), busy = busy)

        assertEquals(at(0, 12, 30), result.blocks.first().startAt)
        result.blocks.forEach { block -> assertTrue(busy.none { it.overlaps(TimeRange(block.startAt, block.endAt)) }) }
    }

    @Test
    fun `at most 6 study hours per day, counting blocks already scheduled`() {
        val result = plan(listOf(task("e", at(4, 12), 3000)), alreadyScheduled = mapOf(monday to 300))

        val minutesByDay = result.blocks.groupBy { it.day() }.mapValues { (_, b) -> b.size * 50 }
        assertEquals(50, minutesByDay[monday])
        assertTrue(minutesByDay.values.all { it <= 360 })
    }

    @Test
    fun `no more than 30 percent of an evaluation on the day before`() {
        val result = plan(listOf(task("e", at(2, 18), 500)))

        val tuesday = result.blocks.count { it.day() == monday.plusDays(1) }
        assertEquals(3, tuesday) // 150 of 500 minutes = 30 %
        assertEquals(10, result.blocks.size)
    }

    @Test
    fun `higher priority picks first and the rest is reported as shortfall`() {
        val tight = Availability(LocalTime.of(8, 0), LocalTime.of(10, 0)) // two slots per day
        val result = plan(listOf(task("first", at(2, 12), 100), task("second", at(2, 12), 100)), availability = tight)

        assertEquals(listOf("first", "first"), result.blocks.map { it.evaluationId })
        assertEquals(mapOf("second" to 100), result.shortfallMinutes)
    }

    @Test
    fun `blocks end before the deadline and never on its day`() {
        val result = plan(listOf(task("e", at(1, 20), 400)))

        assertTrue(result.blocks.isNotEmpty())
        assertTrue(result.blocks.all { it.day() == monday && !it.endAt.isAfter(at(1, 20)) })
    }
}
