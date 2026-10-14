package com.fuerzasalvaje.senda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuerzasalvaje.senda.domain.engine.PdfMarkdown
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.usecase.pdf.EditPdfMarkdownUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.PdfExtraction
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

const val NO_TEXT_PDF_MESSAGE = "Este PDF no tiene texto legible. Escribe los temas principales en el temario"
const val SUMMARIZED_PDF_MESSAGE = "Se usará una versión resumida del PDF"

/** "12.000" */
fun formatChars(count: Int): String =
    DecimalFormat("#,##0", DecimalFormatSymbols().apply { groupingSeparator = '.' }).format(count)

/**
 * "Contenido que leerá la IA": the Markdown made on the device from the PDF, editable, with its character count and
 * "Volver a extraer del PDF". The AI only ever receives this text, never the PDF.
 */
@Composable
fun AiContentSection(
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    summarized: Boolean,
    noText: Boolean,
    extracting: Boolean,
    canReextract: Boolean,
    onReextract: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    SendaCard(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text("Contenido que leerá la IA", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Texto sacado de tu PDF en este teléfono. Corrígelo si algo salió mal: la IA lee solo este texto, no el PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (noText) {
                Text(NO_TEXT_PDF_MESSAGE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = markdown,
                onValueChange = onMarkdownChange,
                enabled = !extracting,
                placeholder = { Text(if (extracting) "Leyendo tu PDF…" else "## Diapositiva 1\n- …") },
                minLines = 5,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            val over = markdown.length > PdfMarkdown.MAX_CHARS
            Text(
                "${formatChars(markdown.length)} / ${formatChars(PdfMarkdown.MAX_CHARS)} caracteres",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) SendaTheme.colors.medium else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summarized || over) {
                Text(SUMMARIZED_PDF_MESSAGE, style = MaterialTheme.typography.bodySmall, color = SendaTheme.colors.medium)
            }
            if (canReextract) {
                OutlinedButton(onClick = onReextract, enabled = !extracting, modifier = Modifier.fillMaxWidth()) {
                    if (extracting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Volver a extraer del PDF")
                }
            }
            footer()
        }
    }
}

/** State of the AI content section of the detail sheet, shared by the calendar and the home screen. */
data class AiContentUiState(
    val extractingId: String? = null,
    val noTextIds: Set<String> = emptySet(),
    /** Short result line under the section ("Contenido guardado", errors). */
    val notice: String? = null,
)

/** Saves edits and re-reads PDFs for the detail sheet, outside the evaluation form. */
class AiContentController(private val edit: EditPdfMarkdownUseCase, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(AiContentUiState())
    val state: StateFlow<AiContentUiState> = _state.asStateFlow()

    fun save(evaluation: Evaluation, markdown: String) {
        scope.launch {
            val ok = edit.update(evaluation, markdown).isSuccess
            _state.update { it.copy(notice = if (ok) "Contenido guardado." else "No se pudo guardar. Inténtalo de nuevo.") }
        }
    }

    fun reextract(evaluation: Evaluation) {
        if (_state.value.extractingId != null) return
        _state.update { it.copy(extractingId = evaluation.id, notice = null) }
        scope.launch {
            val result = edit.reextract(evaluation)
            _state.update {
                it.copy(
                    extractingId = null,
                    noTextIds = if (result is PdfExtraction.NoText) it.noTextIds + evaluation.id else it.noTextIds - evaluation.id,
                    notice = when (result) {
                        is PdfExtraction.Ready -> "Contenido extraído de nuevo."
                        is PdfExtraction.NoText -> null
                        is PdfExtraction.Failed -> "No se pudo leer el PDF en este teléfono."
                    },
                )
            }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }
}
