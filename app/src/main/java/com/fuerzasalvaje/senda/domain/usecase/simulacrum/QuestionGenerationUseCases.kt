package com.fuerzasalvaje.senda.domain.usecase.simulacrum

import com.fuerzasalvaje.senda.domain.engine.PdfMarkdown
import com.fuerzasalvaje.senda.domain.engine.QuestionQuality
import com.fuerzasalvaje.senda.domain.engine.QuestionValidator
import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.QuestionReport
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.QuestionSource
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.localPdfPath
import com.fuerzasalvaje.senda.domain.model.pdfMarkdown
import com.fuerzasalvaje.senda.domain.model.withPdfMarkdown
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.AiQuota
import com.fuerzasalvaje.senda.domain.repository.AiUsageStore
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.domain.repository.QuestionPrefetcher
import com.fuerzasalvaje.senda.domain.repository.QuestionRepository
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import com.fuerzasalvaje.senda.domain.usecase.pdf.ExtractPdfMarkdownUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.PdfExtraction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** The syllabus is missing or too short and there is no PDF text: the student must write the topics. */
class MissingSyllabusException : IllegalStateException("The course has no usable syllabus or PDF")

/** Gemini answered, but fewer than [SendaRules.MIN_QUESTIONS_TO_START] questions passed every check. */
class NoValidQuestionsException(val received: Int, val valid: Int) :
    IllegalStateException("Only $valid of $received generated questions were valid")

/**
 * What the AI studies from, as text only (D-11): the exams' syllabus, the lab titles and the Markdown made on the
 * device from every exam and lab PDF ("## Diapositiva N" / "## Página N" sections).
 */
data class CourseMaterial(
    val text: String,
    val syllabusChars: Int,
    val pdfMarkdown: String = "",
    /** Identifies the content (syllabus + PDF Markdown) only: titles, dates, weights and status do not change it. */
    val contentHash: String = "",
) {
    /** Too little to generate meaningful questions (fewer than 15 characters and no PDF text). */
    val isInsufficient: Boolean get() = syllabusChars < MIN_SYLLABUS_CHARS && pdfMarkdown.isBlank()

    /** Hash of what a generation would use: the content, plus the topics the student typed, if any. */
    fun hashWith(topics: String?): String {
        val typed = topics?.trim().orEmpty()
        return if (typed.length >= MIN_SYLLABUS_CHARS) sha256("$contentHash\u0000$typed") else contentHash
    }

    companion object {
        const val MIN_SYLLABUS_CHARS = 15

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

class CourseMaterialUseCase @Inject constructor(
    private val evaluations: EvaluationRepository,
    private val extractPdf: ExtractPdfMarkdownUseCase,
) {
    suspend operator fun invoke(courseId: String): CourseMaterial {
        val list = evaluations.observeByCourse(courseId).first().map { backfill(it) }.sortedBy { it.id }
        val exams = list.filterIsInstance<Exam>()
        val labs = list.filterIsInstance<Lab>()
        val withSyllabus = exams.filter { it.syllabus.isNotBlank() }
        val labsWithGuide = labs.filter { it.guidePdfPath != null || it.guidePdfMarkdown.isNotBlank() }
        val lines = withSyllabus.map { "${it.title}: ${it.syllabus.trim()}" } +
            labsWithGuide.map { lab -> "Laboratorio ${lab.guideNumber.ifBlank { "" }} ${lab.title}".replace("  ", " ").trim() }
        // Several PDFs share the same budget, so the request stays as small as one PDF.
        val withPdf = list.filter { it.pdfMarkdown.isNotBlank() }
        val budget = if (withPdf.isEmpty()) 0 else PdfMarkdown.MAX_CHARS / withPdf.size
        val pdfText = withPdf.joinToString("\n\n") { evaluation ->
            val syllabus = (evaluation as? Exam)?.syllabus.orEmpty()
            "# PDF de \"${evaluation.title}\"\n" + PdfMarkdown.fit(evaluation.pdfMarkdown, syllabus, budget).text
        }
        val content = withSyllabus.map { it.syllabus.trim() } + withPdf.map { it.pdfMarkdown.trim() }
        return CourseMaterial(
            text = lines.joinToString("\n"),
            syllabusChars = withSyllabus.sumOf { it.syllabus.trim().length },
            pdfMarkdown = pdfText,
            contentHash = CourseMaterial.sha256(content.joinToString("\u0000")),
        )
    }

    /** PDFs attached before the Markdown existed are read once here, on the device, and the result is kept. */
    private suspend fun backfill(evaluation: Evaluation): Evaluation {
        val path = evaluation.localPdfPath
        if (path == null || evaluation.pdfMarkdown.isNotBlank()) return evaluation
        val result = extractPdf(path, (evaluation as? Exam)?.syllabus.orEmpty()) as? PdfExtraction.Ready ?: return evaluation
        val updated = evaluation.withPdfMarkdown(result.markdown, result.summarized)
        evaluations.save(updated)
        return updated
    }
}

/**
 * HU-15 generation: the whole set in ONE Gemini call, which checks its own questions (no second review call).
 * The code still drops what fails structure, "todas/ninguna de las anteriores", relation to the material and
 * duplicates, and rebalances the position of the correct answer.
 */
class GenerateQuestionSetUseCase(
    private val gemini: GeminiService,
    private val logger: AiLogger,
    private val random: Random,
) {
    @Inject constructor(gemini: GeminiService, logger: AiLogger) : this(gemini, logger, Random.Default)

    suspend operator fun invoke(base: QuestionRequest, existingStatements: List<String> = emptyList()): Result<List<GeneratedQuestion>> {
        if (base.count <= 0) return Result.success(emptyList())
        val request = base.copy(avoidStatements = existingStatements.takeLast(MAX_AVOID))
        return safely {
            val generated = gemini.generateQuestions(request).getOrThrow()
            generated.forEachIndexed { i, q ->
                val problems = QuestionValidator.problems(q)
                if (problems.isNotEmpty()) logger.warn("Discarded question #${i + 1}: ${problems.joinToString(", ")}")
            }
            // Relevance is judged against everything the model received: the syllabus and the PDF Markdown.
            val keywords = QuestionQuality.keywords(base.syllabus + "\n" + base.pdfMarkdown)
            val valid = generated.filter(QuestionValidator::isValid)
            val related = valid.filter { QuestionQuality.isRelated(it, keywords) }
            val unique = QuestionQuality.dedupe(related, existingStatements)
            logger.info(
                "Question set: requested=${base.count} received=${generated.size} valid=${valid.size} " +
                    "related=${related.size} unique=${unique.size} in 1 call"
            )
            QuestionQuality.rebalance(unique, random)
        }
    }

    companion object {
        /** Statements sent to the model as "do not repeat". */
        const val MAX_AVOID = 30
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

/** A question chosen for an attempt; [reused] ones were already asked and are shown with their options mixed. */
data class PickedQuestion(val question: Question, val reused: Boolean)

/**
 * The course question bank. A question is unused until it appears in an attempt; reported questions never come
 * back. Attempts take unused questions first and then reuse the asked ones, those failed last time first.
 */
class QuestionBankUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val simulacra: SimulacrumRepository,
) {
    suspend fun unused(courseId: String): List<Question> =
        unused(questions.observeByCourse(courseId).first(), simulacra.observeByCourse(courseId).first())

    /** Up to [count] questions for a new attempt, without calling Gemini. */
    suspend fun pick(courseId: String, count: Int, random: Random): List<PickedQuestion> {
        val bank = questions.observeByCourse(courseId).first().filter { !it.isReported }
        val attempts = simulacra.observeByCourse(courseId).first()
        val fresh = unused(bank, attempts).shuffled(random).map { PickedQuestion(it, reused = false) }
        val asked = bank.filter { question -> fresh.none { it.question.id == question.id } }
        val reused = reuseOrder(asked, attempts, random).map { PickedQuestion(it, reused = true) }
        return (fresh + reused).take(count)
    }

    /** Unused questions per course, live (Simulacros cards). */
    fun observeAvailableCounts(): Flow<Map<String, Int>> =
        combine(questions.observeAll(), simulacra.observeAll()) { all, attempts ->
            all.groupBy { it.courseId }.mapValues { (courseId, list) -> unused(list, attempts.filter { it.courseId == courseId }).size }
        }

    suspend fun all(courseId: String): List<Question> = questions.observeByCourse(courseId).first()

    companion object {
        fun unused(bank: List<Question>, attempts: List<Simulacrum>): List<Question> {
            val asked = attempts.flatMap { it.questionIds }.toSet()
            return bank.filter { !it.isReported && it.id !in asked }
        }

        /**
         * Failed first (wrong or unanswered the last time they were graded), then the rest; random order inside
         * each group.
         */
        fun reuseOrder(asked: List<Question>, attempts: List<Simulacrum>, random: Random): List<Question> {
            val lastResult = mutableMapOf<String, Boolean>()
            attempts.filter { it.isFinished }.sortedBy { it.startedAt }.forEach { attempt ->
                attempt.questionIds.forEach { id -> lastResult[id] = attempt.answers.any { it.questionId == id && it.correct } }
            }
            val (failed, rest) = asked.partition { lastResult[it.id] == false }
            return failed.shuffled(random) + rest.shuffled(random)
        }
    }
}

/**
 * Generates [count] new questions for a course from its material (or typed [topics]) in one Gemini call and stores
 * them, with the hash of the material they came from. Only text reaches Gemini.
 */
class GenerateCourseQuestionsUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val courses: CourseRepository,
    private val material: CourseMaterialUseCase,
    private val generateSet: GenerateQuestionSetUseCase,
    private val usage: AiUsageStore,
    private val clock: Clock,
    private val logger: AiLogger,
) {
    suspend operator fun invoke(courseId: String, count: Int, topics: String? = null): Result<List<Question>> {
        val started = clock.millis()
        val courseMaterial = material(courseId)
        val typed = topics?.trim().orEmpty()
        if (typed.length < CourseMaterial.MIN_SYLLABUS_CHARS && courseMaterial.isInsufficient) {
            return Result.failure(MissingSyllabusException())
        }
        val text = if (typed.length >= CourseMaterial.MIN_SYLLABUS_CHARS) typed else courseMaterial.text
        val courseName = courses.observeById(courseId).firstOrNull()?.name.orEmpty()
        val existing = questions.observeByCourse(courseId).first().map { q -> listOfNotNull(q.scenario, q.statement).joinToString(" ") }
        val request = QuestionRequest(courseName, text, count, pdfMarkdown = courseMaterial.pdfMarkdown)
        logger.info(
            "Question material: course=$courseId syllabusChars=${text.length} pdfChars=${request.pdfMarkdown.length} " +
                "sentChars=${text.length + request.pdfMarkdown.length} in ${clock.millis() - started} ms"
        )
        return generateSet(request, existing).mapCatching { generated ->
            val now = clock.instant()
            val created = generated.map {
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
            if (created.isNotEmpty()) usage.setMaterialHash(courseId, courseMaterial.hashWith(topics))
            logger.info("Questions ready: course=$courseId count=${created.size} in ${clock.millis() - started} ms")
            created
        }
    }
}

/**
 * HU-15, used only when the bank cannot even start an attempt: generates the whole bank now (one call), since the
 * student is waiting. At least [SendaRules.MIN_QUESTIONS_TO_START] valid questions are required.
 */
class PrepareQuestionsUseCase @Inject constructor(private val generate: GenerateCourseQuestionsUseCase) {
    suspend operator fun invoke(courseId: String, topics: String? = null): Result<List<Question>> =
        generate(courseId, SendaRules.QUESTION_BANK_TARGET, topics).mapCatching { created ->
            if (created.size < SendaRules.MIN_QUESTIONS_TO_START) {
                throw NoValidQuestionsException(SendaRules.QUESTION_BANK_TARGET, created.size)
            }
            created
        }
}

/**
 * Background refill of a course bank. It calls Gemini only when the material changed since the bank was made
 * (hash of syllabus + PDF Markdown), or when fewer than [SendaRules.LOW_BANK_UNUSED] unused questions are left.
 */
class RefillQuestionBankUseCase @Inject constructor(
    private val bank: QuestionBankUseCase,
    private val material: CourseMaterialUseCase,
    private val generate: GenerateCourseQuestionsUseCase,
    private val usage: AiUsageStore,
    private val logger: AiLogger,
) {
    suspend operator fun invoke(courseId: String, topics: String? = null): Result<Int> {
        val unused = bank.unused(courseId).size
        val changed = material(courseId).hashWith(topics) != usage.materialHash(courseId)
        if (!changed && unused >= SendaRules.LOW_BANK_UNUSED) {
            logger.info("Refill skipped for course $courseId: $unused unused questions and the same material")
            return Result.success(0)
        }
        logger.info("Refill for course $courseId: unused=$unused materialChanged=$changed")
        return generate(courseId, SendaRules.QUESTION_BANK_TARGET, topics).map { it.size }
    }
}

/** Serializes read-modify-write of attempts: answers and finishing. */
object SimulacrumWrites {
    val mutex = Mutex()

    /** [mine] with any question ids that were appended to the stored copy meanwhile. */
    fun merge(mine: Simulacrum, stored: Simulacrum?): Simulacrum {
        if (stored == null) return mine
        val ids = stored.questionIds + mine.questionIds.filterNot { it in stored.questionIds }
        return mine.copy(questionIds = ids)
    }
}

/**
 * HU-17 feedback: a reported question leaves the bank (it stays visible in past results). The bank is refilled
 * only when few unused questions are left.
 */
class ReportQuestionUseCase @Inject constructor(
    private val questions: QuestionRepository,
    private val bank: QuestionBankUseCase,
    private val prefetcher: QuestionPrefetcher,
    private val logger: AiLogger,
) {
    suspend operator fun invoke(question: Question, reason: QuestionReport): Result<Unit> {
        if (question.isReported) return Result.success(Unit)
        logger.info("Question reported: reason=$reason course=${question.courseId}")
        // The stored copy: the attempt may show the options in another order.
        val stored = questions.observeById(question.id).firstOrNull() ?: question
        return questions.save(stored.copy(reportReason = reason)).onSuccess {
            if (bank.unused(question.courseId).size < SendaRules.LOW_BANK_UNUSED) prefetcher.refill(question.courseId)
        }
    }
}

/** One background generation job, as the WorkManager worker receives it. */
data class PrefetchJob(val courseId: String, val topics: String? = null)

enum class PrefetchOutcome { SUCCESS, RETRY, FAIL }

/**
 * Logic of the prefetch worker. It does nothing once today's calls reach the background limit or while the AI
 * rests (daily quota): those calls are kept for the student, and attempts keep coming from the bank. Errors that
 * more attempts can fix (network, timeouts, overload, too few valid questions) are retried with backoff; missing
 * material, an invalid API key or a signed-out student are final.
 */
class RunPrefetchUseCase @Inject constructor(
    private val refill: RefillQuestionBankUseCase,
    private val quota: AiQuota,
    private val logger: AiLogger,
) {
    suspend operator fun invoke(job: PrefetchJob): PrefetchOutcome {
        if (!quota.allowsBackground()) {
            logger.info("Prefetch skipped for course ${job.courseId}: AI budget kept for the student today")
            return PrefetchOutcome.SUCCESS
        }
        val result = refill(job.courseId, job.topics)
        val error = result.exceptionOrNull() ?: return PrefetchOutcome.SUCCESS.also {
            logger.info("Prefetch done for course ${job.courseId}: +${result.getOrNull()} questions")
        }
        if (error is AiError.Resting || error is AiError.FreeLimitReached) {
            logger.info("Prefetch stopped for course ${job.courseId}: daily quota used up, no retries")
            return PrefetchOutcome.SUCCESS
        }
        val final = error is MissingSyllabusException ||
            (error is AiError.Configuration && error.invalidKey) ||
            error is AuthError
        logger.warn("Prefetch ${if (final) "failed" else "will retry"}: ${error.javaClass.simpleName}: ${error.message}")
        return if (final) PrefetchOutcome.FAIL else PrefetchOutcome.RETRY
    }
}
