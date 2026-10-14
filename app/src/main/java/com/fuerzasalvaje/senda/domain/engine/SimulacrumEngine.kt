package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.SimulacrumAnswer
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** HU-15: AI questions are only kept when they are complete and unambiguous. */
object QuestionValidator {
    const val OPTION_COUNT = 4

    fun isValid(question: GeneratedQuestion): Boolean {
        val options = question.options.map { it.trim() }
        return question.statement.isNotBlank() &&
            options.size == OPTION_COUNT &&
            options.none { it.isBlank() } &&
            options.map { it.lowercase() }.toSet().size == OPTION_COUNT &&
            question.correctOptionIndex in options.indices &&
            question.explanation.isNotBlank()
    }
}

/**
 * The countdown is derived from timestamps (startedAt + limit), never from a running counter,
 * so it survives rotation, process death and closing the app.
 */
object SimulacrumTimer {
    fun deadline(simulacrum: Simulacrum): Instant =
        simulacrum.startedAt + Duration.ofMinutes(simulacrum.timeLimitMinutes.toLong())

    fun remaining(simulacrum: Simulacrum, now: Instant): Duration {
        val left = Duration.between(now, deadline(simulacrum))
        return if (left.isNegative) Duration.ZERO else left
    }

    fun isExpired(simulacrum: Simulacrum, now: Instant): Boolean = !now.isBefore(deadline(simulacrum))
}

object SimulacrumScoring {

    /** Records or replaces the answer to [question]; correctness is decided here, not by the UI. */
    fun answer(simulacrum: Simulacrum, question: Question, selectedOptionIndex: Int?): Simulacrum {
        val answer = SimulacrumAnswer(question.id, selectedOptionIndex, selectedOptionIndex == question.correctOptionIndex)
        return simulacrum.copy(answers = simulacrum.answers.filterNot { it.questionId == question.id } + answer)
    }

    /** Closes the attempt: unanswered questions are recorded as such and the end never passes the deadline. */
    fun finish(simulacrum: Simulacrum, now: Instant): Simulacrum {
        val answered = simulacrum.answers.associateBy { it.questionId }
        return simulacrum.copy(
            answers = simulacrum.questionIds.map { id -> answered[id] ?: SimulacrumAnswer(id, null, false) },
            finishedAt = minOf(now, SimulacrumTimer.deadline(simulacrum)),
        )
    }

    /** 0–20 grade rounded to one decimal (D-10). */
    fun roundedGrade(simulacrum: Simulacrum): Double = (simulacrum.grade * 10).roundToInt() / 10.0
}

/**
 * HU-16 with D-10: weighted average of the last 3 finished mock exams (0.5, 0.3, 0.2 from the most
 * recent), rescaled when there are fewer, as a percentage of the 20-point scale.
 */
object ReadinessCalculator {
    fun readiness(simulacra: List<Simulacrum>): Int? {
        val recent = simulacra
            .filter { it.isFinished && it.questionIds.isNotEmpty() }
            .sortedByDescending { it.finishedAt }
            .take(SendaRules.READINESS_WEIGHTS.size)
        if (recent.isEmpty()) return null
        val weights = SendaRules.READINESS_WEIGHTS.take(recent.size)
        val weighted = recent.zip(weights).sumOf { (simulacrum, weight) -> simulacrum.grade / SendaRules.MAX_GRADE * weight }
        return (weighted / weights.sum() * 100).roundToInt()
    }
}
