package com.fuerzasalvaje.senda.domain.usecase.simulacrum

import com.fuerzasalvaje.senda.domain.engine.QuestionValidator
import com.fuerzasalvaje.senda.domain.engine.ReadinessCalculator
import com.fuerzasalvaje.senda.domain.engine.SimulacrumScoring
import com.fuerzasalvaje.senda.domain.engine.SimulacrumTimer
import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.QuestionSource
import com.fuerzasalvaje.senda.domain.model.RecommendationType
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.repository.AIRecommendationRepository
import com.fuerzasalvaje.senda.domain.repository.AiLogger
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

/** The syllabus is missing or too short and there is no PDF: the student must write the topics. */
class MissingSyllabusException : IllegalStateException("The course has no usable syllabus or PDF")

/** Gemini answered, but fewer than [PrepareQuestionsUseCase.MIN_VALID_QUESTIONS] questions were valid. */
class NoValidQuestionsException(val received: Int, val valid: Int) :
    IllegalStateException("Only $valid of $received generated questions were valid")

/** What the AI can study from: the exams' syllabus text and their local PDFs (D-11). */
data class CourseMaterial(val text: String, val syllabusChars: Int, val pdfPaths: List<String>) {
    /** Too little to generate meaningful questions (fewer than 15 characters and no PDF). */
    val isInsufficient: Boolean get() = syllabusChars < MIN_SYLLABUS_CHARS && pdfPaths.isEmpty()

    companion object {
        const val MIN_SYLLABUS_CHARS = 15
    }
}

class CourseMaterialUseCase @Inject constructor(private val evaluations: EvaluationRepository) {
    suspend operator fun invoke(courseId: String): CourseMaterial {
        val exams = evaluations.observeByCourse(courseId).first().filterIsInstance<Exam>()
        val withSyllabus = exams.filter { it.syllabus.isNotBlank() }
        return CourseMaterial(
            text = withSyllabus.joinToString("\n") { "${it.title}: ${it.syllabus.trim()}" },
            syllabusChars = withSyllabus.sumOf { it.syllabus.trim().length },
            pdfPaths = exams.mapNotNull { it.syllabusPdfPath },
        )
    }
}

/**
 * HU-15: makes sure the course has at least [count] questions. When it does not, Gemini creates
 * them from the exams' PDFs and syllabus (or from [topics] typed by the student). Only valid
 * questions are stored, and at least [MIN_VALID_QUESTIONS] are required.
 */
class PrepareQuestionsUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val courses: CourseRepository,
    private val material: CourseMaterialUseCase,
    private val gemini: GeminiService,
    private val logger: AiLogger,
    private val clock: Clock,
) {
    suspend operator fun invoke(courseId: String, count: Int, topics: String? = null): Result<List<Question>> {
        val existing = questions.observeByCourse(courseId).first()
        if (existing.size >= count) return Result.success(existing)

        val courseMaterial = material(courseId)
        val typed = topics?.trim().orEmpty()
        if (typed.length < CourseMaterial.MIN_SYLLABUS_CHARS && courseMaterial.isInsufficient) {
            return Result.failure(MissingSyllabusException())
        }
        val text = if (typed.isNotEmpty()) typed else courseMaterial.text

        val courseName = courses.observeById(courseId).firstOrNull()?.name.orEmpty()
        val toGenerate = maxOf(SendaRules.DEFAULT_SIMULACRUM_QUESTIONS, count - existing.size)
        val request = QuestionRequest(courseName, text, toGenerate, courseMaterial.pdfPaths)
        return gemini.generateQuestions(request).mapCatching { generated ->
            val valid = generated.filter(QuestionValidator::isValid)
            logValidation(generated, valid.size)
            if (valid.size < MIN_VALID_QUESTIONS) throw NoValidQuestionsException(generated.size, valid.size)
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

    private fun logValidation(generated: List<GeneratedQuestion>, valid: Int) {
        logger.info("Validation: received=${generated.size} valid=$valid discarded=${generated.size - valid}")
        generated.forEachIndexed { index, question ->
            val problems = QuestionValidator.problems(question)
            if (problems.isNotEmpty()) logger.warn("Discarded question #${index + 1}: ${problems.joinToString(", ")}")
        }
    }

    companion object {
        /** Fewer valid questions than requested are accepted, as long as there are at least 3. */
        const val MIN_VALID_QUESTIONS = 3
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
