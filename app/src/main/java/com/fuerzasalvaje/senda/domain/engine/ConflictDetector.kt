package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.Conflict
import com.fuerzasalvaje.senda.domain.model.ConflictType
import java.time.Instant
import java.time.ZoneId

/**
 * HU-07 with D-8:
 * - RED "Cruce" when timed evaluations overlap. Touching blocks (end == start) do not overlap.
 * - YELLOW "Día cargado" when a day holds 2 or more evaluations that do not all overlap each other.
 * Project deliveries are deadlines: they never clash, but they count for "Día cargado".
 * Completed evaluations and delivered phases are ignored.
 */
object ConflictDetector {

    fun detect(entries: List<CalendarEntry>, zone: ZoneId): List<Conflict> =
        entries.asSequence()
            .filterNot { it.isDone }
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .toSortedMap()
            .flatMap { (date, dayEntries) ->
                val clashGroups = overlapGroups(dayEntries.filterNot { it.isDeadline })
                val deadlines = dayEntries.filter { it.isDeadline }
                buildList {
                    clashGroups.filter { it.size >= 2 }.forEach { add(Conflict(ConflictType.CLASH, date, it)) }
                    if (clashGroups.size + deadlines.size >= 2) {
                        add(Conflict(ConflictType.BUSY_DAY, date, dayEntries.sortedBy { it.start }))
                    }
                }
            }

    /** Ids of the entries involved in a red clash, for highlighting. */
    fun clashingIds(conflicts: List<Conflict>): Set<String> =
        conflicts.filter { it.type == ConflictType.CLASH }.flatMap { group -> group.entries.map { it.id } }.toSet()

    /** Sweeps the day in time order, grouping blocks that overlap (directly or through a chain). */
    private fun overlapGroups(timed: List<CalendarEntry>): List<List<CalendarEntry>> {
        val groups = mutableListOf<MutableList<CalendarEntry>>()
        var groupEnd = Instant.MIN
        for (entry in timed.sortedBy { it.start }) {
            if (groups.isNotEmpty() && entry.start.isBefore(groupEnd)) {
                groups.last().add(entry)
                if (entry.end.isAfter(groupEnd)) groupEnd = entry.end
            } else {
                groups.add(mutableListOf(entry))
                groupEnd = entry.end
            }
        }
        return groups
    }
}
