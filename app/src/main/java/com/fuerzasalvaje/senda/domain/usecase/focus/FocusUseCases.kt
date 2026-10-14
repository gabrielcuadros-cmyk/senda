package com.fuerzasalvaje.senda.domain.usecase.focus

import com.fuerzasalvaje.senda.domain.engine.CalendarPlanner
import com.fuerzasalvaje.senda.domain.engine.EvaluationReminder
import com.fuerzasalvaje.senda.domain.engine.ExamRoutine
import com.fuerzasalvaje.senda.domain.engine.PriorityItem
import com.fuerzasalvaje.senda.domain.engine.ReminderPlanner
import com.fuerzasalvaje.senda.domain.engine.StudyBlockReminder
import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.PomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.repository.SettingsRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import com.fuerzasalvaje.senda.domain.repository.Ticker
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** D-13: Pomodoro durations, default 25/5/15 every 4 cycles. */
class ObservePomodoroConfigUseCase @Inject constructor(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<PomodoroConfig> = settings.observePomodoro()
}

class SavePomodoroConfigUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(config: PomodoroConfig): Result<Unit> {
        if (!config.isValid) return Result.failure(IllegalArgumentException("Pomodoro config out of range"))
        return settings.savePomodoro(config)
    }
}

/** Focus blocks finished today, across every session. */
class ObserveTodayPomodorosUseCase @Inject constructor(
    private val repository: PomodoroSessionRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<Int> = repository.observeAll().map { sessions ->
        val today = LocalDate.now(clock)
        sessions.filter { it.startedAt.atZone(clock.zone).toLocalDate() == today }.sumOf { it.completedCycles }
    }
}

/** D-13, D-15: reminder preferences. */
class ObserveReminderSettingUseCase @Inject constructor(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<ReminderSetting> = settings.observeReminders()
}

class SaveReminderSettingUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(setting: ReminderSetting): Result<Unit> = settings.saveReminders(setting)
}

/** What the notification scheduler must program right now. */
data class ReminderPlan(
    val setting: ReminderSetting,
    val evaluationReminders: List<EvaluationReminder>,
    val examRoutines: List<ExamRoutine>,
    val studyBlockReminders: List<StudyBlockReminder> = emptyList(),
    /** Evaluation titles by id, for the study block alerts. */
    val evaluationTitles: Map<String, String> = emptyMap(),
)

/** KB-02, KB-04, D-13, D-15: future evaluation reminders, study block alerts and between-exam routines, live. */
class ObserveReminderPlanUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val studyBlocks: StudyBlockRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<ReminderPlan> =
        combine(
            evaluations.observeAll(), courses.observeAll(), studyBlocks.observeAll(), settings.observeReminders(),
        ) { evaluationList, courseList, blocks, setting ->
            val entries = CalendarPlanner.entries(evaluationList, courseList)
            val now = clock.instant()
            ReminderPlan(
                setting = setting,
                evaluationReminders = ReminderPlanner.evaluationReminders(entries, setting, now),
                examRoutines = if (setting.examRoutineEnabled) ReminderPlanner.examRoutines(entries, clock.zone, now) else emptyList(),
                studyBlockReminders = if (setting.studyBlockAlertsEnabled) {
                    val completed = evaluationList.filter { it.status == EvaluationStatus.COMPLETED }.map { it.id }.toSet()
                    ReminderPlanner.studyBlockReminders(blocks.filter { it.evaluationId !in completed }, now)
                } else emptyList(),
                evaluationTitles = evaluationList.associate { it.id to it.title },
            )
        }
}

/** D-15: today's back-to-back exams whose second exam has not started yet, for the Focus tab. */
class ObserveTodayExamRoutinesUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val clock: Clock,
    private val ticker: Ticker = Ticker.Never,
) {
    operator fun invoke(): Flow<List<ExamRoutine>> =
        combine(evaluations.observeAll(), courses.observeAll(), ticker.ticks) { evaluationList, courseList, _ ->
            val today = LocalDate.now(clock)
            val startOfDay = today.atStartOfDay(clock.zone).toInstant()
            val now = clock.instant()
            ReminderPlanner.examRoutines(CalendarPlanner.entries(evaluationList, courseList), clock.zone, startOfDay.minusSeconds(1))
                .filter { it.finished.start.atZone(clock.zone).toLocalDate() == today && it.next.start.isAfter(now) }
        }
}

/** D-13: content of the 07:30 summary: today's pending entries and the top priority. */
data class DailySummary(
    val date: LocalDate,
    val today: List<CalendarEntry>,
    val topPriority: PriorityItem?,
) {
    val isEmpty: Boolean get() = today.isEmpty() && topPriority == null
}

class BuildDailySummaryUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val courses: CourseRepository,
    private val priorities: ObservePrioritiesUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(): DailySummary {
        val today = LocalDate.now(clock)
        val entries = CalendarPlanner.entries(evaluations.observeAll().first(), courses.observeAll().first())
        return DailySummary(
            date = today,
            today = entries.filter { !it.isDone && it.start.atZone(clock.zone).toLocalDate() == today },
            topPriority = priorities().first().firstOrNull(),
        )
    }
}
