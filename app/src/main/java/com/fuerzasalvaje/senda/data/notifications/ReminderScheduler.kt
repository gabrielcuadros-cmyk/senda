package com.fuerzasalvaje.senda.data.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.di.ApplicationScope
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import com.fuerzasalvaje.senda.domain.repository.PomodoroAlarm
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveReminderPlanUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.ReminderPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps WorkManager in line with the student's data (KB-02, KB-04, D-13, D-15): evaluation reminders and
 * between-exam routines are rebuilt whenever evaluations or preferences change; the 07:30 summary and the
 * 22:30 rest reminder are daily periodic work. Everything is cancelled on sign-out.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val session: SessionProvider,
    private val observePlan: ObserveReminderPlanUseCase,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private var job: Job? = null
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            session.uid.collectLatest { uid ->
                if (uid == null) {
                    workManager.cancelAllWorkByTag(TAG_ALL)
                    return@collectLatest
                }
                observePlan().collectLatest { plan ->
                    // Sync can deliver several changes in a row; schedule once things settle.
                    delay(SETTLE_MILLIS)
                    schedule(plan)
                }
            }
        }
    }

    private fun schedule(plan: ReminderPlan) {
        val now = clock.instant()
        workManager.cancelAllWorkByTag(TAG_EVENT)
        plan.evaluationReminders.take(MAX_EVENTS).forEach { reminder ->
            enqueueEvent(
                name = "reminder:${reminder.key}",
                at = reminder.triggerAt,
                now = now,
                channel = SendaChannel.REMINDERS,
                title = ReminderTexts.evaluationTitle(reminder),
                text = ReminderTexts.evaluationText(reminder, clock.zone),
            )
        }
        plan.examRoutines.take(MAX_EVENTS).forEach { routine ->
            enqueueEvent(
                name = "routine:${routine.key}",
                at = routine.triggerAt,
                now = now,
                channel = SendaChannel.WELLBEING,
                title = ReminderTexts.routineTitle(routine),
                text = ReminderTexts.routineText(routine, clock.zone),
            )
        }
        scheduleDaily(plan.setting)
    }

    private fun enqueueEvent(name: String, at: Instant, now: Instant, channel: SendaChannel, title: String, text: String) {
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(NotificationWorker.input(name.hashCode(), channel, title, text))
            .addTag(TAG_EVENT)
            .addTag(TAG_ALL)
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleDaily(setting: ReminderSetting) {
        if (setting.dailySummaryEnabled) {
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(setting.dailySummaryTime).toMillis(), TimeUnit.MILLISECONDS)
                .addTag(TAG_ALL)
                .build()
            workManager.enqueueUniquePeriodicWork(DAILY_SUMMARY, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(DAILY_SUMMARY)
        }

        if (setting.restReminderEnabled) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(setting.restReminderTime).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(NotificationWorker.input(REST_ID, SendaChannel.WELLBEING, ReminderTexts.REST_TITLE, ReminderTexts.REST_TEXT))
                .addTag(TAG_ALL)
                .build()
            workManager.enqueueUniquePeriodicWork(REST_REMINDER, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(REST_REMINDER)
        }
    }

    /** Time until the next occurrence of [time] today or tomorrow. */
    private fun delayUntil(time: LocalTime): Duration {
        val now = clock.instant()
        val today = LocalDate.now(clock).atTime(time).atZone(clock.zone).toInstant()
        val next = if (today.isAfter(now)) today else LocalDate.now(clock).plusDays(1).atTime(time).atZone(clock.zone).toInstant()
        return Duration.between(now, next)
    }

    companion object {
        const val TAG_ALL = "senda"
        private const val TAG_EVENT = "senda-event"
        private const val DAILY_SUMMARY = "daily-summary"
        private const val REST_REMINDER = "rest-reminder"
        private const val REST_ID = 2230
        private const val MAX_EVENTS = 60
        private const val SETTLE_MILLIS = 1_000L
    }
}

/** Pomodoro end notification through WorkManager, so it arrives with the app in the background. */
@Singleton
class WorkManagerPomodoroAlarm @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : PomodoroAlarm {

    override fun schedule(at: Instant, title: String, message: String) {
        val delay = Duration.between(clock.instant(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(NotificationWorker.input(POMODORO_ID, SendaChannel.FOCUS, title, message))
            .addTag(ReminderScheduler.TAG_ALL)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(POMODORO_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(POMODORO_WORK)
    }

    private companion object {
        const val POMODORO_WORK = "pomodoro-end"
        const val POMODORO_ID = 2525
    }
}
