package com.fuerzasalvaje.senda.ui.components

import com.fuerzasalvaje.senda.domain.model.localPdfPath
import com.fuerzasalvaje.senda.domain.model.pdfMarkdown
import com.fuerzasalvaje.senda.domain.model.pdfSummarized
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.fuerzasalvaje.senda.domain.engine.Validators
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuerzasalvaje.senda.domain.model.Conflict
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import java.time.ZoneId

/** HU-05 quick modal and HU-08 quick status change, shared by the calendar and the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationDetailSheet(
    evaluation: Evaluation,
    course: Course?,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onStatus: (EvaluationStatus) -> Unit,
    onPhaseStatus: (ProjectPhase, PhaseStatus) -> Unit,
    highlightedPhase: ProjectPhase? = null,
    conflicts: List<Conflict> = emptyList(),
    /** "Contenido que leerá la IA": edit it or read the PDF again. */
    aiContent: AiContentUiState = AiContentUiState(),
    onSaveMarkdown: (String) -> Unit = {},
    onReextract: () -> Unit = {},
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(evaluation.type.label, SendaTheme.colors.forType(evaluation.type), icon = evaluation.type.icon)
                Spacer(Modifier.width(8.dp))
                course?.let { Text(it.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(evaluation.title, style = MaterialTheme.typography.headlineSmall)

            when (evaluation) {
                is Exam -> {
                    Detail("Fecha", Formats.dateTime(evaluation.startAt, zone) + (evaluation.endAt?.let { " – ${Formats.time(it, zone)}" } ?: ""))
                    Detail("Tipo", evaluation.examType.label)
                    if (evaluation.room.isNotBlank()) Detail("Aula", evaluation.room)
                    if (evaluation.syllabus.isNotBlank()) Detail("Temario", evaluation.syllabus)
                }
                is Lab -> {
                    Detail("Entrega del informe", Formats.dateTime(evaluation.dueAt, zone))
                    if (evaluation.guideNumber.isNotBlank()) Detail("Guía", evaluation.guideNumber)
                    Detail("Modalidad", evaluation.modality.label)
                    if (evaluation.requiredTools.isNotEmpty()) Detail("Requiere", evaluation.requiredTools.joinToString(", "))
                }
                is Project -> if (evaluation.members.isNotEmpty()) Detail("Integrantes", evaluation.members.joinToString(", "))
            }
            course?.let { Detail("Importancia", "${it.importance.label} (del curso)") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dificultad", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(110.dp))
                DifficultyStars(evaluation.difficulty)
            }
            Detail("Peso", Formats.percent(evaluation.weightPercent))
            Detail("Horas de estudio", Formats.hours(evaluation.studyHours))

            evaluation.localPdfPath?.let { path ->
                OutlinedButton(onClick = { openLocalPdf(context, path) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (evaluation is Lab) "Ver guía en PDF" else "Ver temario en PDF")
                }
            }
            if (evaluation.localPdfPath != null || evaluation.pdfMarkdown.isNotBlank()) {
                // Restarts from the stored text whenever it changes (saved here, re-extracted or synced).
                var draft by rememberSaveable(evaluation.id, evaluation.pdfMarkdown) { mutableStateOf(evaluation.pdfMarkdown) }
                AiContentSection(
                    markdown = draft,
                    onMarkdownChange = { draft = it },
                    summarized = evaluation.pdfSummarized,
                    noText = evaluation.id in aiContent.noTextIds,
                    extracting = aiContent.extractingId == evaluation.id,
                    canReextract = evaluation.localPdfPath != null,
                    onReextract = onReextract,
                ) {
                    aiContent.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (draft.trim() != evaluation.pdfMarkdown) {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { draft = evaluation.pdfMarkdown }) { Text("Descartar") }
                            Button(onClick = { onSaveMarkdown(draft) }) { Text("Guardar contenido") }
                        }
                    }
                }
            }
            if (evaluation is Project && evaluation.rubric.isNotEmpty()) {
                Text("Rúbrica", style = MaterialTheme.typography.titleSmall)
                evaluation.rubric.forEach { item ->
                    Row {
                        Text(item.criterion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${Validators.formatNumber(item.maxScore)} pts", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "Total: ${Validators.formatNumber(evaluation.rubric.sumOf { it.maxScore })} pts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            conflicts.forEach { Text("⚠ ${conflictMessage(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            if (evaluation is Project) {
                Text("Fases", style = MaterialTheme.typography.titleSmall)
                evaluation.phases.sortedBy { it.dueAt }.forEach { phase ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${phase.name} · ${Formats.dateTime(phase.dueAt, zone)}" + if (phase.id == highlightedPhase?.id) "  ←" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        SegmentedSelector(
                            options = PhaseStatus.entries.map { ChipOption(it, it.label) },
                            selected = phase.status,
                            onSelect = { onPhaseStatus(phase, it) },
                        )
                    }
                }
                Text("Estado del proyecto: ${evaluation.status.label}", style = MaterialTheme.typography.labelLarge)
            } else {
                Text("Estado", style = MaterialTheme.typography.titleSmall)
                SegmentedSelector(
                    options = EvaluationStatus.entries.map { ChipOption(it, it.label) },
                    selected = evaluation.status,
                    onSelect = onStatus,
                )
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Editar")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
