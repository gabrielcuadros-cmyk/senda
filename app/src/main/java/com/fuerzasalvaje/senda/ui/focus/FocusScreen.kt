package com.fuerzasalvaje.senda.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.ui.components.ChipOption
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.SelectField
import com.fuerzasalvaje.senda.ui.components.NumberStepper
import com.fuerzasalvaje.senda.ui.components.Pill
import com.fuerzasalvaje.senda.ui.components.ProgressRing
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import com.fuerzasalvaje.senda.ui.wellbeing.ExamRoutineCard
import com.fuerzasalvaje.senda.ui.wellbeing.WellbeingContent
import com.fuerzasalvaje.senda.ui.wellbeing.WellbeingSectionCard
import java.time.Duration
import java.time.ZoneId

@Composable
fun FocusScreen(
    onOpenWellbeing: () -> Unit,
    onOpenReminders: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimerCard(state, viewModel, onEdit = { editing = true })
        if (state.run.step.isBreak && state.run.step.suggestActivePause) {
            WellbeingSectionCard(WellbeingContent.activePause, containerColor = SendaTheme.colors.lab)
        }
        state.routines.forEach { ExamRoutineCard(it, viewModel.zone) }
        LinkCard(
            icon = Icons.Rounded.Favorite,
            title = "Bienestar",
            subtitle = "Respiración, pausas activas, descanso y a quién acudir",
            onClick = onOpenWellbeing,
        )
        LinkCard(
            icon = Icons.Rounded.NotificationsActive,
            title = "Recordatorios",
            subtitle = "Avisos de evaluaciones, resumen de las 07:30 y descanso a las 22:30",
            onClick = onOpenReminders,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (editing) {
        PomodoroConfigDialog(
            initial = state.config,
            onSave = { viewModel.saveConfig(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun TimerCard(state: FocusUiState, viewModel: FocusViewModel, onEdit: () -> Unit) {
    val run = state.run
    val colors = SendaTheme.colors
    val (label, pillColor) = when (run.step.phase) {
        PomodoroPhase.FOCUS -> "Enfoque" to colors.exam
        PomodoroPhase.SHORT_BREAK -> "Pausa corta" to colors.lab
        PomodoroPhase.LONG_BREAK -> "Pausa larga" to colors.project
    }
    SendaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(label, pillColor)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit, enabled = !run.running) { Icon(Icons.Rounded.Settings, "Configurar Pomodoro") }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(
                fraction = state.fraction,
                size = 200.dp,
                strokeWidth = 14.dp,
                color = if (run.step.isBreak) colors.easy else MaterialTheme.colorScheme.primary,
                centerLabel = clock(state.remaining),
                caption = cycleCaption(state),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalIconButton(onClick = viewModel::stop, enabled = !run.isIdle) { Icon(Icons.Rounded.Stop, "Detener") }
                if (run.running) {
                    Button(onClick = viewModel::pause) {
                        Icon(Icons.Rounded.Pause, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pausar")
                    }
                } else {
                    Button(onClick = viewModel::start) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (run.started) "Continuar" else if (run.step.isBreak) "Empezar pausa" else "Empezar")
                    }
                }
                FilledTonalIconButton(onClick = viewModel::skip) { Icon(Icons.Rounded.SkipNext, "Saltar") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Hoy: ${state.todayPomodoros} ${if (state.todayPomodoros == 1) "Pomodoro completado" else "Pomodoros completados"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SelectField(
            label = "¿Para qué evaluación estudias?",
            options = listOf(ChipOption<FocusTarget?>(null, "Sin evaluación")) + state.targets.map { ChipOption<FocusTarget?>(it, it.label) },
            selected = state.target,
            onSelect = viewModel::select,
            placeholder = "Sin evaluación",
            enabled = !run.running,
        )
        val progress = state.progress
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Te faltan ${Formats.hours(progress.pendingMinutes / 60.0)} de ${Formats.hours(progress.totalMinutes / 60.0)} · " +
                    "${progress.focusMinutes} min hechos con Pomodoro",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (run.running) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Detén o pausa para cambiar la evaluación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun cycleCaption(state: FocusUiState): String {
    val step = state.run.step
    return when (step.phase) {
        PomodoroPhase.FOCUS -> {
            val left = state.untilLongBreak
            "Pomodoro ${step.completedFocus + 1} · " + if (left == 1) "pausa larga al terminar" else "pausa larga en $left"
        }
        PomodoroPhase.SHORT_BREAK -> "Descansa ${step.minutes} min"
        PomodoroPhase.LONG_BREAK -> "Descansa ${step.minutes} min"
    }
}

private fun clock(remaining: Duration): String {
    // Round up so the timer shows 25:00 at the start and 00:01 just before the end.
    val seconds = (remaining.toMillis() + 999) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun LinkCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SendaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** D-13: every duration is configurable within sensible limits. */
@Composable
private fun PomodoroConfigDialog(initial: PomodoroConfig, onSave: (PomodoroConfig) -> Unit, onDismiss: () -> Unit) {
    var config by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Pomodoro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfigStepper("Enfoque (min)", config.focusMinutes, 5, PomodoroConfig.FOCUS_RANGE) { config = config.copy(focusMinutes = it) }
                ConfigStepper("Pausa corta (min)", config.shortBreakMinutes, 1, PomodoroConfig.BREAK_RANGE) { config = config.copy(shortBreakMinutes = it) }
                ConfigStepper("Pausa larga (min)", config.longBreakMinutes, 5, PomodoroConfig.BREAK_RANGE) { config = config.copy(longBreakMinutes = it) }
                ConfigStepper("Pomodoros antes de la pausa larga", config.cyclesBeforeLongBreak, 1, PomodoroConfig.CYCLES_RANGE) {
                    config = config.copy(cyclesBeforeLongBreak = it)
                }
                TextButton(onClick = { config = PomodoroConfig() }) { Text("Restablecer 25/5/15") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(config) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ConfigStepper(label: String, value: Int, step: Int, range: IntRange, onChange: (Int) -> Unit) {
    NumberStepper(
        label = label,
        value = "$value",
        onDecrement = { onChange((value - step).coerceIn(range)) },
        onIncrement = { onChange((value + step).coerceIn(range)) },
    )
}
