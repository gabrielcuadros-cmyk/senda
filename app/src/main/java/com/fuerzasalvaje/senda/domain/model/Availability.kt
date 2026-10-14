package com.fuerzasalvaje.senda.domain.model

import java.time.LocalTime

/**
 * Daily study availability (D-9), 08:00–22:00 by default.
 * It must fit between 06:00 and 23:00: the 23:00–06:00 sleep window is protected.
 */
data class Availability(
    val start: LocalTime = SendaRules.DEFAULT_AVAILABILITY_START,
    val end: LocalTime = SendaRules.DEFAULT_AVAILABILITY_END,
) {
    val isValid: Boolean
        get() = !start.isBefore(SendaRules.SLEEP_END) &&
            !end.isAfter(SendaRules.SLEEP_START) &&
            !end.isBefore(start.plusHours(1)) && end.isAfter(start)

    /** Brings any value back inside the allowed window, never into sleep hours. */
    fun sanitized(): Availability {
        val s = start.coerceIn(SendaRules.SLEEP_END, SendaRules.SLEEP_START.minusHours(1))
        val e = end.coerceIn(s.plusHours(1), SendaRules.SLEEP_START)
        return Availability(s, e)
    }
}

private fun LocalTime.coerceIn(min: LocalTime, max: LocalTime): LocalTime = when {
    isBefore(min) -> min
    isAfter(max) -> max
    else -> this
}
