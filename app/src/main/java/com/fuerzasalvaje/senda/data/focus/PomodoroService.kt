package com.fuerzasalvaje.senda.data.focus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fuerzasalvaje.senda.R
import com.fuerzasalvaje.senda.data.notifications.SendaChannel
import com.fuerzasalvaje.senda.data.notifications.SendaNotifier
import com.fuerzasalvaje.senda.domain.model.NotificationTarget
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroRun
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * Foreground service of the Pomodoro (type specialUse on Android 14+). Shows a persistent notification with the
 * remaining time (a system countdown, so it needs no per-second updates) and Pausar/Continuar and Detener buttons.
 * The timer itself lives in [PomodoroController]; the service only reflects it.
 */
@AndroidEntryPoint
class PomodoroService : Service() {

    @Inject lateinit var controller: PomodoroController
    @Inject lateinit var notifier: SendaNotifier
    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        // A sticky restart after the system killed the process lands here first.
        controller.restore()
        scope.launch {
            controller.run.collect { run ->
                if (run.isIdle) finish() else notify(run)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must come first: the service was started with startForegroundService.
        if (!enterForeground(controller.run.value)) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.start()
            ACTION_STOP -> controller.stop()
        }
        if (controller.run.value.isIdle) finish()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(run: PomodoroRun): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(run), type)
        true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Cannot enter the foreground: ${e.message}")
        stopSelf()
        false
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("MissingPermission") // Without the permission the system simply hides it.
    private fun notify(run: PomodoroRun) {
        if (notifier.canPost()) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, build(run))
    }

    private fun build(run: PomodoroRun): Notification {
        val phase = when (run.step.phase) {
            PomodoroPhase.FOCUS -> "Enfoque · Pomodoro ${run.step.completedFocus + 1}"
            PomodoroPhase.SHORT_BREAK -> "Pausa corta"
            PomodoroPhase.LONG_BREAK -> "Pausa larga"
        }
        val target = run.targetTitle ?: "Sin evaluación vinculada"
        val builder = NotificationCompat.Builder(this, SendaChannel.STUDY.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(notifier.openIntent(NotificationTarget.Focus(), NOTIFICATION_ID))

        val endsAt = run.endsAt
        when {
            endsAt != null -> builder
                .setContentTitle(phase)
                .setContentText(target)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endsAt.toEpochMilli())
                .setShowWhen(true)
                .addAction(0, "Pausar", action(ACTION_PAUSE))
            run.started -> builder
                .setContentTitle("$phase · en pausa")
                .setContentText("Quedan ${clock(run.remaining)} · $target")
                .setShowWhen(false)
                .addAction(0, "Continuar", action(ACTION_RESUME))
            else -> builder
                .setContentTitle("Listo: $phase")
                .setContentText("${run.step.minutes} min · $target")
                .setShowWhen(false)
                .addAction(0, "Empezar", action(ACTION_RESUME))
        }
        builder.addAction(0, "Detener", action(ACTION_STOP))
        return builder.build()
    }

    private fun action(name: String): PendingIntent = PendingIntent.getForegroundService(
        this,
        name.hashCode(),
        Intent(this, PomodoroService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun clock(remaining: Duration): String {
        val seconds = (remaining.toMillis() + 999) / 1000
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        private const val TAG = "SendaPomodoroService"
        private const val NOTIFICATION_ID = 2526
        private const val ACTION_PAUSE = "com.fuerzasalvaje.senda.pomodoro.PAUSE"
        private const val ACTION_RESUME = "com.fuerzasalvaje.senda.pomodoro.RESUME"
        private const val ACTION_STOP = "com.fuerzasalvaje.senda.pomodoro.STOP"

        @Volatile
        var running: Boolean = false
            private set

        /** Started from an action of the student; a running service follows the controller by itself. */
        fun start(context: Context) {
            if (running) return
            ContextCompat.startForegroundService(context, Intent(context, PomodoroService::class.java))
        }

        fun stop(context: Context) {
            if (running) context.stopService(Intent(context, PomodoroService::class.java))
        }
    }
}
