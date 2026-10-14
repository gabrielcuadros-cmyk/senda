package com.fuerzasalvaje.senda.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import java.time.Duration

private val OffsetLabels = listOf(
    Duration.ofDays(3) to "3 días antes",
    Duration.ofDays(1) to "1 día antes",
    Duration.ofHours(2) to "2 horas antes",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(onBack: () -> Unit, viewModel: ReminderSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordatorios") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val setting = state.setting
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationPermissionCard()
            SendaCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Avisos de evaluaciones",
                    subtitle = "Exámenes, laboratorios y entregas de proyecto pendientes",
                    checked = setting.enabled,
                    onChange = viewModel::setEnabled,
                )
                OffsetLabels.forEach { (offset, label) ->
                    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = offset in setting.evaluationOffsets,
                            onCheckedChange = { viewModel.setOffset(offset, it) },
                            enabled = setting.enabled,
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SendaCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Resumen diario",
                    subtitle = "A las ${setting.dailySummaryTime}: evaluaciones del día y tu prioridad n.º 1",
                    checked = setting.dailySummaryEnabled,
                    onChange = viewModel::setDailySummary,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(
                    title = "Recordatorio de descanso",
                    subtitle = "A las ${setting.restReminderTime}, para cerrar el día y dormir",
                    checked = setting.restReminderEnabled,
                    onChange = viewModel::setRestReminder,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(
                    title = "Rutina entre exámenes",
                    subtitle = "Rutina de 15 minutos cuando tienes exámenes seguidos el mismo día",
                    checked = setting.examRoutineEnabled,
                    onChange = viewModel::setExamRoutine,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Shown while the system blocks Senda's notifications, with a way to allow them. */
@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted
        if (!granted) context.startActivity(appNotificationSettings(context.packageName))
    }
    if (enabled) return

    SendaCard(Modifier.fillMaxWidth(), containerColor = SendaTheme.colors.busyDay.copy(alpha = 0.14f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = SendaTheme.colors.busyDay)
            Spacer(Modifier.width(10.dp))
            Text(
                "Las notificaciones de Senda están desactivadas, así que no recibirás estos avisos.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(appNotificationSettings(context.packageName))
            }
        }) { Text("Activar notificaciones") }
    }
}

private fun appNotificationSettings(packageName: String) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
