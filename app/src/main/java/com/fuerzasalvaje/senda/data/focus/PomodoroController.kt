package com.fuerzasalvaje.senda.data.focus

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fuerzasalvaje.senda.data.notifications.NotificationWorker
import com.fuerzasalvaje.senda.data.notifications.ReminderScheduler
import com.fuerzasalvaje.senda.data.notifications.SendaChannel
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.di.ApplicationScope
import com.fuerzasalvaje.senda.domain.engine.PomodoroEngine
import com.fuerzasalvaje.senda.domain.model.NotificationTarget
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroRun
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.repository.PomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.repository.PomodoroTimer
import com.fuerzasalvaje.senda.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Pomodoro (KB-05, D-13). Every change is persisted, the end of a step is computed from [PomodoroRun.endsAt]
 * (also after the system closed the app), finished focus blocks are saved as sessions, and the foreground
 * service runs while something is in progress.
 */
@Singleton
class PomodoroController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: PomodoroRunStore,
    private val settings: SettingsRepository,
    private val sessions: PomodoroSessionRepository,
    private val auth: SessionProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) : PomodoroTimer {

    private val lock = Any()
    private var config = store.loadConfig()
    private val state = MutableStateFlow(store.load() ?: PomodoroEngine.idle(config))
    override val run: StateFlow<PomodoroRun> = state.asStateFlow()
    private var watcher: Job? = null
    private var restored = false

    /** Called once from the Application: catches up with time spent while the app was closed. */
    fun restore() {
        if (restored) return
        restored = true
        settle()
        if (state.value.running) watch()
        scope.launch {
            settings.observePomodoro().collect { saved ->
                config = saved
                store.saveConfig(saved)
                mutate { PomodoroEngine.applyConfig(it, saved) }
            }
        }
        scope.launch {
            auth.uid.collect { uid -> if (uid == null && !state.value.isIdle) reset(keepTarget = false) }
        }
        syncService()
    }

    override fun start() {
        if (state.value.running) return
        val now = clock.instant()
        val started = mutate { PomodoroEngine.start(it, now, { UUID.randomUUID().toString() }, config) }
        scheduleEndAlert(started)
        watch()
        syncService()
    }

    override fun pause() {
        if (!state.value.running) return
        watcher?.cancel()
        cancelEndAlert()
        mutate { PomodoroEngine.pause(it, clock.instant()) }
        syncService()
    }

    override fun stop() = reset(keepTarget = true)

    override fun skip() {
        watcher?.cancel()
        cancelEndAlert()
        mutate { PomodoroEngine.skip(it, config) }
        syncService()
    }

    override fun link(evaluationId: String?, courseId: String?, title: String?) {
        mutate { PomodoroEngine.link(it, evaluationId, courseId, title) }
    }

    private fun reset(keepTarget: Boolean) {
        watcher?.cancel()
        cancelEndAlert()
        mutate { PomodoroEngine.idle(config, previous = it.takeIf { keepTarget }) }
        syncService()
    }

    /** Waits for the running step to end, then settles it. */
    private fun watch() {
        watcher?.cancel()
        watcher = scope.launch {
            while (true) {
                val endsAt = state.value.endsAt ?: break
                val wait = Duration.between(clock.instant(), endsAt).toMillis()
                if (wait > 0) delay(wait)
                settle()
                if (!state.value.running) break
            }
            syncService()
        }
    }

    private fun settle() {
        val now = clock.instant()
        var completed: PomodoroSession? = null
        mutate { current ->
            val result = PomodoroEngine.settle(current, now, config)
            completed = result.completed
            result.run
        }
        completed?.let { session -> scope.launch { sessions.save(session) } }
    }

    private fun mutate(change: (PomodoroRun) -> PomodoroRun): PomodoroRun = synchronized(lock) {
        val updated = change(state.value)
        if (updated != state.value) {
            state.value = updated
            store.save(updated)
        }
        updated
    }

    /** The service shows the ongoing notification; it is not needed once the timer is back to idle. */
    private fun syncService() {
        if (state.value.isIdle) {
            PomodoroService.stop(context)
        } else {
            try {
                PomodoroService.start(context)
            } catch (e: IllegalStateException) {
                // Android 12+ refuses to start a foreground service from the background (e.g. process restored
                // by WorkManager). The state is kept and the service starts with the next action in the app.
                Log.w(TAG, "Pomodoro service not started: ${e.message}")
            }
        }
    }

    /** Through WorkManager, so the alert arrives even if the system closes the app. */
    private fun scheduleEndAlert(run: PomodoroRun) {
        val endsAt = run.endsAt ?: return
        val next = PomodoroEngine.next(run.step, config)
        val (title, message) = if (run.step.phase == PomodoroPhase.FOCUS) {
            val pause = if (next.suggestActivePause) " Toca pausa activa: levántate y estírate." else ""
            "¡Pomodoro completado!" to "Toma ${next.minutes} min de pausa.$pause"
        } else {
            "Fin de la pausa" to "Vuelve a enfocarte: ${next.minutes} min."
        }
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(Duration.between(clock.instant(), endsAt).coerceAtLeast(Duration.ZERO).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(NotificationWorker.input(END_ALERT_ID, SendaChannel.STUDY, title, message, NotificationTarget.Focus()))
            .addTag(ReminderScheduler.TAG_ALL)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(END_ALERT_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun cancelEndAlert() {
        WorkManager.getInstance(context).cancelUniqueWork(END_ALERT_WORK)
    }

    private companion object {
        const val TAG = "SendaPomodoro"
        const val END_ALERT_WORK = "pomodoro-end"
        const val END_ALERT_ID = 2525
    }
}
