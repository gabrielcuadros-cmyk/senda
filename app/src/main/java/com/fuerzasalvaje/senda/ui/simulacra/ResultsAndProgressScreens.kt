package com.fuerzasalvaje.senda.ui.simulacra

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.domain.engine.SimulacrumScoring
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.MarkdownText
import com.fuerzasalvaje.senda.ui.components.ProgressRing
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import java.util.Locale

private const val PASSING_GRADE = 10.5

private fun grade(value: Double) = String.format(Locale.forLanguageTag("es-PE"), "%.1f", value)

/** HU-17: results of one attempt with the AI tutor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulacrumResultScreen(onBack: () -> Unit, viewModel: SimulacrumResultViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = SendaTheme.colors
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultados") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SendaCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(state.courseName, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${grade(state.grade)} / 20",
                        style = MaterialTheme.typography.displaySmall,
                        color = if (state.grade >= PASSING_GRADE) colors.easy else colors.hard,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Summary(Icons.Rounded.CheckCircle, colors.easy, "${state.correct} correctas")
                        Summary(Icons.Rounded.Cancel, colors.hard, "${state.incorrect} incorrectas")
                        Summary(Icons.AutoMirrored.Rounded.HelpOutline, MaterialTheme.colorScheme.onSurfaceVariant, "${state.unanswered} sin responder")
                    }
                }
            }
            items(state.items, key = { it.question.id }) { item -> ResultCard(item, onExplain = { viewModel.explain(item) }) }
        }
    }

    state.explaining?.let { item ->
        val explanation = state.explanations[item.question.id]
        ModalBottomSheet(onDismissRequest = viewModel::closeExplanation) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Tu tutor IA explica", style = MaterialTheme.typography.titleMedium)
                }
                Text(item.question.statement, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    explanation == null || explanation.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Preparando la explicación…")
                    }
                    explanation.text != null -> MarkdownText(explanation.text)
                    else -> {
                        Text(explanation.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.explain(item) }) { Text("Reintentar") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Summary(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ResultCard(item: ResultItem, onExplain: () -> Unit) {
    val colors = SendaTheme.colors
    val (tint, label) = when (item.outcome) {
        AnswerOutcome.CORRECT -> colors.easy to "Correcta"
        AnswerOutcome.INCORRECT -> colors.hard to "Incorrecta"
        AnswerOutcome.UNANSWERED -> MaterialTheme.colorScheme.onSurfaceVariant to "Sin responder"
    }
    SendaCard(Modifier.fillMaxWidth(), containerColor = tint.copy(alpha = 0.10f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(tint, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("Pregunta ${item.number} · $label", style = MaterialTheme.typography.labelLarge, color = tint)
        }
        item.question.scenario?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        Text(item.question.statement, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        item.selected?.let { Text("Tu respuesta: ${item.question.options.getOrNull(it).orEmpty()}", style = MaterialTheme.typography.bodySmall) }
        if (item.outcome != AnswerOutcome.CORRECT) {
            Text(
                "Respuesta correcta: ${item.question.options[item.question.correctOptionIndex]}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.easy,
            )
            if (item.question.explanation.isNotBlank()) {
                Text(item.question.explanation, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedButton(onClick = onExplain, modifier = Modifier.align(Alignment.End).padding(top = 6.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Explícame con IA")
            }
        }
    }
}

/** HU-16: Progreso tab. */
@Composable
fun ProgressScreen(onOpenResult: (String) -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.history.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Insights,
            title = "Aún no hay progreso",
            message = "Haz tu primer simulacro para ver la evolución de tus notas y tu nivel de preparación.",
            modifier = Modifier.padding(top = 48.dp),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SendaCard(Modifier.fillMaxWidth()) {
                Text("Evolución de tus notas", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                GradesLineChart(state.courses)
            }
        }
        item { Text("Nivel de preparación", style = MaterialTheme.typography.titleLarge) }
        items(state.courses, key = { it.course.id }) { progress ->
            SendaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        fraction = (progress.readiness ?: 0) / 100f,
                        size = 72.dp,
                        strokeWidth = 8.dp,
                        color = Color(progress.course.colorArgb).takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(progress.course.name, style = MaterialTheme.typography.titleMedium)
                        Text("${progress.readiness ?: 0} % preparado para el examen", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Última nota ${grade(progress.grades.last())} · ${progress.grades.size} simulacros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Text("Historial", style = MaterialTheme.typography.titleLarge) }
        items(state.history, key = { it.first.id }) { (attempt, course) ->
            SendaCard(onClick = { onOpenResult(attempt.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(course?.name ?: "Curso", style = MaterialTheme.typography.titleSmall)
                        Text(
                            Formats.dateTime(attempt.finishedAt ?: attempt.startedAt, viewModel.zone) +
                                " · ${attempt.correctCount}✓ ${attempt.incorrectCount}✗ ${attempt.unansweredCount} sin responder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val value = SimulacrumScoring.roundedGrade(attempt)
                    Text(
                        grade(value),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (value >= PASSING_GRADE) SendaTheme.colors.easy else SendaTheme.colors.hard,
                    )
                }
            }
        }
    }
}

/** Grades 0–20 per course over successive attempts, drawn with Canvas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradesLineChart(courses: List<CourseProgress>) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val passing = SendaTheme.colors.medium
    val maxPoints = courses.maxOfOrNull { it.grades.size } ?: 0
    val lineColors = courses.associate { it.course.id to Color(it.course.colorArgb).darken() }
    Row {
        Column(Modifier.height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("20", "10", "0").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = label) }
        }
        Spacer(Modifier.width(6.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(180.dp)
                .semantics { contentDescription = "Gráfico de evolución de notas de ${courses.size} cursos" },
        ) {
            val max = SendaRules.MAX_GRADE.toFloat()
            fun y(value: Double) = size.height * (1f - value.toFloat() / max)
            fun x(index: Int) = if (maxPoints <= 1) size.width / 2 else size.width * index / (maxPoints - 1)
            listOf(0.0, 10.0, 20.0).forEach { drawLine(grid, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1f) }
            drawLine(passing, Offset(0f, y(PASSING_GRADE)), Offset(size.width, y(PASSING_GRADE)), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            courses.forEach { progress ->
                val color = lineColors.getValue(progress.course.id)
                val path = Path()
                progress.grades.forEachIndexed { i, g ->
                    if (i == 0) path.moveTo(x(i), y(g)) else path.lineTo(x(i), y(g))
                }
                drawPath(path, color, style = Stroke(width = 4f))
                progress.grades.forEachIndexed { i, g -> drawCircle(color, radius = 7f, center = Offset(x(i), y(g))) }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        courses.forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(lineColors.getValue(it.course.id), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(it.course.name, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("- - aprobado (10,5)", style = MaterialTheme.typography.labelSmall, color = passing)
    }
}

/** Course swatches are pastel; lines need more contrast. */
private fun Color.darken(factor: Float = 0.55f) = Color(red * factor, green * factor, blue * factor, 1f)
