package com.fuerzasalvaje.senda.ui.simulacra

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.ui.components.ConfirmDialog
import com.fuerzasalvaje.senda.ui.theme.CardShape
import com.fuerzasalvaje.senda.ui.theme.SendaTheme

/** HU-15: the running mock exam. */
@Composable
fun SimulacrumScreen(
    onFinished: (String) -> Unit,
    onExit: () -> Unit,
    viewModel: SimulacrumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) state.simulacrum?.let { onFinished(it.id) } }
    BackHandler(enabled = !state.finished) { viewModel.requestExit() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (state.loading || state.simulacrum == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                state.error?.let { Text(it) } ?: CircularProgressIndicator()
            }
            return@Column
        }
        TimerHeader(state, onClose = viewModel::requestExit)
        val question = state.current
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (question != null) {
                Text(
                    "Pregunta ${state.currentIndex + 1} de ${state.questions.size}" + if (question.isCase) " · Caso" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                question.scenario?.let {
                    Surface(color = SendaTheme.colors.exam, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                    }
                }
                Text(question.statement, style = MaterialTheme.typography.titleLarge)
                question.options.forEachIndexed { index, option ->
                    OptionCard(
                        letter = ('A' + index).toString(),
                        text = option,
                        selected = state.selections[question.id] == index,
                        onClick = { viewModel.select(index) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = viewModel::previous, enabled = state.currentIndex > 0, modifier = Modifier.weight(1f)) {
                Text("Anterior")
            }
            if (state.currentIndex < state.questions.lastIndex) {
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) { Text("Siguiente") }
            } else {
                Button(onClick = viewModel::requestFinish, modifier = Modifier.weight(1f)) { Text("Finalizar") }
            }
        }
    }

    if (state.confirmFinish) {
        val pending = state.questions.size - state.answeredCount
        ConfirmDialog(
            title = "¿Finalizar simulacro?",
            message = "Aún te queda tiempo" + if (pending > 0) " y tienes $pending sin responder." else ".",
            confirmLabel = "Finalizar",
            onConfirm = viewModel::confirmFinish,
            onDismiss = viewModel::dismissFinish,
        )
    }
    if (state.confirmExit) {
        ConfirmDialog(
            title = "¿Salir del simulacro?",
            message = "Tus respuestas están guardadas, pero el tiempo sigue corriendo. Puedes continuar desde la pestaña Simulacros.",
            confirmLabel = "Salir",
            onConfirm = { viewModel.dismissExit(); onExit() },
            onDismiss = viewModel::dismissExit,
        )
    }
}

@Composable
private fun TimerHeader(state: SimulacrumUiState, onClose: () -> Unit) {
    val urgent = state.isLastMinute
    val color = if (urgent) SendaTheme.colors.hard else MaterialTheme.colorScheme.onBackground
    val minutes = state.remainingSeconds / 60
    val seconds = state.remainingSeconds % 60
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Salir") }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = color)
            Spacer(Modifier.width(6.dp))
            Text(
                "%02d:%02d".format(minutes, seconds),
                color = color,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { contentDescription = "Quedan $minutes minutos y $seconds segundos" },
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            "${state.answeredCount} de ${state.questions.size} respondidas",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun OptionCard(letter: String, text: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (selected) primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(letter, style = MaterialTheme.typography.titleSmall) }
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
