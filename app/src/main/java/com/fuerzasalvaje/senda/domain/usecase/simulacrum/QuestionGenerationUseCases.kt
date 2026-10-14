package com.fuerzasalvaje.senda.domain.usecase.simulacrum

import com.fuerzasalvaje.senda.domain.engine.QuestionQuality
import com.fuerzasalvaje.senda.domain.engine.QuestionValidator
import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.QuestionReport
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.QuestionSource
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.domain.repository.QuestionPrefetcher
import com.fuerzasalvaje.senda.domain.repository.QuestionRepository
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** The syllabus is missing or too short and there is no PDF: the student must write the topics. */
class MissingSyllabusException : IllegalStateException("The course has no usable syllabus or PDF")

/** Gemini answered, but fewer than [SendaRules.MIN_QUESTIONS_TO_START] questions passed every check. */
class NoValidQuestionsException(val received: Int, val valid: Int) :
    IllegalStateException("Only $valid of $received generated questions were valid")

/** What the AI studies from: the exams' syllabus, the lab titles and every exam and lab PDF (D-11). */
data class CourseMaterial(val text: String, val syllabusChars: Int, val pdfPaths: List<String>) {
    /** Too little to generate meaningful questions (fewer than 15 characters and no PDF). */
    val isInsufficient: Boolean get() = syllabusChars < MIN_SYLLABUS_CHARS && pdfPaths.isEmpty()

    companion object {
        const val MIN_SYLLABUS_CHARS = 15
    }
}

class CourseMaterialUseCase @Inject constructor(private val evaluations: EvaluationRepository) {
    suspend operator fun invoke(courseId: String): CourseMaterial {
        val list = evaluations.observeByCourse(courseId).first()
        val exams = list.filterIsInstance<Exam>()
        val labs = list.filterIsInstance<Lab>()
        val withSyllabus = exams.filter { it.syllabus.isNotBlank() }
        val labsWithGuide = labs.filter { it.guidePdfPath != null }
        val lines = withSyllabus.map { "${it.title}: ${it.syllabus.trim()}" } +
            labsWithGuide.map { lab -> "Laboratorio ${lab.guideNumber.ifBlank { "" }} ${lab.title}".replace("  ", " ").trim() }
        return CourseMaterial(
            text = lines.joinToString("\n"),
            syllabusChars = withSyllabus.sumOf { it.syllabus.trim().length },
            pdfPaths = exams.mapNotNull { it.syllabusPdfPath } + labsWithGuide.mapNotNull { it.guidePdfPath },
        )
    }
}

/**
 * HU-15 generation pipeline: batches of [SendaRules.QUESTION_BATCH_SIZE] generated in parallel; each batch is
 * validated (structure, "todas/ninguna de las anteriores", relation to the syllabus, duplicates), reviewed by a
 * second AI call and rebalanced so the correct answer is not always the same letter. A failed batch is skipped:
 * the others are still used. Only when every batch fails does the call fail.
 */
class GenerateQuestionBatchesUseCase(
    private val gemini: GeminiService,
    private val logger: AiLogger,
    private val random: Random,
) {
    @Inject constructor(gemini: GeminiService, logger: AiLogger) : this(gemini, logger, Random.Default)

    suspend operator fun invoke(base: QuestionRequest, total: Int, existingStatements: List<String> = emptyList()): Result<List<GeneratedQuestion>> {
        if (total <= 0) return Result.success(emptyList())
        val sizes = batchSizes(total)
        // Relevance is judged against the typed syllabus; with a PDF the questions may rightly use its own terms.
        val keywords = if (base.pdfPaths.isEmpty()) QuestionQuality.keywords(base.syllabus) else emptySet()
        val avoid = existingStatements.takeLast(MAX_AVOID)
        val results = coroutineScope {
            sizes.mapIndexed { index, size ->
                async {
                    safely {
                        batch(base.copy(count = size, batchIndex = index, batchCount = sizes.size, avoidStatements = avoid), keywords, existingStatements)
                    }
                }
            }.awaitAll()
        }
        results.forEachIndexed { index, result ->
            result.exceptionOrNull()?.let { logger.warn("Batch ${index + 1}/${sizes.size} failed: ${it.javaClass.simpleName}: ${it.message}") }
        }
        val arrived = results.mapNotNull { it.getOrNull() }
        if (arrived.isEmpty()) return Result.failure(mostRelevant(results.mapNotNull { it.exceptionOrNull() }))
        val merged = QuestionQuality.dedupe(arrived.flatten(), existingStatements)
        logger.info("Batches ok=${arrived.size}/${sizes.size} questions=${merged.size} of $total requested")
        return Result.success(merged)
    }

    private suspend fun batch(request: QuestionRequest, keywords: Set<String>, existing: List<String>): List<GeneratedQuestion> {
        val generated = gemini.generateQuestions(request).getOrThrow()
        generated.forEachIndexed { i, q ->
            val problems = QuestionValidator.problems(q)
            if (problems.isNotEmpty()) logger.warn("Batch ${request.batchIndex + 1}: discarded question #${i + 1}: ${problems.joinToString(", ")}")
        }
        val valid = generated.filter(QuestionValidator::isValid)
        val related = valid.filter { QuestionQuality.isRelated(it, keywords) }
        val unique = QuestionQuality.dedupe(related, existing)
        logger.info(
            "Batch ${request.batchIndex + 1}/${request.batchCount}: received=${generated.size} valid=${valid.size} " +
                "related=${related.size} unique=${unique.size}"
        )
        if (unique.isEmpty()) return emptyList()
        val reviewed = review(request, unique, keywords)
        return QuestionQuality.rebalance(reviewed, random)
    }

    /** Keeps approved and corrected questions. If the review itself cannot be done, the checked batch is kept. */
    private suspend fun review(request: QuestionRequest, batch: List<GeneratedQuestion>, keywords: Set<String>): List<GeneratedQuestion> =
        gemini.reviewQuestions(request, batch).fold(
            onSuccess = { verdicts -> applyVerdicts(batch, verdicts.associateBy { it.index }, keywords) },
            onFailure = { error ->
                logger.warn("Review unavailable (${error.javaClass.simpleName}); keeping ${batch.size} checked questions")
                batch
            },
        )

    private fun applyVerdicts(
        batch: List<GeneratedQuestion>,
        verdicts: Map<Int, com.fuerzasalvaje.senda.domain.model.QuestionVerdict>,
        keywords: Set<String>,
    ): List<GeneratedQuestion> {
        val kept = batch.indices.mapNotNull { index ->
            val verdict = verdicts[index]
            val corrected = verdict?.corrected
            when {
                verdict == null -> null
                verdict.approved -> batch[index]
                corrected != null && QuestionValidator.isValid(corrected) && QuestionQuality.isRelated(corrected, keywords) -> corrected
                else -> null
            }
        }
        val fixed = batch.indices.count { verdicts[it]?.approved == false && verdicts[it]?.corrected != null }
        logger.info("Review: approved=${verdicts.values.count { it.approved }} corrected=$fixed kept=${kept.size}/${batch.size}")
        return QuestionQuality.dedupe(kept)
    }

    private fun mostRelevant(errors: List<Throwable>): Throwable =
        errors.firstOrNull { it is AiError.Configuration && it.invalidKey }
            ?: errors.firstOrNull { it is AiError.FreeLimitReached }
            ?: errors.firstOrNull()
            ?: AiError.InvalidResponse("No batch answered")

    companion object {
        /** Statements sent to the model as "do not repeat". */
        const val MAX_AVOID = 30

        /** 12 -> [5, 5, 2]; 3 -> [3]. */
        fun batchSizes(total: Int, size: Int = SendaRules.QUESTION_BATCH_SIZE): List<Int> =
            if (total <= 0) emptyList() else List((total + size - 1) / size) { i -> minOf(size, total - i * size) }
    }
}

/** Like runCatching, but keeps coroutine cancellation working. */
internal suspend fun <T> safely(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Questions that can go into a new attempt. */
class QuestionBankUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val simulacra: SimulacrumRepository,
) {
    /**
     * Not reported, and not answered correctly in the last [SendaRules.RECENT_ATTEMPTS_WITHOUT_REPEATS] finished
     * attempts of the course, so the student meets new questions.
     */
    suspend fun available(courseId: String): List<Question> =
        filter(questions.observeByCourse(courseId).first(), simulacra.observeByCourse(courseId).first())

    /** Available questions per course, live (Simulacros cards). */
    fun observeAvailableCounts(): Flow<Map<String, Int>> =
        combine(questions.observeAll(), simulacra.observeAll()) { all, attempts ->
            all.groupBy { it.courseId }.mapValues { (courseId, list) -> filter(list, attempts.filter { it.courseId == courseId }).size }
        }

    suspend fun all(courseId: String): List<Question> = questions.observeByCourse(courseId).first()

    companion object {
        fun filter(bank: List<Question>, attempts: List<Simulacrum>): List<Question> {
            val recentlyRight = attempts
                .filter { it.isFinished }
                .sortedByDescending { it.finishedAt }
                .take(SendaRules.RECENT_ATTEMPTS_WITHOUT_REPEATS)
                .flatMap { attempt -> attempt.answers.filter { it.correct }.map { it.questionId } }
                .toSet()
            return bank.filter { !it.isReported && it.id !in recentlyRight }
        }
    }
}

/** Generates [count] new questions for a course from its material (or typed [topics]) and stores them. */
class GenerateCourseQuestionsUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val courses: CourseRepository,
    private val material: CourseMaterialUseCase,
    private val batches: GenerateQuestionBatchesUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(courseId: String, count: Int, topics: String? = null): Result<List<Question>> {
        val courseMaterial = material(courseId)
        val typed = topics?.trim().orEmpty()
        if (typed.length < CourseMaterial.MIN_SYLLABUS_CHARS && courseMaterial.isInsufficient) {
            return Result.failure(MissingSyllabusException())
        }
        val text = if (typed.length >= CourseMaterial.MIN_SYLLABUS_CHARS) typed else courseMaterial.text
        val courseName = courses.observeById(courseId).firstOrNull()?.name.orEmpty()
        val existing = questions.observeByCourse(courseId).first().map { q -> listOfNotNull(q.scenario, q.statement).joinToString(" ") }
        val request = QuestionRequest(courseName, text, count, courseMaterial.pdfPaths)
        return batches(request, count, existing).mapCatching { generated ->
            val now = clock.instant()
            generated.map {
                Question(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    statement = it.statement.trim(),
                    scenario = it.scenario,
                    options = it.options.map(String::trim),
                    correctOptionIndex = it.correctOptionIndex,
                    explanation = it.explanation.trim(),
                    source = QuestionSource.AI,
                    createdAt = now,
                ).also { question -> questions.save(question).getOrThrow() }
            }
        }
    }
}

/**
 * HU-15, used when the bank cannot even start an attempt: generates now what is missing to reach [count]
 * available questions. At least [SendaRules.MIN_QUESTIONS_TO_START] are required.
 */
class PrepareQuestionsUseCase @Inject constructor(
    private val bank: QuestionBankUseCase,
    private val generate: GenerateCourseQuestionsUseCase,
) {
    suspend operator fun invoke(courseId: String, count: Int, topics: String? = null): Result<List<Question>> {
        val available = bank.available(courseId)
        if (available.size >= count) return Result.success(available)
        return generate(courseId, count - available.size, topics).mapCatching { created ->
            val total = available + created
            if (total.size < SendaRules.MIN_QUESTIONS_TO_START) throw NoValidQuestionsException(count - available.size, created.size)
            total
        }
    }
}

/** Keeps [SendaRules.QUESTION_BANK_TARGET] questions ready per course; [fresh] adds a full new set. */
class RefillQuestionBankUseCase @Inject constructor(
    private val bank: QuestionBankUseCase,
    private val generate: GenerateCourseQuestionsUseCase,
) {
    suspend operator fun invoke(courseId: String, fresh: Boolean = false, topics: String? = null): Result<Int> {
        val missing = if (fresh) SendaRules.QUESTION_BANK_TARGET else SendaRules.QUESTION_BANK_TARGET - bank.available(courseId).size
        if (missing <= 0) return Result.success(0)
        return generate(courseId, missing, topics).map { it.size }
    }
}

/** Serializes read-modify-write of attempts: answers, finishing and questions arriving late. */
object SimulacrumWrites {
    val mutex = Mutex()

    /** [mine] with any question ids that were appended to the stored copy meanwhile. */
    fun merge(mine: Simulacrum, stored: Simulacrum?): Simulacrum {
        if (stored == null) return mine
        val ids = stored.questionIds + mine.questionIds.filterNot { it in stored.questionIds }
        return mine.copy(questionIds = ids)
    }
}

/** Adds questions to an attempt that started with fewer than [desired] (bank first, then Gemini). */
class TopUpSimulacrumUseCase @Inject constructor(
    private val simulacra: SimulacrumRepository,
    private val bank: QuestionBankUseCase,
    private val generate: GenerateCourseQuestionsUseCase,
) {
    suspend operator fun invoke(simulacrumId: String, desired: Int, topics: String? = null): Result<Int> = safely {
        val attempt = simulacra.observeById(simulacrumId).firstOrNull() ?: return@safely 0
        if (attempt.isFinished) return@safely 0
        val missing = desired - attempt.questionIds.size
        if (missing <= 0) return@safely 0
        val fromBank = bank.available(attempt.courseId).filterNot { it.id in attempt.questionIds }.take(missing)
        val stillMissing = missing - fromBank.size
        val created = if (stillMissing > 0) generate(attempt.courseId, stillMissing, topics).getOrThrow() else emptyList()
        val newIds = (fromBank + created).map { it.id }
        SimulacrumWrites.mutex.withLock {
            val latest = simulacra.observeById(simulacrumId).firstOrNull() ?: return@withLock 0
            if (latest.isFinished) return@withLock 0
            val ids = (latest.questionIds + newIds.filterNot { it in latest.questionIds }).take(desired)
            simulacra.save(latest.copy(questionIds = ids)).getOrThrow()
            ids.size - latest.questionIds.size
        }
    }
}

/** HU-17 feedback: a reported question leaves the bank (it stays visible in past results) and is replaced. */
class ReportQuestionUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val prefetcher: QuestionPrefetcher,
    private val logger: AiLogger,
) {
    suspend operator fun invoke(question: Question, reason: QuestionReport): Result<Unit> {
        if (question.isReported) return Result.success(Unit)
        logger.info("Question reported: reason=$reason course=${question.courseId}")
        return questions.save(question.copy(reportReason = reason)).onSuccess { prefetcher.refill(question.courseId) }
    }
}

/** One background generation job, as the WorkManager worker receives it. */
data class PrefetchJob(
    val courseId: String,
    val fresh: Boolean = false,
    val simulacrumId: String? = null,
    val desired: Int = 0,
    val topics: String? = null,
)

enum class PrefetchOutcome { SUCCESS, RETRY, FAIL }

/**
 * Logic of the prefetch worker: errors that more attempts can fix (network, timeouts, overload, quotas, too few
 * valid questions) are retried with backoff until they succeed; missing material, an invalid API key or a
 * signed-out student are final.
 */
class RunPrefetchUseCase @Inject constructor(
    private val refill: RefillQuestionBankUseCase,
    private val topUp: TopUpSimulacrumUseCase,
    private val logger: AiLogger,
) {
    suspend operator fun invoke(job: PrefetchJob): PrefetchOutcome {
        val result = if (job.simulacrumId != null) {
            topUp(job.simulacrumId, job.desired, job.topics)
        } else {
            refill(job.courseId, job.fresh, job.topics)
        }
        val error = result.exceptionOrNull() ?: return PrefetchOutcome.SUCCESS.also {
            logger.info("Prefetch done for course ${job.courseId}: +${result.getOrNull()} questions")
        }
        val final = error is MissingSyllabusException ||
            (error is AiError.Configuration && error.invalidKey) ||
            error is AuthError
        logger.warn("Prefetch ${if (final) "failed" else "will retry"}: ${error.javaClass.simpleName}: ${error.message}")
        return if (final) PrefetchOutcome.FAIL else PrefetchOutcome.RETRY
    }
}
