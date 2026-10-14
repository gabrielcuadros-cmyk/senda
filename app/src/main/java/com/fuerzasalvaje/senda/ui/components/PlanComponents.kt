package com.fuerzasalvaje.senda.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuerzasalvaje.senda.domain.engine.CourseLoad
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import kotlin.math.roundToInt

private data class Mood(val level: Int, val icon: ImageVector, val label: String)

private val Moods = listOf(
    Mood(1, Icons.Rounded.SentimentVerySatisfied, "Tranquilo"),
    Mood(2, Icons.Rounded.SentimentSatisfied, "Bien"),
    Mood(3, Icons.Rounded.SentimentNeutral, "Normal"),
    Mood(4, Icons.Rounded.SentimentDissatisfied, "Estresado"),
    Mood(5, Icons.Rounded.SentimentVeryDissatisfied, "Muy estresado"),
)

/** D-12: daily stress check-in, 1 (calm) to 5 (very stressed). */
@Composable
fun StressCheckIn(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = SendaTheme.colors
    SendaCard(modifier.fillMaxWidth()) {
        Text("¿Cómo te sientes hoy?", style = MaterialTheme.typography.titleMedium)
        Text(
            if (selected == null) "Tu nivel de estrés ayuda al tutor IA a darte mejores consejos."
            else "Registrado: ${Moods[selected - 1].label.lowercase()} ($selected/5). Puedes cambiarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Moods.forEach { mood ->
                val tint = when {
                    mood.level <= 2 -> colors.easy
                    mood.level == 3 -> colors.medium
                    else -> colors.hard
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    FilledIconToggleButton(
                        checked = selected == mood.level,
                        onCheckedChange = { onSelect(mood.level) },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            checkedContainerColor = tint.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Estrés ${mood.level}: ${mood.label}" },
                    ) {
                        Icon(mood.icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
                    }
                    Text(
                        "${mood.level}",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * HU-13: horizontal bars drawn with Canvas, one per course, scaled to the share of the week's hours.
 * The dashed line marks the 40 % threshold; critical courses are drawn in red.
 */
@Composable
fun CourseHoursChart(loads: List<CourseLoad>, modifier: Modifier = Modifier) {
    val colors = SendaTheme.colors
    val track = MaterialTheme.colorScheme.surfaceVariant
    val threshold = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        loads.forEach { load ->
            val barColor = if (load.isCritical) colors.hard else Color(load.course?.colorArgb ?: 0xFFB5D8FF).copy(alpha = 1f)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (load.isCritical) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = "Curso crítico", tint = colors.hard, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(load.course?.name ?: "Curso", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${Formats.hours(load.hours)} · ${(load.share * 100).roundToInt()} %",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .padding(top = 4.dp)
                        .semantics { contentDescription = "${load.course?.name}: ${(load.share * 100).roundToInt()} por ciento" },
                ) {
                    val radius = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(track, cornerRadius = radius)
                    drawRoundRect(barColor, size = Size(size.width * load.share.toFloat(), size.height), cornerRadius = radius)
                    val x = size.width * SendaRules.CRITICAL_COURSE_SHARE.toFloat()
                    drawLine(
                        threshold,
                        Offset(x, -4f),
                        Offset(x, size.height + 4f),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 16.dp, height = 2.dp)) {
                drawLine(threshold, Offset.Zero, Offset(size.width, 0f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            Spacer(Modifier.width(6.dp))
            Text("Límite de 40 % del tiempo de estudio", style = MaterialTheme.typography.labelSmall, color = threshold)
        }
    }
}

