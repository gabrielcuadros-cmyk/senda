package com.fuerzasalvaje.senda.domain.usecase.simulacrum

import com.fuerzasalvaje.senda.domain.engine.ReadinessCalculator
import com.fuerzasalvaje.senda.domain.engine.SimulacrumScoring
import com.fuerzasalvaje.senda.domain.engine.SimulacrumTimer
import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.RecommendationType
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.repository.AIRecommendationRepository
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.domain.repository.QuestionRepository
import com.fuerzasalvaje.senda.domain.repository.QuestionPrefetcher
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

/**
 * HU-15: creates an attempt and starts its clock now, without waiting for Gemini when the bank has enough.
 * With at least [SendaRules.MIN_QUESTIONS_TO_START] available questions it starts with them and the rest are
 * added in the background as they arrive; with fewer, the missing ones are generated first. Every attempt
 * also refills the bank for the next one.
 */
class StartSimulacrumUseCase(
    private val bank: QuestionBankUseCase,
    private val prepare: PrepareQuestionsUseCase,
    private val simulacra: SimulacrumRepository,
    private val prefetcher: QuestionPrefetcher,
    private val clock: Clock,
    /** Tests pass a seeded generator to make the question order predictable. */
    private val random: Random,
) {
    @Inject constructor(
        bank: QuestionBankUseCase,
        prepare: PrepareQuestionsUseCase,
        simulacra: SimulacrumRepository,
        prefetcher: QuestionPrefetcher,
        clock: Clock,
    ) : this(bank, prepare, simulacra, prefetcher, clock, Random.Default)

    suspend operator fun invoke(
        courseId: String,
        questionCount: Int = SendaRules.DEFAULT_SIMULACRUM_QUESTIONS,
        minutes: Int = SendaRules.DEFAULT_SIMULACRUM_MINUTES,
        topics: String? = null,
    ): Result<Simulacrum> = safely {
        val available = bank.available(courseId)
        val pool = if (available.size >= SendaRules.MIN_QUESTIONS_TO_START) available else prepare(courseId, questionCount, topics).getOrThrow()
        val chosen = pool.shuffled(random).take(questionCount)
        val simulacrum = Simulacrum(
            id = UUID.randomUUID().toString(),
            courseId = courseId,
            questionIds = chosen.map { it.id },
            timeLimitMinutes = minutes,
            startedAt = clock.instant(),
        )
        simulacra.save(simulacrum).getOrThrow()
        if (chosen.size < questionCount) prefetcher.topUp(courseId, simulacrum.id, questionCount, topics)
        prefetcher.refill(courseId, topics = topics)
        simulacrum
    }
}

/** An attempt with its questions in order. */
data class SimulacrumSession(val simulacrum: Simulacrum, val questions: List<Question>)

class ObserveSimulacrumUseCase @Inject constructor(
    private val simulacra: SimulacrumRepository,
    private val questions: QuestionRepository,
) {
    operator fun invoke(id: String): Flow<SimulacrumSession?> =
        combine(simulacra.observeById(id), questions.observeAll()) { simulacrum, all ->
            simulacrum?.let {
                val byId = all.associateBy { q -> q.id }
                SimulacrumSession(it, it.questionIds.mapNotNull(byId::get))
            }
        }
}

/** Saves the answer as soon as it changes, so nothing is lost if the app closes. */
class AnswerQuestionUseCase @Inject constructor(private val simulacra: SimulacrumRepository) {
    /** Pure update, applied first so quick consecutive answers never overwrite each other. */
    fun apply(simulacrum: Simulacrum, question: Question, optionIndex: Int?): Simulacrum =
        SimulacrumScoring.answer(simulacrum, question, optionIndex)

    /** Keeps question ids appended in the background while the student was answering. */
    suspend fun persist(simulacrum: Simulacrum): Result<Unit> = SimulacrumWrites.mutex.withLock {
        simulacra.save(SimulacrumWrites.merge(simulacrum, simulacra.observeById(simulacrum.id).firstOrNull()))
    }
}

class FinishSimulacrumUseCase @Inject constructor(
    private val simulacra: SimulacrumRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(simulacrum: Simulacrum): Result<Simulacrum> {
        if (simulacrum.isFinished) return Result.success(simulacrum)
        return SimulacrumWrites.mutex.withLock {
            val merged = SimulacrumWrites.merge(simulacrum, simulacra.observeById(simulacrum.id).firstOrNull())
            val finished = SimulacrumScoring.finish(merged, clock.instant())
            simulacra.save(finished).map { finished }
        }
    }

    /** Attempts whose time ran out while the app was closed are submitted with what was answered. */
    suspend fun finishExpired(): Int {
        val now = clock.instant()
        val expired = simulacra.observeAll().first().filter { !it.isFinished && SimulacrumTimer.isExpired(it, now) }
        expired.forEach { invoke(it) }
        return expired.size
    }
}

/** HU-16: readiness per course, in %. Null when the course has no finished attempt. */
class ObserveReadinessUseCase @Inject constructor(private val simulacra: SimulacrumRepository) {
    operator fun invoke(): Flow<Map<String, Int?>> = simulacra.observeAll().map { all ->
        all.groupBy { it.courseId }.mapValues { (_, list) -> ReadinessCalculator.readiness(list) }
    }
}

/**
 * HU-17: AI explanation of a wrong or missing answer. Cached per question and answer as an
 * AIRecommendation with a deterministic id, so the same mistake never calls Gemini twice.
 */
class ExplainMistakeUseCase @Inject constructor(
    private val gemini: GeminiService,
    private val recommendations: AIRecommendationRepository,
    private val courses: CourseRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(question: Question, selectedOptionIndex: Int?): Result<String> {
        val cacheId = cacheId(question.id, selectedOptionIndex)
        recommendations.observeById(cacheId).firstOrNull()?.let { return Result.success(it.content) }

        val courseName = courses.observeById(question.courseId).firstOrNull()?.name.orEmpty()
        val statement = question.scenario?.let { "$it\n${question.statement}" } ?: question.statement
        return gemini.explainMistake(
            courseName = courseName,
            question = statement,
            options = question.options,
            correctAnswer = question.options[question.correctOptionIndex],
            studentAnswer = selectedOptionIndex?.let { question.options.getOrNull(it) },
        ).mapCatching { text ->
            recommendations.save(
                AIRecommendation(
                    id = cacheId,
                    type = RecommendationType.MISTAKE_EXPLANATION,
                    content = text.trim(),
                    courseId = question.courseId,
                    createdAt = clock.instant(),
                )
            ).getOrThrow()
            text.trim()
        }
    }

    companion object {
        fun cacheId(questionId: String, selectedOptionIndex: Int?) = "explain-$questionId-${selectedOptionIndex ?: "none"}"
    }
}
