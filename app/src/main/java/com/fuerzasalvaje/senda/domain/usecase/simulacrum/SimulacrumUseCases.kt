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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

/**
 * HU-15: creates an attempt from the bank and starts its clock now, without calling Gemini. Unused questions come
 * first; when they run out the asked ones are reused, failed ones first, in a new order and with their options
 * mixed. Only an empty bank (fewer than [SendaRules.MIN_QUESTIONS_TO_START] questions) is generated while the
 * student waits. The bank is refilled in the background only when fewer than [SendaRules.LOW_BANK_UNUSED] unused
 * questions are left.
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
        var picked = bank.pick(courseId, questionCount, random)
        if (picked.size < SendaRules.MIN_QUESTIONS_TO_START) {
            prepare(courseId, topics).getOrThrow()
            picked = bank.pick(courseId, questionCount, random)
            if (picked.size < SendaRules.MIN_QUESTIONS_TO_START) {
                throw NoValidQuestionsException(SendaRules.QUESTION_BANK_TARGET, picked.size)
            }
        }
        val chosen = picked.shuffled(random)
        val simulacrum = Simulacrum(
            id = UUID.randomUUID().toString(),
            courseId = courseId,
            questionIds = chosen.map { it.question.id },
            timeLimitMinutes = minutes,
            startedAt = clock.instant(),
            optionOrders = chosen.filter { it.reused }.associate { it.question.id to mixedOrder(it.question.options.size) },
        )
        simulacra.save(simulacrum).getOrThrow()
        if (bank.unused(courseId).size < SendaRules.LOW_BANK_UNUSED) prefetcher.refill(courseId, topics = topics)
        simulacrum
    }

    /** A random order of the options that differs from the stored one. */
    private fun mixedOrder(size: Int): List<Int> {
        val identity = (0 until size).toList()
        if (size < 2) return identity
        var order = identity.shuffled(random)
        while (order == identity) order = identity.shuffled(random)
        return order
    }
}

/** An attempt with its questions in order, each with its options as this attempt shows them. */
data class SimulacrumSession(val simulacrum: Simulacrum, val questions: List<Question>)

class ObserveSimulacrumUseCase @Inject constructor(
    private val simulacra: SimulacrumRepository,
    private val questions: QuestionRepository,
) {
    operator fun invoke(id: String): Flow<SimulacrumSession?> =
        combine(simulacra.observeById(id), questions.observeAll()) { simulacrum, all ->
            simulacrum?.let {
                val byId = all.associateBy { q -> q.id }
                SimulacrumSession(it, it.questionIds.mapNotNull { id -> byId[id]?.withOptionOrder(it.optionOrders[id]) })
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
 * HU-17: AI explanation of a wrong or missing answer. Cached per question and chosen answer as an AIRecommendation
 * with a deterministic id, so the same mistake never calls Gemini twice: the key uses the answer text (options may
 * be shown in another order when a question is reused) and simultaneous requests for it wait for the first one.
 */
class ExplainMistakeUseCase @Inject constructor(
    private val gemini: GeminiService,
    private val recommendations: AIRecommendationRepository,
    private val courses: CourseRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(question: Question, selectedOptionIndex: Int?): Result<String> {
        val cacheId = cacheId(question.id, selectedOptionIndex?.let { question.options.getOrNull(it) })
        return locks.computeIfAbsent(cacheId) { Mutex() }.withLock { explain(question, selectedOptionIndex, cacheId) }
    }

    private suspend fun explain(question: Question, selectedOptionIndex: Int?, cacheId: String): Result<String> {
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
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

        /** Same question and same chosen answer text: same id, whatever order the options were shown in. */
        fun cacheId(questionId: String, selectedAnswer: String?) =
            "explain-$questionId-${selectedAnswer?.trim()?.let { Integer.toHexString(it.hashCode()) } ?: "none"}"
    }
}
