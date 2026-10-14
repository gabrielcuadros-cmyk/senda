package com.fuerzasalvaje.senda.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.ui.components.CourseHoursChart
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.components.TimeField
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanScreen(onBack: () -> Unit, viewModel: StudyPlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.proposal.message) {
        state.proposal.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan de estudio") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HoursSection(state)
            AvailabilitySection(state, viewModel)
            ProposalSection(state, viewModel.zone, viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HoursSection(state: StudyPlanUiState) {
    SendaCard(Modifier.fillMaxWidth()) {
        Text("Horas de estudio por curso", style = MaterialTheme.typography.titleMedium)
        Text(
            "Evaluaciones pendientes de esta semana · total ${Formats.hours(state.report.totalHours)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (state.report.loads.isEmpty()) {
            Text("No tienes horas de estudio estimadas para esta semana.", style = MaterialTheme.typography.bodyMedium)
        } else {
            CourseHoursChart(state.report.loads)
        }
        state.report.critical.forEach { load ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = SendaTheme.colors.hard)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sobrecarga: ${load.course?.name ?: "un curso"} requiere ${(load.share * 100).roundToInt()} % " +
                        "de tu tiempo de estudio (más del 40 %).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AvailabilitySection(state: StudyPlanUiState, viewModel: StudyPlanViewModel) {
    SendaCard(Modifier.fillMaxWidth()) {
        Text("Tu disponibilidad diaria", style = MaterialTheme.typography.titleMedium)
        Text(
            "Los bloques se proponen solo en este horario, fuera de tus clases y evaluaciones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("Desde", state.availability.start, viewModel::onStart, Modifier.weight(1f))
            TimeField("Hasta", state.availability.end, viewModel::onEnd, Modifier.weight(1f))
        }
        state.proposal.availabilityError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Tu sueño de 23:00 a 06:00 está protegido: nunca agendamos estudio ahí.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProposalSection(state: StudyPlanUiState, zone: ZoneId, viewModel: StudyPlanViewModel) {
    val proposal = state.proposal
    SendaCard(Modifier.fillMaxWidth(), containerColor = SendaTheme.colors.study) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Bloques sugeridos", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Bloques de 50 min con 10 min de pausa, de mayor a menor prioridad, máximo 6 h por día.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        val blocks = proposal.blocks
        when {
            proposal.generating -> CircularProgressIndicator(Modifier.size(24.dp))
            blocks == null -> Button(onClick = viewModel::suggest, modifier = Modifier.fillMaxWidth()) { Text("Sugerir bloques") }
            blocks.isEmpty() -> EmptyState(
                icon = Icons.Rounded.EventAvailable,
                title = "Nada que agendar",
                message = "No hay horas de estudio pendientes o no queda tiempo libre antes de tus evaluaciones.",
            )
            else -> {
                blocks.groupBy { it.block.startAt.atZone(zone).toLocalDate() }.forEach { (day, dayBlocks) ->
                    Text(Formats.dayMonth(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    dayBlocks.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                "${Formats.time(item.block.startAt, zone)}–${Formats.time(item.block.endAt, zone)}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(96.dp),
                            )
                            Text(
                                item.title + if (item.courseName.isNotBlank()) " · ${item.courseName}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Text(
                    "${blocks.size} bloques · ${Formats.hours(blocks.size * 50 / 60.0)} de estudio",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (proposal.shortfalls.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            proposal.shortfalls.forEach {
                Text(
                    "No alcanzó el tiempo para ${Formats.hours(it.minutes / 60.0)} de \"${it.title}\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (!blocks.isNullOrEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::discard, modifier = Modifier.weight(1f)) { Text("Descartar") }
                Button(onClick = viewModel::accept, enabled = !proposal.accepting, modifier = Modifier.weight(1f)) {
                    Text("Aceptar y agendar")
                }
            }
        } else if (blocks != null) {
            OutlinedButton(onClick = viewModel::suggest, modifier = Modifier.fillMaxWidth()) { Text("Volver a calcular") }
        }
    }
}
