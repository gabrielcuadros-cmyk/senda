package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.SendaRules

/** HU-12 with D-7: 1★ = 2 h ... 5★ = 10 h, editable in half-hour steps. */
object StudyHoursEstimator {
    const val STEP = 0.5
    const val MAX_HOURS = 200.0

    fun suggested(difficulty: Int): Double = SendaRules.suggestedStudyHours(difficulty)

    fun increment(hours: Double): Double = (hours + STEP).coerceAtMost(MAX_HOURS)

    fun decrement(hours: Double): Double = (hours - STEP).coerceAtLeast(0.0)
}
