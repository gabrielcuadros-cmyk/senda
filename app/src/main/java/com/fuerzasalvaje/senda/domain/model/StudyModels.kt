package com.fuerzasalvaje.senda.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class QuestionSource { AI, MANUAL }

/** A stored mock-exam question. Case questions carry a [scenario] (D-10). */
data class Question(
    val id: String,
    val courseId: String,
    val evaluationId: String? = null,
    val statement: String,
    val scenario: String? = null,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String = "",
    val source: QuestionSource = QuestionSource.AI,
    val createdAt: Instant,
    /** Set when the student reports it; reported questions leave the bank but stay in past results. */
    val reportReason: QuestionReport? = null,
) {
    init {
        require(options.size >= 2) { "A question needs at least two options" }
        require(correctOptionIndex in options.indices) { "Correct option out of range" }
    }

    val isCase: Boolean get() = scenario != null
    val isReported: Boolean get() = reportReason != null
}

/** Why a student reports a question from the results screen. */
enum class QuestionReport { WRONG, CONFUSING, OFF_SYLLABUS }

data class SimulacrumAnswer(
    val questionId: String,
    /** Null when the student did not answer. */
    val selectedOptionIndex: Int?,
    val correct: Boolean,
)

/** A mock exam attempt. Grade is on the 0–20 scale (D-10). */
data class Simulacrum(
    val id: String,
    val courseId: String,
    val evaluationId: String? = null,
    val questionIds: List<String>,
    val answers: List<SimulacrumAnswer> = emptyList(),
    val timeLimitMinutes: Int = SendaRules.DEFAULT_SIMULACRUM_MINUTES,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
) {
    val correctCount: Int get() = answers.count { it.correct }
    val incorrectCount: Int get() = answers.count { it.selectedOptionIndex != null && !it.correct }
    val unansweredCount: Int get() = questionIds.size - correctCount - incorrectCount
    val isFinished: Boolean get() = finishedAt != null
    val grade: Double
        get() = if (questionIds.isEmpty()) 0.0 else correctCount * SendaRules.MAX_GRADE / questionIds.size
}

enum class RecommendationType { STUDY_TIPS, MISTAKE_EXPLANATION }

data class AIRecommendation(
    val id: String,
    val type: RecommendationType,
    val content: String,
    val courseId: String? = null,
    val evaluationId: String? = null,
    val stressLevel: Int? = null,
    val createdAt: Instant,
)

data class PomodoroSession(
    val id: String,
    val courseId: String? = null,
    val evaluationId: String? = null,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val focusMinutes: Int = SendaRules.POMODORO_FOCUS_MINUTES,
    val shortBreakMinutes: Int = SendaRules.POMODORO_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = SendaRules.POMODORO_LONG_BREAK_MINUTES,
    val completedCycles: Int = 0,
) {
    /** Focus minutes actually completed in this session. */
    val focusedMinutes: Int get() = completedCycles * focusMinutes
}

enum class StudyBlockStatus { SUGGESTED, SCHEDULED, DONE }

data class StudyBlock(
    val id: String,
    val courseId: String,
    val evaluationId: String? = null,
    val startAt: Instant,
    val endAt: Instant,
    val status: StudyBlockStatus = StudyBlockStatus.SUGGESTED,
) {
    init {
        require(endAt.isAfter(startAt)) { "Study block end must be after its start" }
    }
}

/** Daily stress check-in, 1 to 5 (D-12). One per day: the id is the ISO date. */
data class StressCheckIn(
    val date: LocalDate,
    val level: Int,
    val createdAt: Instant,
) {
    val id: String get() = date.toString()

    init {
        require(level in SendaRules.STRESS_RANGE) { "Stress level must be 1 to 5" }
    }
}

/**
 * Reminder preferences (D-13, D-15). Evaluation reminders are 3 days, 1 day and 2 hours before by default;
 * the daily summary (07:30) and the rest reminder (22:30) keep their fixed times.
 */
data class ReminderSetting(
    val enabled: Boolean = true,
    val evaluationOffsets: List<Duration> = DEFAULT_OFFSETS,
    val dailySummaryEnabled: Boolean = true,
    val restReminderEnabled: Boolean = true,
    val examRoutineEnabled: Boolean = true,
    val studyBlockAlertsEnabled: Boolean = true,
    val dailySummaryTime: LocalTime = LocalTime.of(7, 30),
    val restReminderTime: LocalTime = LocalTime.of(22, 30),
) {
    companion object {
        val DEFAULT_OFFSETS: List<Duration> = listOf(Duration.ofDays(3), Duration.ofDays(1), Duration.ofHours(2))
    }
}
