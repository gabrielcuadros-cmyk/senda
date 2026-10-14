package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.domain.model.SendaRules
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/** One ranked evaluation for the urgency list (HU-10, HU-11). */
data class PriorityItem(
    val evaluation: Evaluation,
    /** Raw value of (Difficulty x Weight) / max(HoursRemaining, 1). */
    val score: Double,
    /** [score] scaled to 0–100 against the maximum possible value. */
    val normalizedScore: Int,
    val dueAt: Instant,
    val hoursRemaining: Double,
    val isOverdue: Boolean,
    /** "Faltan X días y Y horas", or "Vencida". */
    val timeLabel: String,
    val importance: Importance,
    /** For projects, the phase the deadline refers to. */
    val phase: ProjectPhase? = null,
)

/**
 * Prioridad = (Dificultad x Peso) / TiempoRestante, with TiempoRestante in hours and a minimum of 1.
 * Completed evaluations are excluded; overdue ones go first. Ties: difficulty, course importance,
 * then the closest date (D-1, D-5).
 */
class PriorityEngine @Inject constructor(private val clock: Clock) {

    fun rank(
        evaluations: List<Evaluation>,
        importanceByCourse: Map<String, Importance> = emptyMap(),
    ): List<PriorityItem> {
        val now = clock.instant()
        return evaluations
            .mapNotNull { evaluate(it, importanceByCourse[it.courseId] ?: Importance.MEDIUM, now) }
            .sortedWith(ORDER)
    }

    fun evaluate(
        evaluation: Evaluation,
        importance: Importance,
        now: Instant = clock.instant(),
    ): PriorityItem? {
        if (evaluation.status == EvaluationStatus.COMPLETED) return null

        val phase = (evaluation as? Project)?.let { it.nextPendingPhase ?: return null }
        val dueAt = when (evaluation) {
            is Exam -> evaluation.startAt
            is Lab -> evaluation.dueAt
            is Project -> phase!!.dueAt
        }

        val hoursRemaining = Duration.between(now, dueAt).toMinutes() / 60.0
        val score = evaluation.difficulty * evaluation.weightPercent / max(hoursRemaining, MIN_HOURS)
        val isOverdue = !dueAt.isAfter(now)

        return PriorityItem(
            evaluation = evaluation,
            score = score,
            normalizedScore = (score / MAX_SCORE * 100).roundToInt().coerceIn(0, 100),
            dueAt = dueAt,
            hoursRemaining = hoursRemaining,
            isOverdue = isOverdue,
            timeLabel = if (isOverdue) OVERDUE_LABEL else remainingLabel(Duration.between(now, dueAt)),
            importance = importance,
            phase = phase,
        )
    }

    companion object {
        const val OVERDUE_LABEL = "Vencida"
        private const val MIN_HOURS = 1.0
        private val MAX_SCORE =
            SendaRules.DIFFICULTY_RANGE.last * SendaRules.MAX_WEIGHT_PERCENT / MIN_HOURS

        private val ORDER = compareByDescending<PriorityItem> { it.isOverdue }
            .thenByDescending { it.score }
            .thenByDescending { it.evaluation.difficulty }
            .thenByDescending { it.importance.rank }
            .thenBy { it.dueAt }

        /** "Faltan 1 día y 4 horas", "Falta 1 hora", "Faltan menos de 1 hora". */
        fun remainingLabel(remaining: Duration): String {
            val totalHours = remaining.toHours()
            if (totalHours < 1) return "Faltan menos de 1 hora"
            val days = totalHours / 24
            val hours = totalHours % 24
            val parts = buildList {
                if (days > 0) add(if (days == 1L) "1 día" else "$days días")
                if (hours > 0) add(if (hours == 1L) "1 hora" else "$hours horas")
            }
            val verb = if (parts.size == 1 && (days == 1L || (days == 0L && hours == 1L))) "Falta" else "Faltan"
            return "$verb ${parts.joinToString(" y ")}"
        }
    }
}
