package com.fuerzasalvaje.senda.ui.simulacra

import com.fuerzasalvaje.senda.domain.repository.PrefetchStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.CourseMaterial
import com.fuerzasalvaje.senda.ui.components.AI_RESTING_MESSAGE
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.components.SendaFilterChip
import com.fuerzasalvaje.senda.ui.theme.SendaTheme

private val QuestionOptions = listOf(5, 10, 15, 20)
private val MinuteOptions = listOf(10, 20, 30, 45, 60)

/** Simulacros tab (HU-15, HU-16). */
@Composable
fun SimulacraScreen(
    onOpenSimulacrum: (String) -> Unit,
    onCreateCourse: () -> Unit,
    viewModel: SimulacraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.startedId) { state.startedId?.let { viewModel.consumeStarted(); onOpenSimulacrum(it) } }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long); viewModel.consumeMessage() }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.items.isEmpty() -> EmptyState(
                icon = Icons.Rounded.School,
                title = "Aún no tienes cursos",
                message = "Crea un curso y registra el temario de sus exámenes para generar simulacros.",
                actionLabel = "Crear mi primer curso",
                onAction = onCreateCourse,
                modifier = Modifier.padding(top = 48.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.aiResting) {
                    item {
                        SendaCard(containerColor = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(AI_RESTING_MESSAGE, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { SettingsCard(state, viewModel) }
                items(state.inProgress, key = { it.first.id }) { (attempt, course) ->
                    SendaCard(onClick = { onOpenSimulacrum(attempt.id) }, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Continuar simulacro", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${course?.name ?: "Curso"} · ${attempt.answers.count { it.selectedOptionIndex != null }} de ${attempt.questionIds.size} respondidas",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                item { Text("Tus cursos", style = MaterialTheme.typography.titleLarge) }
                items(state.items, key = { it.course.id }) { item ->
                    CourseCard(item, preparing = state.preparingCourseId == item.course.id, busy = state.preparingCourseId != null) {
                        viewModel.start(item.course)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.askTopicsFor?.let { course ->
        TopicsDialog(course, onConfirm = { topics -> viewModel.start(course, topics) }, onDismiss = viewModel::dismissTopics)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(state: SimulacraUiState, viewModel: SimulacraViewModel) {
    SendaCard(Modifier.fillMaxWidth()) {
        Text("Configura tu simulacro", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Preguntas", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionOptions.forEach { SendaFilterChip("$it", state.questionCount == it) { viewModel.setQuestionCount(it) } }
        }
        Text("Minutos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MinuteOptions.forEach { SendaFilterChip("$it", state.minutes == it) { viewModel.setMinutes(it) } }
        }
        Text(
            "Los simulacros salen de tus preguntas guardadas. La IA crea más desde el temario y el PDF solo cuando se están acabando.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Background question generation of the course (WorkManager) or how many questions are ready. */
@Composable
private fun PrefetchLine(item: CourseSimulacraItem) {
    val (text, color) = when (item.prefetch) {
        PrefetchStatus.PREPARING -> "Preparando preguntas con IA…" to MaterialTheme.colorScheme.primary
        PrefetchStatus.RETRYING -> "No se pudo preparar, reintentando" to SendaTheme.colors.busyDay
        PrefetchStatus.FAILED ->
            "No se pudo preparar. Revisa el temario o el PDF del curso." to SendaTheme.colors.hard
        PrefetchStatus.IDLE -> when {
            item.available > 0 -> "${item.available} ${if (item.available == 1) "pregunta nueva lista" else "preguntas nuevas listas"}"
            item.questionCount > 0 -> "Repasarás tus ${item.questionCount} preguntas guardadas, primero las que fallaste"
            else -> "Aún sin preguntas: se generan desde tu temario y PDF"
        } to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        if (item.prefetch == PrefetchStatus.PREPARING) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun CourseCard(item: CourseSimulacraItem, preparing: Boolean, busy: Boolean, onStart: () -> Unit) {
    SendaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Color(item.course.colorArgb), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(item.course.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        val readiness = item.readiness
        Text(
            if (readiness == null) "Nivel de preparación: aún sin simulacros" else "Nivel de preparación: $readiness %",
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                readiness == null -> MaterialTheme.colorScheme.onSurfaceVariant
                readiness >= 70 -> SendaTheme.colors.easy
                readiness >= 50 -> MaterialTheme.colorScheme.onSurface
                else -> SendaTheme.colors.hard
            },
        )
        PrefetchLine(item)
        Text(
            "${item.attempts} ${if (item.attempts == 1) "simulacro hecho" else "simulacros hechos"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onStart, enabled = !busy, modifier = Modifier.align(Alignment.End).padding(top = 6.dp)) {
            if (preparing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Preparando preguntas…")
            } else {
                Text("Nuevo simulacro")
            }
        }
    }
}

@Composable
private fun TopicsDialog(course: Course, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var topics by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué temas entran?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "El temario de ${course.name} es muy corto o no existe, y sus exámenes no tienen PDF. " +
                        "Escribe los temas para que la IA prepare las preguntas, o adjunta el PDF del temario al examen.",
                )
                OutlinedTextField(
                    value = topics,
                    onValueChange = { topics = it },
                    placeholder = { Text("Ej.: leyes de Newton, trabajo y energía") },
                    supportingText = {
                        val missing = CourseMaterial.MIN_SYLLABUS_CHARS - topics.trim().length
                        Text(if (missing > 0) "Escribe al menos $missing caracteres más." else "Listo para generar.")
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(topics) },
                enabled = topics.trim().length >= CourseMaterial.MIN_SYLLABUS_CHARS,
            ) { Text("Generar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
