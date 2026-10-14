package com.fuerzasalvaje.senda.ui.home

import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.fuerzasalvaje.senda.domain.engine.CourseLoad
import com.fuerzasalvaje.senda.ui.components.MarkdownText
import com.fuerzasalvaje.senda.ui.components.StressCheckIn
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.domain.engine.PriorityItem
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.usecase.plan.StudyProgress
import com.fuerzasalvaje.senda.ui.wellbeing.ExamRoutineCard
import com.fuerzasalvaje.senda.ui.components.ConflictBanner
import com.fuerzasalvaje.senda.ui.components.DifficultyStars
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.components.EvaluationDetailSheet
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.Pill
import com.fuerzasalvaje.senda.ui.components.PriorityBadge
import com.fuerzasalvaje.senda.ui.components.ProgressRing
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.components.icon
import com.fuerzasalvaje.senda.ui.components.label
import com.fuerzasalvaje.senda.ui.theme.SendaTheme

@Composable
fun HomeScreen(
    onCreateCourse: () -> Unit,
    onCreateEvaluation: () -> Unit,
    onEditEvaluation: (String) -> Unit,
    onOpenPlan: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pdfDigests by viewModel.pdfDigests.collectAsStateWithLifecycle()
    val zone = viewModel.zone

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    if (state.firstName.isBlank()) "${state.greeting}!" else "${state.greeting}, ${state.firstName}!",
                    style = MaterialTheme.typography.headlineMedium,
                )
                state.today?.let {
                    Text(Formats.fullDate(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.courses.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.School,
                    title = "Empecemos por tus cursos",
                    message = "Registra tu primer curso con su horario. Luego agrega sus exámenes, laboratorios y proyectos.",
                    actionLabel = "Crear mi primer curso",
                    onAction = onCreateCourse,
                )
            }
            return@LazyColumn
        }

        item { StressCheckIn(selected = state.stressLevel, onSelect = viewModel::checkIn) }
        items(state.routines, key = { "routine:${it.key}" }) { ExamRoutineCard(it, zone) }
        item { WeeklyProgressCard(state) }
        if (state.conflicts.isNotEmpty()) item { ConflictBanner(state.conflicts) }
        state.criticalCourse?.let { load -> item { CriticalCourseCard(load, onOpenPlan) } }
        item {
            TutorTipCard(
                state = state,
                zone = zone,
                onGenerate = viewModel::generateTip,
            )
        }
        item {
            OutlinedButton(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.EditCalendar, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Plan de estudio: reparte tus horas libres")
            }
        }

        item { Text("Tus prioridades", style = MaterialTheme.typography.titleLarge) }
        if (state.priorities.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.EventAvailable,
                    title = if (state.hasEvaluations) "¡Todo al día!" else "Sin evaluaciones aún",
                    message = if (state.hasEvaluations) "No tienes evaluaciones pendientes." else "Registra tu primera evaluación con el botón +.",
                    actionLabel = if (state.hasEvaluations) null else "Nueva evaluación",
                    onAction = onCreateEvaluation,
                )
            }
        } else {
            items(state.priorities, key = { it.evaluation.id }) { item ->
                PriorityCard(
                    item,
                    courseName = state.courseOf(item.evaluation)?.name,
                    progress = state.studyProgress[item.evaluation.id],
                    onClick = { viewModel.open(item.evaluation) },
                    onStatus = { viewModel.setStatus(item.evaluation, it) },
                )
            }
        }
    }

    state.selected?.let { evaluation ->
        EvaluationDetailSheet(
            evaluation = evaluation,
            course = state.courseOf(evaluation),
            zone = zone,
            onDismiss = viewModel::close,
            onEdit = { viewModel.close(); onEditEvaluation(evaluation.id) },
            onStatus = { viewModel.setStatus(evaluation, it) },
            onPhaseStatus = { phase, status -> (evaluation as? Project)?.let { viewModel.setPhaseStatus(it, phase.id, status) } },
            conflicts = state.conflicts.filter { conflict -> conflict.entries.any { it.evaluation.id == evaluation.id } },
            pdfStatus = pdfDigests[evaluation.id]?.status,
        )
    }
}

@Composable
private fun WeeklyProgressCard(state: HomeUiState) {
    SendaCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction = state.progress.fraction, caption = "esta semana")
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Avance semanal", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.progress.total == 0) "No tienes evaluaciones esta semana."
                    else "${state.progress.completed} de ${state.progress.total} completadas",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PriorityCard(
    item: PriorityItem,
    courseName: String?,
    progress: StudyProgress?,
    onClick: () -> Unit,
    onStatus: (EvaluationStatus) -> Unit,
) {
    val evaluation = item.evaluation
    SendaCard(onClick = onClick, containerColor = SendaTheme.colors.forType(evaluation.type), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(evaluation.type.label, MaterialTheme.colorScheme.surface, icon = evaluation.type.icon)
            Spacer(Modifier.weight(1f))
            PriorityBadge(item.normalizedScore, item.isOverdue)
        }
        Spacer(Modifier.padding(top = 6.dp))
        Text(
            item.phase?.let { "${evaluation.title} · ${it.name}" } ?: evaluation.title,
            style = MaterialTheme.typography.titleMedium,
        )
        courseName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.timeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (item.isOverdue) SendaTheme.colors.hard else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            DifficultyStars(evaluation.difficulty)
        }
        if (progress != null && progress.totalMinutes > 0) StudyHoursLine(progress)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuickStatus(item.evaluation.status, onStatus)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClick) {
                Text("Ver detalle")
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

/** HU-08: status chip that opens a menu right on the card; for projects it applies to every phase (D-17). */
@Composable
private fun QuickStatus(status: EvaluationStatus, onStatus: (EvaluationStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(status.label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Cambiar estado") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EvaluationStatus.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onStatus(option) })
            }
        }
    }
}

@Composable
private fun CriticalCourseCard(load: CourseLoad, onOpenPlan: () -> Unit) {
    val hard = SendaTheme.colors.hard
    SendaCard(Modifier.fillMaxWidth(), containerColor = hard.copy(alpha = 0.12f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = hard)
            Spacer(Modifier.width(8.dp))
            Text("Curso crítico: ${load.course?.name ?: "un curso"}", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Concentra ${(load.share * 100).roundToInt()} % de tus horas de estudio esta semana " +
                "(${Formats.hours(load.hours)}). Reparte su estudio con tiempo para no descuidarlo.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onOpenPlan, modifier = Modifier.align(Alignment.End)) { Text("Ver plan de estudio") }
    }
}

@Composable
private fun TutorTipCard(state: HomeUiState, zone: java.time.ZoneId, onGenerate: () -> Unit) {
    SendaCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("Consejo de tu tutor IA", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        val tip = state.tip
        when {
            state.tipState.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Pensando en tu semana…", style = MaterialTheme.typography.bodyMedium)
            }
            tip != null -> {
                MarkdownText(tip.content)
                Text(
                    "Generado ${Formats.dateTime(tip.createdAt, zone)}" + (tip.stressLevel?.let { " · estrés $it/5" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            else -> Text(
                "Recibe consejos según tus prioridades y cómo te sientes hoy.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.tipState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        if (state.stressLevel == null) {
            Text(
                "Haz tu check-in de estrés de hoy para activar el tutor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Button(
            onClick = onGenerate,
            enabled = state.canGenerateTip,
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
        ) {
            Text(
                when {
                    state.tipState.error != null -> "Reintentar"
                    state.tip != null -> "Generar otro consejo"
                    else -> "Generar consejo"
                }
            )
        }
    }
}

/** Pending study hours of an evaluation, after accepted blocks and completed Pomodoros. */
@Composable
private fun StudyHoursLine(progress: StudyProgress) {
    val pending = progress.pendingMinutes
    val text = if (pending == 0) "Horas de estudio cubiertas" else "Te faltan ${Formats.hours(pending / 60.0)} de estudio"
    val focus = if (progress.focusMinutes > 0) " · ${progress.focusMinutes} min con Pomodoro" else ""
    Text(
        text + focus,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
