package com.fuerzasalvaje.senda.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** Importance lives in the course and every evaluation inherits it (D-2). Used only as a tie-breaker. */
enum class Importance(val rank: Int) { LOW(1), MEDIUM(2), HIGH(3) }

data class Course(
    val id: String,
    val name: String,
    val teacher: String? = null,
    val importance: Importance = Importance.MEDIUM,
    /** ARGB color, e.g. 0xFFB5D8FF. */
    val colorArgb: Long,
    val schedule: List<ClassSlot> = emptyList(),
    val createdAt: Instant,
)

/** One weekly class of a course (D-9). */
data class ClassSlot(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val room: String = "",
) {
    init {
        require(end.isAfter(start)) { "Class end must be after its start" }
    }
}
