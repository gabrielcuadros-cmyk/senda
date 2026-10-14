package com.fuerzasalvaje.senda.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import com.fuerzasalvaje.senda.domain.model.StudyBlockEntry
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import java.time.Instant
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.ui.components.ChipOption
import com.fuerzasalvaje.senda.ui.components.ConflictBanner
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.components.EvaluationDetailSheet
import com.fuerzasalvaje.senda.ui.components.FilterChipsRow
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.pluralLabel
import com.fuerzasalvaje.senda.ui.components.shortLabel
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

private val HourHeight = 56.dp
private val DayWidth = 112.dp
private val TimeColumnWidth = 44.dp
private val DayHeaderHeight = 52.dp
private val MinBlockHeight = 30.dp
private const val FIRST_VISIBLE_HOUR = 7

@Composable
fun CalendarScreen(
    onEditEvaluation: (String) -> Unit,
    onCreateCourse: () -> Unit,
    onOpenPlan: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pdfDigests by viewModel.pdfDigests.collectAsStateWithLifecycle()
    val zone = viewModel.zone

    if (!state.loading && state.courses.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.School,
            title = "Tu calendario está vacío",
            message = "Crea tu primer curso y registra tus exámenes, laboratorios y proyectos.",
            actionLabel = "Crear mi primer curso",
            onAction = onCreateCourse,
            modifier = Modifier.padding(top = 48.dp),
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        WeekHeader(
            weekStart = state.weekStart,
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onToday = viewModel::goToToday,
            onOpenPlan = onOpenPlan,
        )
        FilterChipsRow(
            options = EvaluationType.entries.map { ChipOption(it, it.pluralLabel) },
            selected = state.filter.types,
            onToggle = viewModel::toggleType,
            onClear = viewModel::clearTypes,
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                CourseFilterChip(
                    courses = state.courses.map { ChipOption(it.id, it.name) },
                    selected = state.filter.courseId,
                    onSelect = viewModel::selectCourse,
                )
            },
        )
        ConflictBanner(state.conflicts, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (state.entries.isEmpty()) {
            Text(
                if (state.filter.isActive) "No hay evaluaciones con estos filtros esta semana." else "Sin evaluaciones esta semana.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        WeekGrid(
            state = state,
            zone = zone,
            onOpen = viewModel::open,
            onOpenStudy = viewModel::openStudy,
            modifier = Modifier.weight(1f),
        )
    }

    state.selectedStudy?.let { entry ->
        StudyBlockDialog(
            entry = entry,
            zone = zone,
            onDone = viewModel::markStudyDone,
            onRemove = viewModel::removeStudy,
            onDismiss = viewModel::closeStudy,
        )
    }

    state.selected?.let { entry ->
        EvaluationDetailSheet(
            evaluation = entry.evaluation,
            course = entry.course,
            zone = zone,
            highlightedPhase = entry.phase,
            conflicts = state.conflicts.filter { conflict -> conflict.entries.any { it.id == entry.id } },
            pdfStatus = pdfDigests[entry.evaluation.id]?.status,
            onDismiss = viewModel::close,
            onEdit = { viewModel.close(); onEditEvaluation(entry.evaluation.id) },
            onStatus = { viewModel.setStatus(entry.evaluation, it) },
            onPhaseStatus = { phase, status -> (entry.evaluation as? Project)?.let { viewModel.setPhaseStatus(it, phase.id, status) } },
        )
    }
}

@Composable
private fun WeekHeader(
    weekStart: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Semana anterior") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Semana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formats.weekRange(weekStart), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Semana siguiente") }
        TextButton(onClick = onToday) { Text("Hoy") }
        IconButton(onClick = onOpenPlan) { Icon(Icons.Rounded.EditCalendar, "Plan de estudio") }
    }
}

@Composable
private fun CourseFilterChip(courses: List<ChipOption<String>>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(courses.firstOrNull { it.value == selected }?.label ?: "Curso: todos", maxLines = 1) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos los cursos") }, onClick = { onSelect(null); expanded = false })
            courses.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option.value); expanded = false })
            }
        }
    }
}

@Composable
private fun WeekGrid(
    state: CalendarUiState,
    zone: ZoneId,
    onOpen: (CalendarEntry) -> Unit,
    onOpenStudy: (StudyBlockEntry) -> Unit,
    modifier: Modifier,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val hourPx = with(LocalDensity.current) { HourHeight.roundToPx() }
    LaunchedEffect(Unit) { vertical.scrollTo(FIRST_VISIBLE_HOUR * hourPx) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(modifier.fillMaxWidth()) {
        Column(Modifier.width(TimeColumnWidth)) {
            Spacer(Modifier.height(DayHeaderHeight))
            Column(Modifier.verticalScroll(vertical)) {
                (0 until 24).forEach { hour ->
                    Box(Modifier.height(HourHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Text("%02d:00".format(hour), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.horizontalScroll(horizontal)) {
                state.days.forEach { day -> DayHeader(day, state, Modifier.width(DayWidth).height(DayHeaderHeight)) }
            }
            Box(Modifier.verticalScroll(vertical)) {
                Row(Modifier.horizontalScroll(horizontal)) {
                    state.days.forEach { day ->
                        DayColumn(
                            items = state.entriesOn(day, zone).map { GridItem.Evaluation(it) } +
                                state.studyBlocksOn(day, zone).map { GridItem.Study(it) },
                            isToday = day == state.today,
                            clashingIds = state.clashingIds,
                            zone = zone,
                            lineColor = lineColor,
                            onOpen = onOpen,
                            onOpenStudy = onOpenStudy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, state: CalendarUiState, modifier: Modifier) {
    val isToday = day == state.today
    Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.dayOfWeek.shortLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                contentColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            ) {
                Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            if (state.clashOn(day)) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = "Cruce", tint = SendaTheme.colors.clash, modifier = Modifier.size(18.dp))
            }
            if (state.busyOn(day)) {
                Icon(Icons.Rounded.EventBusy, contentDescription = "Día cargado", tint = SendaTheme.colors.busyDay, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Anything drawn on the grid: an evaluation block or an accepted study block. */
private sealed interface GridItem {
    val key: String
    val start: Instant
    val end: Instant

    data class Evaluation(val entry: CalendarEntry) : GridItem {
        override val key get() = entry.id
        override val start: Instant get() = entry.start
        override val end: Instant get() = entry.end
    }

    data class Study(val entry: StudyBlockEntry) : GridItem {
        override val key get() = "study:${entry.block.id}"
        override val start: Instant get() = entry.block.startAt
        override val end: Instant get() = entry.block.endAt
    }
}

@Composable
private fun DayColumn(
    items: List<GridItem>,
    isToday: Boolean,
    clashingIds: Set<String>,
    zone: ZoneId,
    lineColor: androidx.compose.ui.graphics.Color,
    onOpen: (CalendarEntry) -> Unit,
    onOpenStudy: (StudyBlockEntry) -> Unit,
) {
    val todayTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val layout = remember(items) { lanes(items) }
    Box(
        Modifier
            .width(DayWidth)
            .height(HourHeight * 24)
            .background(if (isToday) todayTint else androidx.compose.ui.graphics.Color.Transparent)
            .drawBehind {
                val hour = HourHeight.toPx()
                for (i in 0..24) drawLine(lineColor, Offset(0f, i * hour), Offset(size.width, i * hour), strokeWidth = 1f)
                drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
            },
    ) {
        layout.forEach { (item, lane, laneCount) ->
            val start = item.start.atZone(zone).toLocalTime()
            val minutes = Duration.between(item.start, item.end).toMinutes().coerceAtLeast(0)
            val top: Dp = HourHeight * ((start.hour * 60 + start.minute) / 60f)
            val height: Dp = maxOf(HourHeight * (minutes / 60f), MinBlockHeight)
            val laneWidth = DayWidth / laneCount
            val modifier = Modifier
                .offset(x = laneWidth * lane, y = top)
                .width(laneWidth - 3.dp)
                .height(height)
                .padding(start = 2.dp)
            when (item) {
                is GridItem.Evaluation -> EventBlock(
                    entry = item.entry,
                    clashing = item.entry.id in clashingIds,
                    zone = zone,
                    onClick = { onOpen(item.entry) },
                    modifier = modifier,
                )
                is GridItem.Study -> StudyBlockView(item.entry, zone, onClick = { onOpenStudy(item.entry) }, modifier = modifier)
            }
        }
    }
}
@Composable
private fun EventBlock(entry: CalendarEntry, clashing: Boolean, zone: ZoneId, onClick: () -> Unit, modifier: Modifier) {
    val colors = SendaTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = colors.forType(entry.type),
        border = if (clashing) BorderStroke(2.dp, colors.clash) else null,
        modifier = modifier.alpha(if (entry.isDone) 0.55f else 1f),
    ) {
        val time = if (entry.isDeadline) "Entrega ${Formats.time(entry.start, zone)}" else "${Formats.time(entry.start, zone)}–${Formats.time(entry.end, zone)}"
        // Fit the content to the block: short blocks show one line instead of clipping two.
        BoxWithConstraints(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            val roomy = maxHeight >= 44.dp
            val titleLines = if (maxHeight >= 64.dp) 2 else 1
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isDone) Icon(Icons.Rounded.CheckCircle, contentDescription = "Completado", modifier = Modifier.size(12.dp))
                    Text(
                        if (roomy) entry.title else "${Formats.time(entry.start, zone)} ${entry.title}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = titleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (roomy) Text(time, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** HU-14: accepted study block, peach colored and always titled "Estudiar: …" so it never reads as an evaluation. */
@Composable
private fun StudyBlockView(entry: StudyBlockEntry, zone: ZoneId, onClick: () -> Unit, modifier: Modifier) {
    val done = entry.block.status == StudyBlockStatus.DONE
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = SendaTheme.colors.study,
        modifier = modifier.alpha(if (done) 0.55f else 1f),
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            val roomy = maxHeight >= 44.dp
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Bloque de estudio", modifier = Modifier.size(12.dp))
                    if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = "Hecho", modifier = Modifier.padding(start = 2.dp).size(12.dp))
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                if (roomy) {
                    Text(
                        "${Formats.time(entry.block.startAt, zone)}–${Formats.time(entry.block.endAt, zone)}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyBlockDialog(
    entry: StudyBlockEntry,
    zone: ZoneId,
    onDone: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("¿Quitar este bloque?") },
            text = { Text("Se borra del calendario y se cancela su aviso. Puedes volver a pedir un plan cuando quieras.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
        title = { Text(entry.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.explanation)
                Text(Formats.dateTime(entry.block.startAt, zone) + " – " + Formats.time(entry.block.endAt, zone))
                entry.course?.let { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("50 minutos de estudio y luego 10 de pausa.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (entry.block.status != StudyBlockStatus.DONE) TextButton(onClick = onDone) { Text("Marcar como hecho") }
            else TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = true }) { Text("Quitar del calendario") } },
    )
}

/** Splits overlapping blocks of one day into side-by-side lanes. */
private fun lanes(entries: List<GridItem>): List<Triple<GridItem, Int, Int>> {
    val visualEnd = { e: GridItem -> maxOf(e.end, e.start.plus(Duration.ofMinutes(35))) }
    val result = mutableListOf<Triple<GridItem, Int, Int>>()
    var cluster = mutableListOf<Pair<GridItem, Int>>()
    var clusterEnd = java.time.Instant.MIN
    val laneEnds = mutableListOf<java.time.Instant>()

    fun flush() {
        val count = (cluster.maxOfOrNull { it.second } ?: -1) + 1
        cluster.forEach { (entry, lane) -> result.add(Triple(entry, lane, count)) }
        cluster = mutableListOf()
        laneEnds.clear()
    }

    for (entry in entries.sortedBy { it.start }) {
        if (cluster.isNotEmpty() && !entry.start.isBefore(clusterEnd)) flush()
        val lane = laneEnds.indexOfFirst { !entry.start.isBefore(it) }.takeIf { it >= 0 } ?: laneEnds.size
        if (lane == laneEnds.size) laneEnds.add(visualEnd(entry)) else laneEnds[lane] = visualEnd(entry)
        cluster.add(entry to lane)
        clusterEnd = if (cluster.size == 1) visualEnd(entry) else maxOf(clusterEnd, visualEnd(entry))
    }
    if (cluster.isNotEmpty()) flush()
    return result
}

