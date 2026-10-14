package com.fuerzasalvaje.senda.data.notifications

import com.fuerzasalvaje.senda.domain.engine.EvaluationReminder
import com.fuerzasalvaje.senda.domain.engine.ExamRoutine
import com.fuerzasalvaje.senda.domain.model.CalendarEntry
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.usecase.focus.DailySummary
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Spanish notification texts. The rest reminder never suggests staying up (D-15). */
object ReminderTexts {

    private val spanish: Locale = Locale.forLanguageTag("es-PE")
    private val dayTime = DateTimeFormatter.ofPattern("EEE d 'de' MMM, HH:mm", spanish)
    private val time = DateTimeFormatter.ofPattern("HH:mm", spanish)

    const val REST_TITLE = "Hora de descansar"
    const val REST_TEXT =
        "Son las 22:30. Cierra por hoy y prepárate para dormir: el descanso también es parte de tu estudio."

    fun evaluationTitle(reminder: EvaluationReminder): String =
        "${typeLabel(reminder.entry)}: ${reminder.entry.title}"

    fun evaluationText(reminder: EvaluationReminder, zone: ZoneId): String {
        val entry = reminder.entry
        val course = entry.course?.name?.let { " · $it" }.orEmpty()
        return "${offsetLabel(reminder.offset)} · ${entry.start.atZone(zone).format(dayTime)}$course"
    }

    fun routineTitle(routine: ExamRoutine): String =
        "Rutina de ${SendaRules.BETWEEN_EXAMS_ROUTINE_MINUTES} minutos antes de ${routine.next.title}"

    fun routineText(routine: ExamRoutine, zone: ZoneId): String =
        "Terminó ${routine.finished.title}. Tu siguiente examen es a las ${routine.next.start.atZone(zone).format(time)}: " +
            "respira e hidrátate 5 min, repasa ideas clave 5 min y camina 5 min."

    fun studyBlockTitle(evaluationTitle: String?): String =
        "En ${SendaRules.STUDY_BLOCK_ALERT_MINUTES} min: " + (evaluationTitle?.let { "estudiar $it" } ?: "bloque de estudio")

    fun studyBlockText(block: StudyBlock, zone: ZoneId): String =
        "Bloque de ${block.startAt.atZone(zone).format(time)} a ${block.endAt.atZone(zone).format(time)}. " +
            "Toca para empezar un Pomodoro."

    const val SUMMARY_TITLE = "Tu día en Senda"

    fun summaryText(summary: DailySummary, zone: ZoneId): String {
        val today = when (summary.today.size) {
            0 -> "Hoy no tienes evaluaciones."
            1 -> "Hoy: ${summary.today.single().title} a las ${summary.today.single().start.atZone(zone).format(time)}."
            else -> "Hoy tienes ${summary.today.size} evaluaciones: ${summary.today.joinToString(", ") { it.title }}."
        }
        val top = summary.topPriority?.let { item ->
            val title = item.phase?.let { "${item.evaluation.title} · ${it.name}" } ?: item.evaluation.title
            " Prioridad n.º 1: $title (${item.timeLabel.replaceFirstChar { it.lowercase(spanish) }})."
        }.orEmpty()
        return today + top
    }

    private fun typeLabel(entry: CalendarEntry): String = when (entry.type) {
        EvaluationType.EXAM -> "Examen"
        EvaluationType.LAB -> "Laboratorio"
        EvaluationType.PROJECT -> "Entrega"
    }

    fun offsetLabel(offset: Duration): String {
        val days = offset.toDays()
        val hours = offset.toHours()
        val minutes = offset.toMinutes()
        return when {
            days >= 1 && offset == Duration.ofDays(days) -> if (days == 1L) "Mañana" else "En $days días"
            hours >= 1 && offset == Duration.ofHours(hours) -> if (hours == 1L) "En 1 hora" else "En $hours horas"
            else -> "En $minutes minutos"
        }
    }
}
