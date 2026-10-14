package com.fuerzasalvaje.senda.domain.model

import java.time.Duration
import java.time.LocalTime

/** Business constants from docs/CONTEXTO_PROYECTO.md, section 6 and PO decisions D-1 to D-17. */
object SendaRules {

    // Difficulty (HU-04, D-3, D-7)
    val DIFFICULTY_RANGE = 1..5
    fun difficultyLevel(stars: Int): DifficultyLevel = when (stars) {
        in 1..2 -> DifficultyLevel.EASY
        3 -> DifficultyLevel.MEDIUM
        else -> DifficultyLevel.HARD
    }
    fun suggestedStudyHours(stars: Int): Double = stars.coerceIn(DIFFICULTY_RANGE) * 2.0

    // Weight in % of the course grade (D-1, D-4)
    const val DEFAULT_WEIGHT_PERCENT = 10.0
    const val MAX_WEIGHT_PERCENT = 100.0
    fun isValidWeight(percent: Double): Boolean = percent > 0.0 && percent <= MAX_WEIGHT_PERCENT
    fun courseWeightExceeded(weights: List<Double>): Boolean = weights.sum() > MAX_WEIGHT_PERCENT

    // Default durations when no end time is registered (D-8)
    fun defaultDuration(type: EvaluationType): Duration = when (type) {
        EvaluationType.EXAM -> Duration.ofHours(2)
        EvaluationType.LAB -> Duration.ofHours(1)
        EvaluationType.PROJECT -> Duration.ZERO
    }

    // Study time (HU-13, D-9)
    const val CRITICAL_COURSE_SHARE = 0.40
    val DEFAULT_AVAILABILITY_START: LocalTime = LocalTime.of(8, 0)
    val DEFAULT_AVAILABILITY_END: LocalTime = LocalTime.of(22, 0)
    val SLEEP_START: LocalTime = LocalTime.of(23, 0)
    val SLEEP_END: LocalTime = LocalTime.of(6, 0)

    // Mock exams (D-10)
    const val DEFAULT_SIMULACRUM_QUESTIONS = 10
    const val DEFAULT_SIMULACRUM_MINUTES = 20
    const val MAX_GRADE = 20.0
    val READINESS_WEIGHTS = listOf(0.5, 0.3, 0.2)

    // Pomodoro and wellbeing (D-13, D-15)
    const val POMODORO_FOCUS_MINUTES = 25
    const val POMODORO_SHORT_BREAK_MINUTES = 5
    const val POMODORO_LONG_BREAK_MINUTES = 15
    const val POMODORO_CYCLES_BEFORE_LONG_BREAK = 4
    const val ACTIVE_PAUSE_EVERY_POMODOROS = 2
    const val BETWEEN_EXAMS_ROUTINE_MINUTES = 15
    /** The routine is offered only when the gap between two exams of the same day is 15 to 60 minutes. */
    const val ROUTINE_MIN_GAP_MINUTES = 15L
    const val ROUTINE_MAX_GAP_MINUTES = 60L

    // Reminders (D-13)
    const val STUDY_BLOCK_ALERT_MINUTES = 10L

    // Stress check-in (D-12)
    val STRESS_RANGE = 1..5
}

enum class DifficultyLevel { EASY, MEDIUM, HARD }
