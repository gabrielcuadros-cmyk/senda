package com.fuerzasalvaje.senda.domain.usecase.wellbeing

import com.fuerzasalvaje.senda.domain.engine.CalendarPlanner
import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.PendingStudyItem
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.RecommendationType
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.StressCheckIn
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.domain.repository.AIRecommendationRepository
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.domain.repository.PomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.repository.StressCheckInRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.pendingStudyMinutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** D-12: today's stress check-in, or null when not done yet. */
class ObserveTodayStressUseCase @Inject constructor(
    private val repository: StressCheckInRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<StressCheckIn?> = repository.observeForDate(LocalDate.now(clock))
}

/** D-12: one check-in per day, 1 to 5. Saving again the same day replaces it. */
class SaveStressCheckInUseCase @Inject constructor(
    private val repository: StressCheckInRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(level: Int): Result<Unit> {
        if (level !in SendaRules.STRESS_RANGE) return Result.failure(IllegalArgumentException("Stress must be 1 to 5"))
        return repository.save(StressCheckIn(LocalDate.now(clock), level, clock.instant()))
    }
}

/** Latest study tip saved for the student. */
class ObserveLatestTipUseCase @Inject constructor(private val repository: AIRecommendationRepository) {
    operator fun invoke(): Flow<AIRecommendation?> = repository.observeAll().map { list ->
        list.filter { it.type == RecommendationType.STUDY_TIPS }.maxByOrNull { it.createdAt }
    }
}

/**
 * Asks the AI tutor for study tips using today's stress and the computed workload (D-12), then stores the answer
 * as an AIRecommendation. At most one call per day: today's tip is returned again unless the stress check-in
 * changed since it was written.
 */
class GenerateStudyTipsUseCase @Inject constructor(
    private val gemini: GeminiService,
    private val priorities: ObservePrioritiesUseCase,
    private val courses: CourseRepository,
    private val studyBlocks: StudyBlockRepository,
    private val pomodoroSessions: PomodoroSessionRepository,
    private val recommendations: AIRecommendationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(stressLevel: Int): Result<AIRecommendation> {
        val today = LocalDate.now(clock)
        recommendations.observeAll().first()
            .filter { it.type == RecommendationType.STUDY_TIPS && it.createdAt.atZone(clock.zone).toLocalDate() == today }
            .maxByOrNull { it.createdAt }
            ?.takeIf { it.stressLevel == stressLevel }
            ?.let { return Result.success(it) }
        val courseNames = courses.observeAll().first().associate { it.id to it.name }
        val blocks = studyBlocks.observeAll().first()
        val sessions = pomodoroSessions.observeAll().first()
        val items = priorities().first().take(MAX_ITEMS).map {
            PendingStudyItem(
                title = it.phase?.let { phase -> "${it.evaluation.title} (${phase.name})" } ?: it.evaluation.title,
                courseName = courseNames[it.evaluation.courseId].orEmpty(),
                priorityScore = it.normalizedScore.toDouble(),
                hoursUntilDue = it.hoursRemaining.toLong(),
                pendingStudyHours = pendingStudyMinutes(it.evaluation, blocks, sessions) / 60.0,
            )
        }
        return gemini.studyTips(StudyTipsRequest(stressLevel, items, weeklyPomodoroMinutes(sessions))).mapCatching { text ->
            val recommendation = AIRecommendation(
                id = UUID.randomUUID().toString(),
                type = RecommendationType.STUDY_TIPS,
                content = text.trim(),
                stressLevel = stressLevel,
                createdAt = clock.instant(),
            )
            recommendations.save(recommendation).getOrThrow()
            recommendation
        }
    }

    /** Focus minutes of the sessions started since Monday of the current week. */
    private fun weeklyPomodoroMinutes(sessions: List<PomodoroSession>): Int {
        val weekStart = CalendarPlanner.weekStartOf(LocalDate.now(clock)).atStartOfDay(clock.zone).toInstant()
        return sessions.filter { !it.startedAt.isBefore(weekStart) }.sumOf { it.focusedMinutes }
    }

    private companion object {
        const val MAX_ITEMS = 8
    }
}
