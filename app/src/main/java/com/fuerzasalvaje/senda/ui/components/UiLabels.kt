package com.fuerzasalvaje.senda.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Science
import androidx.compose.ui.graphics.vector.ImageVector
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.domain.model.LabModality
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Spanish labels and formats for the UI. Code stays in English.

val SpanishLocale: Locale = Locale.forLanguageTag("es-PE")

val EvaluationType.label: String
    get() = when (this) {
        EvaluationType.EXAM -> "Examen"
        EvaluationType.LAB -> "Laboratorio"
        EvaluationType.PROJECT -> "Proyecto"
    }

val EvaluationType.pluralLabel: String
    get() = when (this) {
        EvaluationType.EXAM -> "Exámenes"
        EvaluationType.LAB -> "Laboratorios"
        EvaluationType.PROJECT -> "Proyectos"
    }

val EvaluationType.icon: ImageVector
    get() = when (this) {
        EvaluationType.EXAM -> Icons.Rounded.Quiz
        EvaluationType.LAB -> Icons.Rounded.Science
        EvaluationType.PROJECT -> Icons.AutoMirrored.Rounded.Assignment
    }

val EvaluationStatus.label: String
    get() = when (this) {
        EvaluationStatus.PENDING -> "Pendiente"
        EvaluationStatus.IN_PROGRESS -> "En proceso"
        EvaluationStatus.COMPLETED -> "Completado"
    }

val PhaseStatus.label: String
    get() = when (this) {
        PhaseStatus.PENDING -> "Pendiente"
        PhaseStatus.IN_PROGRESS -> "En proceso"
        PhaseStatus.DELIVERED -> "Entregado"
    }

val ExamType.label: String
    get() = when (this) {
        ExamType.PARTIAL -> "Parcial"
        ExamType.FINAL -> "Final"
        ExamType.CASE_STUDY -> "Caso de estudio"
    }

val LabModality.label: String
    get() = when (this) {
        LabModality.INDIVIDUAL -> "Individual"
        LabModality.GROUP -> "En parejas o grupal"
    }

val Importance.label: String
    get() = when (this) {
        Importance.LOW -> "Baja"
        Importance.MEDIUM -> "Media"
        Importance.HIGH -> "Alta"
    }

fun DayOfWeek.label(): String =
    getDisplayName(TextStyle.FULL, SpanishLocale).replaceFirstChar { it.titlecase(SpanishLocale) }

fun DayOfWeek.shortLabel(): String =
    getDisplayName(TextStyle.SHORT, SpanishLocale).removeSuffix(".").replaceFirstChar { it.titlecase(SpanishLocale) }

object Formats {
    private val time = DateTimeFormatter.ofPattern("HH:mm", SpanishLocale)
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d 'de' MMM", SpanishLocale)
    private val fullDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SpanishLocale)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", SpanishLocale)

    fun time(value: LocalTime): String = value.format(time)
    fun time(value: Instant, zone: ZoneId): String = value.atZone(zone).format(time)
    fun dayMonth(value: LocalDate): String = value.format(dayMonth).capitalized()
    fun fullDate(value: LocalDate): String = value.format(fullDate).capitalized()
    fun dateTime(value: Instant, zone: ZoneId): String =
        "${dayMonth(value.atZone(zone).toLocalDate())}, ${time(value, zone)}"

    /** "12 – 18 oct 2026" */
    fun weekRange(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        return "${weekStart.format(shortDate)} – ${end.format(shortDate)} ${end.year}"
    }

    fun hours(value: Double): String =
        if (value % 1.0 == 0.0) "${value.toInt()} h" else "${"%.1f".format(SpanishLocale, value)} h"

    fun percent(value: Double): String =
        if (value % 1.0 == 0.0) "${value.toInt()} %" else "${"%.1f".format(SpanishLocale, value)} %"

    private fun String.capitalized() = replaceFirstChar { it.titlecase(SpanishLocale) }
}
