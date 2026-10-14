package com.fuerzasalvaje.senda.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fuerzasalvaje.senda.MainActivity
import com.fuerzasalvaje.senda.R
import com.fuerzasalvaje.senda.domain.model.NotificationTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notification channels shown in the system settings (labels in Spanish, like the UI). */
enum class SendaChannel(val id: String, val label: String, val description: String, val importance: Int) {
    EVALUATIONS(
        "evaluations", "Evaluaciones",
        "Avisos de exámenes, laboratorios y entregas, y el resumen de las 07:30.",
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    STUDY(
        "study", "Estudio",
        "Pomodoro en curso, fin de cada bloque y avisos de tus bloques de estudio.",
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    WELLBEING(
        "wellbeing", "Bienestar",
        "Recordatorio de descanso y rutinas entre exámenes.",
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
}

@Singleton
class SendaNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        LEGACY_CHANNELS.forEach(manager::deleteNotificationChannel)
        SendaChannel.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel.id, channel.label, channel.importance).apply { description = channel.description },
            )
        }
    }

    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Opens MainActivity, which routes to [target]. [requestCode] keeps each notification's extras apart. */
    fun openIntent(target: NotificationTarget, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TARGET, target.encode())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission") // Checked by canPost().
    fun post(id: Int, channel: SendaChannel, title: String, text: String, target: NotificationTarget) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(target, id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val EXTRA_TARGET = "com.fuerzasalvaje.senda.TARGET"
        private val LEGACY_CHANNELS = listOf("reminders", "focus")
    }
}
