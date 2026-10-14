package com.fuerzasalvaje.senda.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuerzasalvaje.senda.domain.model.PdfDigestStatus
import com.fuerzasalvaje.senda.ui.theme.SendaTheme

/** Spanish label of where the PDF summary stands. */
val PdfDigestStatus.label: String
    get() = when (this) {
        PdfDigestStatus.READING -> "Leyendo tu PDF…"
        PdfDigestStatus.READY -> "PDF listo para simulacros"
        PdfDigestStatus.RETRYING -> "No se pudo leer el PDF, reintentando"
        PdfDigestStatus.FAILED -> "No se pudo leer el PDF. Vuelve a adjuntarlo."
    }

/** One line with an icon (or a small spinner while reading) and the status label. */
@Composable
fun PdfDigestStatusLine(status: PdfDigestStatus, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        when (status) {
            PdfDigestStatus.READING -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            PdfDigestStatus.READY -> Icon(Icons.Rounded.CheckCircle, null, tint = SendaTheme.colors.easy, modifier = Modifier.size(16.dp))
            PdfDigestStatus.RETRYING -> Icon(Icons.Rounded.Sync, null, tint = SendaTheme.colors.medium, modifier = Modifier.size(16.dp))
            PdfDigestStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(status.label, style = MaterialTheme.typography.bodySmall)
    }
}
