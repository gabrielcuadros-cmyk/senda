package com.fuerzasalvaje.senda.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fuerzasalvaje.senda.domain.model.Conflict
import com.fuerzasalvaje.senda.domain.model.ConflictType
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.ui.theme.CardShape
import com.fuerzasalvaje.senda.ui.theme.SendaTheme

/** Rounded 20 dp card used everywhere. [containerColor] defaults to the surface. */
@Composable
fun SendaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = CardShape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = CardShape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

/** HU-04: 1 to 5 stars, colored by difficulty level. Read-only when [onChange] is null. */
@Composable
fun DifficultyStars(
    value: Int,
    modifier: Modifier = Modifier,
    onChange: ((Int) -> Unit)? = null,
    starSize: Dp = 20.dp,
) {
    val color = SendaTheme.colors.forDifficulty(SendaRules.difficultyLevel(value))
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SendaRules.DIFFICULTY_RANGE.forEach { star ->
            val filled = star <= value
            val icon = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline
            val tint = if (filled) color else MaterialTheme.colorScheme.outline
            if (onChange != null) {
                IconButton(onClick = { onChange(star) }, modifier = Modifier.size(starSize + 16.dp)) {
                    Icon(icon, contentDescription = "$star estrellas", tint = tint, modifier = Modifier.size(starSize))
                }
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(starSize))
            }
        }
    }
}

/** Priority score 0–100 as a colored pill (HU-10). */
@Composable
fun PriorityBadge(score: Int, overdue: Boolean, modifier: Modifier = Modifier) {
    val colors = SendaTheme.colors
    val color = when {
        overdue || score >= 60 -> colors.hard
        score >= 25 -> colors.medium
        else -> colors.easy
    }
    Surface(
        modifier = modifier.semantics { contentDescription = "Prioridad $score de 100" },
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (overdue) "Vencida" else "$score", style = MaterialTheme.typography.labelMedium)
        }
    }
}

data class ChipOption<T>(val value: T, val label: String)

/** HU-06: quick filter chips; "Todos" clears the selection. */
@Composable
fun <T> FilterChipsRow(
    options: List<ChipOption<T>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SendaFilterChip(label = "Todos", selected = selected.isEmpty(), onClick = onClear)
        options.forEach { option ->
            SendaFilterChip(option.label, option.value in selected) { onToggle(option.value) }
        }
        trailing()
    }
}

@Composable
fun SendaFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

/** HU-07: lists red clashes and yellow busy days, naming the evaluations involved. */
@Composable
fun ConflictBanner(conflicts: List<Conflict>, modifier: Modifier = Modifier) {
    if (conflicts.isEmpty()) return
    val colors = SendaTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        conflicts.forEach { conflict ->
            val isClash = conflict.type == ConflictType.CLASH
            val color = if (isClash) colors.clash else colors.busyDay
            Surface(shape = CardShape, color = color.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (isClash) Icons.Rounded.WarningAmber else Icons.Rounded.EventBusy,
                        contentDescription = null,
                        tint = color,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${if (isClash) "Cruce" else "Día cargado"} · ${Formats.dayMonth(conflict.date)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(conflictMessage(conflict), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

fun conflictMessage(conflict: Conflict): String {
    val names = conflict.entries.map { it.title }
    val list = if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " y " + names.last()
    return when (conflict.type) {
        ConflictType.CLASH -> "$list se cruzan en el mismo horario."
        ConflictType.BUSY_DAY -> "${names.size} evaluaciones el mismo día: $list."
    }
}

/** HU-08: weekly progress ring. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    strokeWidth: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    centerLabel: String = "${(fraction * 100).toInt()} %",
    caption: String? = null,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color, startAngle = -90f, sweepAngle = 360f * animated, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, style = MaterialTheme.typography.headlineSmall)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Small rounded label, e.g. the evaluation type. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
