package com.fuerzasalvaje.senda.domain.usecase.simulacrum

import com.fuerzasalvaje.senda.domain.engine.QuestionValidator
import com.fuerzasalvaje.senda.domain.engine.ReadinessCalculator
import com.fuerzasalvaje.senda.domain.engine.SimulacrumScoring
import com.fuerzasalvaje.senda.domain.engine.SimulacrumTimer
import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.QuestionSource
import com.fuerzasalvaje.senda.domain.model.RecommendationType
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.repository.AIRecommendationRepository
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.domain.repository.QuestionRepository
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

/** The course has no syllabus text: the student must write the topics first. */
class MissingSyllabusException : IllegalStateException("The course has no syllabus to generate questions from")

/** Gemini answered, but none of the questions passed validation. */
class NoValidQuestionsException : IllegalStateException("No valid questions were generated")

/** Syllabus text of a course: the "temario" of its exams (D-11). */
class CourseSyllabusUseCase @Inject constructor(private val evaluations: EvaluationRepository) {
    suspend operator fun invoke(courseId: String): String =
        evaluations.observeByCourse(courseId).first()
            .filterIsInstance<Exam>()
            .mapNotNull { exam -> exam.syllabus.trim().takeIf { it.isNotEmpty() }?.let { "${exam.title}: $it" } }
            .joinToString("\n")
}

/**
 * HU-15: makes sure the course has at least [count] questions. When it does not, Gemini creates
 * them from the syllabus (or from [topics] typed by the student); only valid ones are stored.
 */
class PrepareQuestionsUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val courses: CourseRepository,
    private val syllabus: CourseSyllabusUseCase,
    private val gemini: GeminiService,
    private val clock: Clock,
) {
    suspend operator fun invoke(courseId: String, count: Int, topics: String? = null): Result<List<Question>> {
        val existing = questions.observeByCourse(courseId).first()
        if (existing.size >= count) return Result.success(existing)

        val source = topics?.trim()?.takeIf { it.isNotEmpty() } ?: syllabus(courseId)
        if (source.isBlank()) return Result.failure(MissingSyllabusException())

        val courseName = courses.observeById(courseId).firstOrNull()?.name.orEmpty()
        val toGenerate = maxOf(SendaRules.DEFAULT_SIMULACRUM_QUESTIONS, count - existing.size)
        return gemini.generateQuestions(courseName, source, toGenerate).mapCatching { generated ->
            val valid = generated.filter(QuestionValidator::isValid)
            if (valid.isEmpty()) throw NoValidQuestionsException()
            val created = valid.map {
                Question(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    statement = it.statement.trim(),
                    scenario = it.scenario,
                    options = it.options.map(String::trim),
                    correctOptionIndex = it.correctOptionIndex,
                    explanation = it.explanation.trim(),
                    source = QuestionSource.AI,
                    createdAt = clock.instant(),
                )
            }
            created.forEach { questions.save(it).getOrThrow() }
            existing + created
        }
    }
}

/** HU-15: creates an attempt with up to [questionCount] questions and starts its clock now. */
class StartSimulacrumUseCase(
    private val prepare: PrepareQuestionsUseCase,
    private val simulacra: SimulacrumRepository,
    private val clock: Clock,
    /** Tests pass a seeded generator to make the question order predictable. */
    private val random: Random,
) {
    @Inject constructor(prepare: PrepareQuestionsUseCase, simulacra: SimulacrumRepository, clock: Clock) :
        this(prepare, simulacra, clock, Random.Default)

    suspend operator fun invoke(
        courseId: String,
        questionCount: Int = SendaRules.DEFAULT_SIMULACRUM_QUESTIONS,
        minutes: Int = SendaRules.DEFAULT_SIMULACRUM_MINUTES,
        topics: String? = null,
    ): Result<Simulacrum> = prepare(courseId, questionCount, topics).mapCatching { bank ->
        val simulacrum = Simulacrum(
            id = UUID.randomUUID().toString(),
            courseId = courseId,
            questionIds = bank.shuffled(random).take(questionCount).map { it.id },
            timeLimitMinutes = minutes,
            startedAt = clock.instant(),
        )
        simulacra.save(simulacrum).getOrThrow()
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

    suspend fun persist(simulacrum: Simulacrum): Result<Unit> = simulacra.save(simulacrum)
}

class FinishSimulacrumUseCase @Inject constructor(
    private val simulacra: SimulacrumRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(simulacrum: Simulacrum): Result<Simulacrum> {
        if (simulacrum.isFinished) return Result.success(simulacrum)
        val finished = SimulacrumScoring.finish(simulacrum, clock.instant())
        return simulacra.save(finished).map { finished }
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
