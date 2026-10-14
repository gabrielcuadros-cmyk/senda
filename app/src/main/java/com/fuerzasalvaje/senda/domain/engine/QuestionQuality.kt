package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import java.text.Normalizer
import kotlin.random.Random

/**
 * Automatic quality checks for AI questions (HU-15): near-duplicates, relation to the syllabus,
 * "all/none of the above" options and the balance of the correct answer position.
 */
object QuestionQuality {

    /** Two statements this similar (word overlap) are the same question. */
    const val DUPLICATE_SIMILARITY = 0.8

    /** Keyword matching uses the first letters of each word, so "derivadas" matches "derivada". */
    private const val STEM_LENGTH = 5
    private const val MIN_KEYWORD_LENGTH = 5

    /** Below this many keywords the syllabus is too short to judge relevance. */
    const val MIN_KEYWORDS_FOR_RELEVANCE = 2

    private val catchAll = listOf("todas las anteriores", "ninguna de las anteriores", "todas las opciones", "ninguna de las opciones")

    private val stopwords = setOf(
        "sobre", "entre", "desde", "hasta", "donde", "cuando", "porque", "cual", "cuales", "como", "para", "segun",
        "tiene", "tienen", "puede", "pueden", "debe", "deben", "estos", "estas", "otros", "otras", "todos", "todas",
        "unidad", "tema", "temas", "semana", "examen", "parcial", "final", "capitulo", "clase", "clases", "laboratorio",
        "guia", "practica", "incluye", "incluyen", "parte", "partes", "general", "basico", "basica", "introduccion",
    )

    /** Lowercase, no accents, letters and digits only, single spaces. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9ñ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun words(text: String): Set<String> = normalize(text).split(' ').filter { it.isNotEmpty() }.toSet()

    /** Jaccard similarity of the statements' words, 0 to 1. */
    fun similarity(a: String, b: String): Double {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() && wb.isEmpty()) return 1.0
        return (wa intersect wb).size.toDouble() / (wa union wb).size
    }

    private fun fullStatement(question: GeneratedQuestion) =
        listOfNotNull(question.scenario, question.statement).joinToString(" ")

    fun isDuplicate(statement: String, others: Collection<String>): Boolean {
        val normalized = normalize(statement)
        return others.any { normalize(it) == normalized || similarity(statement, it) >= DUPLICATE_SIMILARITY }
    }

    /** Keeps the first of each group of duplicated questions, also against the statements already in the bank. */
    fun dedupe(questions: List<GeneratedQuestion>, existing: Collection<String> = emptyList()): List<GeneratedQuestion> {
        val seen = existing.toMutableList()
        return questions.filter { question ->
            val statement = fullStatement(question)
            if (isDuplicate(statement, seen)) false else {
                seen += statement
                true
            }
        }
    }

    /** Distinct stems of the meaningful words of the syllabus. */
    fun keywords(syllabus: String): Set<String> =
        words(syllabus)
            .filter { it.length >= MIN_KEYWORD_LENGTH && it !in stopwords && it.any(Char::isLetter) }
            .map { it.take(STEM_LENGTH) }
            .toSet()

    /** True when any syllabus keyword appears in the question, its options or its explanation. */
    fun isRelated(question: GeneratedQuestion, keywords: Set<String>): Boolean {
        if (keywords.size < MIN_KEYWORDS_FOR_RELEVANCE) return true
        val text = words(listOfNotNull(question.scenario, question.statement, question.explanation).joinToString(" ") + " " +
            question.options.joinToString(" "))
        val stems = text.map { it.take(STEM_LENGTH) }.toSet()
        return keywords.any { it in stems }
    }

    fun isCatchAll(option: String): Boolean = catchAll.any { normalize(option).contains(it) }

    /** True when more than half of the batch has the correct answer in the same position. */
    fun isUnbalanced(batch: List<GeneratedQuestion>): Boolean {
        if (batch.size < 2) return false
        val top = batch.groupingBy { it.correctOptionIndex }.eachCount().values.maxOrNull() ?: 0
        return top * 2 > batch.size
    }

    /**
     * Spreads the correct answers over A/B/C/D: each question gets a target position from a shuffled cycle
     * and its correct option is swapped there. Options keep their text, so explanations stay valid.
     */
    fun rebalance(batch: List<GeneratedQuestion>, random: Random = Random.Default): List<GeneratedQuestion> {
        if (!isUnbalanced(batch)) return batch
        val targets = List(batch.size) { it % QuestionValidator.OPTION_COUNT }.shuffled(random)
        return batch.zip(targets).map { (question, target) -> moveCorrect(question, target) }
    }

    fun moveCorrect(question: GeneratedQuestion, target: Int): GeneratedQuestion {
        val from = question.correctOptionIndex
        if (from == target || target !in question.options.indices || from !in question.options.indices) return question
        val options = question.options.toMutableList()
        options[from] = options[target].also { options[target] = options[from] }
        return question.copy(options = options, correctOptionIndex = target)
    }
}
