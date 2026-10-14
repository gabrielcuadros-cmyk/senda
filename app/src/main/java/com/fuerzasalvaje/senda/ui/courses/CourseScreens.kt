package com.fuerzasalvaje.senda.ui.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.ui.components.ChipOption
import com.fuerzasalvaje.senda.ui.components.ConfirmDialog
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.Pill
import com.fuerzasalvaje.senda.ui.components.SegmentedSelector
import com.fuerzasalvaje.senda.ui.components.SelectField
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.components.TimeField
import com.fuerzasalvaje.senda.ui.components.label
import com.fuerzasalvaje.senda.ui.components.shortLabel
import com.fuerzasalvaje.senda.ui.theme.CourseColors
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: CourseListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis cursos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Nuevo curso") })
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() -> EmptyState(
                icon = Icons.Rounded.School,
                title = "Aún no tienes cursos",
                message = "Registra tus cursos con su horario de clases para empezar a planificar.",
                actionLabel = "Crear mi primer curso",
                onAction = onAdd,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.course.id }) { item ->
                    SendaCard(onClick = { onOpen(item.course.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Color(item.course.colorArgb), CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.course.name, style = MaterialTheme.typography.titleMedium)
                                val details = buildList {
                                    item.course.teacher?.let(::add)
                                    add("${item.course.schedule.size} clases/semana")
                                    add("${item.evaluationCount} evaluaciones")
                                }
                                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // Below the name, not beside it: beside it squeezed the name on 360 dp.
                                Spacer(Modifier.height(6.dp))
                                Pill("Importancia ${item.course.importance.label.lowercase()}", MaterialTheme.colorScheme.surfaceVariant)
                                if (item.pdfsReady > 0) {
                                    Text(
                                        if (item.pdfsReady == 1) "1 PDF listo para simulacros" else "${item.pdfsReady} PDF listos para simulacros",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseFormScreen(
    onDone: () -> Unit,
    viewModel: CourseFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Editar curso" else "Nuevo curso") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, "Cerrar") } },
                actions = {
                    if (state.isEdit) IconButton(onClick = viewModel::requestDelete) { Icon(Icons.Rounded.Delete, "Eliminar curso") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Nombre del curso") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.teacher,
                onValueChange = viewModel::onTeacher,
                label = { Text("Docente (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Importancia del curso", style = MaterialTheme.typography.titleSmall)
            Text(
                "Sirve para desempatar prioridades. Todas sus evaluaciones la heredan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedSelector(
                options = Importance.entries.map { ChipOption(it, it.label) },
                selected = state.importance,
                onSelect = viewModel::onImportance,
            )

            Text("Color", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CourseColors.forEach { color ->
                    val selected = color == state.colorArgb
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color(color), CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { viewModel.onColor(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Seleccionado", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text("Horario de clases semanal", style = MaterialTheme.typography.titleSmall)
            state.slots.forEach { slot ->
                SendaCard(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectField(
                            label = "Día",
                            options = DayOfWeek.entries.map { ChipOption(it, it.label()) },
                            selected = slot.day,
                            onSelect = { day -> viewModel.updateSlot(slot.key) { it.copy(day = day) } },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeSlot(slot.key) }) { Icon(Icons.Rounded.Delete, "Quitar clase") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField("Inicio", slot.start, { t -> viewModel.updateSlot(slot.key) { it.copy(start = t) } }, Modifier.weight(1f))
                        TimeField(
                            "Fin", slot.end, { t -> viewModel.updateSlot(slot.key) { it.copy(end = t) } }, Modifier.weight(1f),
                            error = if (slot.invalid) "Debe ser después del inicio" else null,
                        )
                    }
                    OutlinedTextField(
                        value = slot.room,
                        onValueChange = { room -> viewModel.updateSlot(slot.key) { it.copy(room = room) } },
                        label = { Text("Aula") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${slot.day.shortLabel()} ${Formats.time(slot.start)}–${Formats.time(slot.end)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.slotsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = viewModel::addSlot) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Agregar clase")
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (state.isEdit) "Guardar cambios" else "Crear curso")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar curso?",
            message = if (state.evaluationCount > 0) {
                "También se eliminarán sus ${state.evaluationCount} evaluaciones. Esta acción no se puede deshacer."
            } else {
                "Esta acción no se puede deshacer."
            },
            confirmLabel = "Eliminar",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}
