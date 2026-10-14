package com.fuerzasalvaje.senda.ui.evaluation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.domain.engine.ValidationError
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.LabModality
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.ui.components.ChipOption
import com.fuerzasalvaje.senda.ui.components.ConfirmDialog
import com.fuerzasalvaje.senda.ui.components.DateField
import com.fuerzasalvaje.senda.ui.components.DifficultyStars
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.NumberStepper
import com.fuerzasalvaje.senda.ui.components.SegmentedSelector
import com.fuerzasalvaje.senda.ui.components.SelectField
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.components.TimeField
import com.fuerzasalvaje.senda.ui.components.label
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import java.io.File
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationFormScreen(
    onDone: () -> Unit,
    onCreateCourse: () -> Unit,
    viewModel: EvaluationFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = viewModel.zoneId
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Editar evaluación" else "Nueva evaluación") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, "Cerrar") } },
                actions = {
                    if (state.isEdit) IconButton(onClick = viewModel::requestDelete) { Icon(Icons.Rounded.Delete, "Eliminar evaluación") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.courses.isEmpty() -> EmptyState(
                icon = Icons.Rounded.WarningAmber,
                title = "Primero crea un curso",
                message = "Cada evaluación pertenece a un curso. Registra tu primer curso y vuelve aquí.",
                actionLabel = "Crear curso",
                onAction = onCreateCourse,
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                Modifier
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SegmentedSelector(
                    options = EvaluationType.entries.map { ChipOption(it, it.label) },
                    selected = state.type,
                    onSelect = viewModel::onType,
                    enabled = !state.isEdit,
                )
                SelectField(
                    label = "Curso",
                    options = state.courses.map { ChipOption(it.id, it.name) },
                    selected = state.courseId,
                    onSelect = viewModel::onCourse,
                    error = state.visibleError(ValidationError.COURSE_REQUIRED, zone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitle,
                    label = {
                        Text(
                            when (state.type) {
                                EvaluationType.EXAM -> "Nombre del examen"
                                EvaluationType.LAB -> "Nombre del laboratorio"
                                EvaluationType.PROJECT -> "Nombre del proyecto"
                            }
                        )
                    },
                    isError = state.visibleError(ValidationError.TITLE_REQUIRED, zone) != null,
                    supportingText = state.visibleError(ValidationError.TITLE_REQUIRED, zone)?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                when (state.type) {
                    EvaluationType.EXAM -> ExamFields(state, viewModel)
                    EvaluationType.LAB -> LabFields(state, viewModel)
                    EvaluationType.PROJECT -> ProjectFields(state, viewModel)
                }

                if (state.type != EvaluationType.PROJECT) {
                    ScheduleFields(state, viewModel)
                    PdfField(state, viewModel)
                }

                DifficultyAndWeight(state, viewModel)

                if (state.isEdit && state.type != EvaluationType.PROJECT) {
                    Text("Estado", style = MaterialTheme.typography.titleSmall)
                    SegmentedSelector(
                        options = EvaluationStatus.entries.map { ChipOption(it, it.label) },
                        selected = state.status,
                        onSelect = viewModel::onStatus,
                    )
                }

                if (state.showErrors && state.errors(zone).isNotEmpty()) {
                    Text("Revisa los campos marcados.", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (state.isEdit) "Guardar cambios" else "Guardar evaluación")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    state.reschedule?.let { request ->
        ConfirmDialog(
            title = "¿Reprogramar evaluación?",
            message = "Cambiará de ${request.from?.let { Formats.dateTime(it, zone) } ?: "sin fecha"} " +
                "a ${request.to?.let { Formats.dateTime(it, zone) } ?: "sin fecha"}. Se reorganizará en tu calendario.",
            confirmLabel = "Reprogramar",
            onConfirm = viewModel::confirmReschedule,
            onDismiss = viewModel::dismissReschedule,
        )
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar evaluación?",
            message = "Se eliminará \"${state.title}\" y su PDF guardado. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun ExamFields(state: EvaluationFormUiState, viewModel: EvaluationFormViewModel) {
    Text("Tipo de examen", style = MaterialTheme.typography.titleSmall)
    SegmentedSelector(
        options = ExamType.entries.map { ChipOption(it, it.label) },
        selected = state.examType,
        onSelect = viewModel::onExamType,
    )
    OutlinedTextField(
        value = state.room,
        onValueChange = viewModel::onRoom,
        label = { Text("Aula") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.syllabus,
        onValueChange = viewModel::onSyllabus,
        label = { Text("Temario a evaluar") },
        placeholder = { Text("Ej.: Unidad 2: cinemática, MRU y MRUV") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabFields(state: EvaluationFormUiState, viewModel: EvaluationFormViewModel) {
    OutlinedTextField(
        value = state.guideNumber,
        onValueChange = viewModel::onGuideNumber,
        label = { Text("N.º de guía o práctica") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    TextListField(
        title = "Software y equipos requeridos",
        placeholder = "Ej.: Arduino IDE",
        items = state.tools,
        onAdd = viewModel::addTool,
        onRemove = viewModel::removeTool,
    )
    Text("Entrega", style = MaterialTheme.typography.titleSmall)
    SegmentedSelector(
        options = LabModality.entries.map { ChipOption(it, it.label) },
        selected = state.modality,
        onSelect = viewModel::onModality,
    )
}

@Composable
private fun ScheduleFields(state: EvaluationFormUiState, viewModel: EvaluationFormViewModel) {
    val zone = viewModel.zoneId
    val isLab = state.type == EvaluationType.LAB
    val dateError = state.visibleError(ValidationError.DATE_REQUIRED, zone)
    val endError = state.visibleError(ValidationError.END_BEFORE_START, zone)
    DateField(
        label = if (isLab) "Fecha límite del informe" else "Fecha del examen",
        value = state.date,
        onChange = viewModel::onDate,
        error = dateError,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeField(if (isLab) "Hora" else "Hora de inicio", state.startTime, viewModel::onStartTime, Modifier.weight(1f))
        if (state.endTime != null) {
            TimeField("Hora de fin", state.endTime, { viewModel.onEndTime(it) }, Modifier.weight(1f), error = endError)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hora de fin", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Sin hora de fin se asume ${if (isLab) "1 hora" else "2 horas"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.endTime != null,
            onCheckedChange = { on ->
                viewModel.onEndTime(if (on) state.startTime.plusHours(if (isLab) 1 else 2).coerceAtLeastSameDay(state.startTime) else null)
            },
        )
    }
}

/** Keeps the suggested end on the same day (plusHours wraps around midnight). */
private fun LocalTime.coerceAtLeastSameDay(start: LocalTime): LocalTime = if (isAfter(start)) this else LocalTime.of(23, 59)

@Composable
private fun PdfField(state: EvaluationFormUiState, viewModel: EvaluationFormViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onPdfPicked(it.toString()) }
    }
    val label = if (state.type == EvaluationType.LAB) "Guía en PDF" else "Temario en PDF (opcional)"
    SendaCard(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        state.importingPdf -> "Copiando…"
                        state.pdfPath != null -> "Guardado en el teléfono: ${File(state.pdfPath).name.take(12)}…"
                        else -> "Se guarda solo en este teléfono."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.pdfPath != null) {
                IconButton(onClick = viewModel::removePdf) { Icon(Icons.Rounded.Delete, "Quitar PDF") }
            }
            TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !state.importingPdf) {
                Text(if (state.pdfPath != null) "Cambiar" else "Adjuntar")
            }
        }
    }
}

@Composable
private fun ProjectFields(state: EvaluationFormUiState, viewModel: EvaluationFormViewModel) {
    val zone = viewModel.zoneId
    TextListField(
        title = "Grupo o integrantes",
        placeholder = "Nombre del integrante",
        items = state.members,
        onAdd = viewModel::addMember,
        onRemove = viewModel::removeMember,
    )

    Text("Fases y entregas", style = MaterialTheme.typography.titleSmall)
    Text(
        "Un trabajo simple es un proyecto con una sola fase.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.phases.forEach { phase ->
        SendaCard(containerColor = SendaTheme.colors.project, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = phase.name,
                    onValueChange = { name -> viewModel.updatePhase(phase.id) { it.copy(name = name) } },
                    label = { Text("Fase") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (state.phases.size > 1) {
                    IconButton(onClick = { viewModel.removePhase(phase.id) }) { Icon(Icons.Rounded.Delete, "Quitar fase") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    "Fecha", phase.date, { d -> viewModel.updatePhase(phase.id) { it.copy(date = d) } }, Modifier.weight(1f), compact = true,
                    error = if (state.showErrors && phase.date == null) "Requerida" else null,
                )
                TimeField("Hora", phase.time, { t -> viewModel.updatePhase(phase.id) { it.copy(time = t) } }, Modifier.weight(1f))
            }
            SegmentedSelector(
                options = PhaseStatus.entries.map { ChipOption(it, it.label) },
                selected = phase.status,
                onSelect = { status -> viewModel.updatePhase(phase.id) { it.copy(status = status) } },
            )
        }
    }
    state.visibleError(ValidationError.PHASE_INVALID, zone)?.let { ErrorLine(it) }
    state.visibleError(ValidationError.PHASES_REQUIRED, zone)?.let { ErrorLine(it) }
    OutlinedButton(onClick = viewModel::addPhase) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Agregar fase") }

    Text("Rúbrica de evaluación", style = MaterialTheme.typography.titleSmall)
    state.rubric.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = item.criterion,
                onValueChange = { v -> viewModel.updateRubricItem(item.key) { it.copy(criterion = v) } },
                label = { Text("Criterio") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = item.score,
                onValueChange = { v -> viewModel.updateRubricItem(item.key) { it.copy(score = v) } },
                label = { Text("Puntaje") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(96.dp),
            )
            IconButton(onClick = { viewModel.removeRubricItem(item.key) }) { Icon(Icons.Rounded.Delete, "Quitar criterio") }
        }
    }
    state.visibleError(ValidationError.RUBRIC_INVALID, zone)?.let { ErrorLine(it) }
    OutlinedButton(onClick = viewModel::addRubricItem) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Agregar criterio") }
}

@Composable
private fun DifficultyAndWeight(state: EvaluationFormUiState, viewModel: EvaluationFormViewModel) {
    val zone = viewModel.zoneId
    Text("Dificultad", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        DifficultyStars(value = state.difficulty, onChange = viewModel::onDifficulty, starSize = 28.dp)
        Spacer(Modifier.width(8.dp))
        val level = SendaRules.difficultyLevel(state.difficulty)
        Surface(color = SendaTheme.colors.forDifficulty(level).copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
            Text(
                when (level) {
                    com.fuerzasalvaje.senda.domain.model.DifficultyLevel.EASY -> "Fácil"
                    com.fuerzasalvaje.senda.domain.model.DifficultyLevel.MEDIUM -> "Media"
                    com.fuerzasalvaje.senda.domain.model.DifficultyLevel.HARD -> "Difícil"
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    val weightError = state.visibleError(ValidationError.WEIGHT_INVALID, zone)
    OutlinedTextField(
        value = state.weight,
        onValueChange = viewModel::onWeight,
        label = { Text("Peso en la nota del curso (%)") },
        isError = weightError != null,
        supportingText = { Text(weightError ?: "Mayor que 0 y hasta 100.") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    state.courseWeightTotal?.let { total ->
        Surface(color = SendaTheme.colors.busyDay.copy(alpha = 0.18f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = SendaTheme.colors.busyDay)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Las evaluaciones de este curso suman ${Formats.percent(total)}. Puedes guardar igual, pero revisa los pesos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    NumberStepper(
        label = "Horas de estudio",
        value = Formats.hours(state.studyHours),
        onDecrement = viewModel::decrementHours,
        onIncrement = viewModel::incrementHours,
        supporting = "Sugerido para ${state.difficulty}★: ${Formats.hours(state.suggestedHours)}",
        error = state.visibleError(ValidationError.HOURS_INVALID, zone),
    )
    if (state.studyHoursEdited && state.studyHours != state.suggestedHours) {
        TextButton(onClick = viewModel::useSuggestedHours) { Text("Usar sugerido (${Formats.hours(state.suggestedHours)})") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextListField(
    title: String,
    placeholder: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Text(title, style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Add, "Agregar") }
    }
    if (items.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, item ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(index) },
                    label = { Text(item) },
                    trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Quitar $item", modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
