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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notification channels shown in the system settings (labels in Spanish, like the UI). */
enum class SendaChannel(val id: String, val label: String, val description: String, val importance: Int) {
    REMINDERS(
        "reminders", "Recordatorios",
        "Avisos de exámenes, laboratorios y entregas, y el resumen de las 07:30.",
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    WELLBEING(
        "wellbeing", "Bienestar",
        "Recordatorio de descanso y rutinas entre exámenes.",
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    FOCUS(
        "focus", "Pomodoro",
        "Fin de cada bloque de enfoque o pausa.",
        NotificationManager.IMPORTANCE_HIGH,
    ),
}

@Singleton
class SendaNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
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

    @SuppressLint("MissingPermission") // Checked by canPost().
    fun post(id: Int, channel: SendaChannel, title: String, text: String) {
        if (!canPost()) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
