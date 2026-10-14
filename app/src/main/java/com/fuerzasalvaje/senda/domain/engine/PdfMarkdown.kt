package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.FittedMarkdown
import com.fuerzasalvaje.senda.domain.model.PdfText
import kotlin.math.ceil

/**
 * What the AI reads instead of the PDF (Gemini never receives the file): Markdown made on the device with one
 * "## Página N" or "## Diapositiva N" section per page, its lines as bullets, formulas and numbers as they appear.
 */
object PdfMarkdown {
    /** Most text the AI receives from one PDF. */
    const val MAX_CHARS = 12_000

    private const val PAGE = "Página"
    private const val SLIDE = "Diapositiva"

    /** A line on at least this share of the pages (and on 3 or more) is a running header or footer. */
    private const val REPEATED_SHARE = 0.4
    private const val MIN_REPEATS = 3

    // A dash is a bullet only when a space follows it, so "-5 °C" keeps its sign.
    private val bulletMarker = Regex("^(?:[•·▪▫■□●○◦►▶➢➤✓✔]+|[-*–—]+(?=\\s))\\s*")
    private val spaces = Regex("\\s+")
    private val pageNumber = Regex("^(?:(?:página|pagina|pág\\.?|pag\\.?|page|diapositiva|slide)\\s*)?\\d{1,4}(?:\\s*(?:de|/|of)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)
    private val heading = Regex("^##\\s+(?:$PAGE|$SLIDE)\\s+\\d+\\s*$")
    private val formulaChars = setOf('=', '+', '×', '÷', '/', '^', '√', '∑', 'Σ', '∫', '≤', '≥', '≈', '±', 'π', 'Δ', '%', '²', '³')

    /** Unit name the questions cite: "diap." for slides, "pág." for pages. */
    fun sectionTitle(slides: Boolean): String = if (slides) SLIDE else PAGE

    /** One section per page with text; pages without text are left out. */
    fun convert(text: PdfText): String {
        val title = sectionTitle(text.slides)
        return text.pages.mapIndexedNotNull { index, page ->
            val lines = page.lines().map(::cleanLine).filter { it.isNotEmpty() }
            if (lines.isEmpty()) null
            else "## $title ${index + 1}\n" + lines.joinToString("\n") { "- $it" }
        }.joinToString("\n\n")
    }

    /**
     * Keeps [markdown] within [maxChars]. First drops repeated lines, running headers and footers, page numbers and
     * extra spaces; if it still does not fit, keeps the sections with the most numbers, formulas and [syllabus]
     * keywords, in their original order.
     */
    fun fit(markdown: String, syllabus: String = "", maxChars: Int = MAX_CHARS): FittedMarkdown {
        if (markdown.length <= maxChars) return FittedMarkdown(markdown, summarized = false)
        val sections = clean(parse(markdown))
        val cleaned = render(sections)
        if (cleaned.length <= maxChars) return FittedMarkdown(cleaned, summarized = true)

        val keywords = QuestionQuality.keywords(syllabus)
        val ranked = sections.indices.sortedWith(
            compareByDescending<Int> { score(sections[it], keywords) }.thenBy { it }
        )
        val kept = mutableSetOf<Int>()
        var used = 0
        for (index in ranked) {
            val size = render(listOf(sections[index])).length + SEPARATOR.length
            if (used + size <= maxChars) {
                kept += index
                used += size
            }
        }
        val text = render(sections.filterIndexed { index, _ -> index in kept }).ifEmpty { cleaned }
        return FittedMarkdown(text.take(maxChars).trimEnd(), summarized = true)
    }

    private data class Section(val heading: String?, val lines: List<String>)

    private const val SEPARATOR = "\n\n"

    private fun cleanLine(line: String): String = line.replace(spaces, " ").trim().replace(bulletMarker, "").trim()

    private fun parse(markdown: String): List<Section> {
        val sections = mutableListOf<Section>()
        var currentHeading: String? = null
        var lines = mutableListOf<String>()
        markdown.lines().forEach { raw ->
            val line = raw.replace(spaces, " ").trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") -> {
                    if (currentHeading != null || lines.isNotEmpty()) sections += Section(currentHeading, lines)
                    currentHeading = line
                    lines = mutableListOf()
                }
                else -> lines += line.removePrefix("- ").trim()
            }
        }
        if (currentHeading != null || lines.isNotEmpty()) sections += Section(currentHeading, lines)
        return sections
    }

    /** Drops page numbers, lines repeated inside a section and lines that repeat across many pages. */
    private fun clean(sections: List<Section>): List<Section> {
        val pages = sections.count { it.heading != null && heading.matches(it.heading) }.coerceAtLeast(1)
        val counts = sections.flatMap { section -> section.lines.map(::footerKey).distinct() }.groupingBy { it }.eachCount()
        val threshold = maxOf(MIN_REPEATS, ceil(pages * REPEATED_SHARE).toInt())
        return sections.map { section ->
            val lines = section.lines
                .filterNot { pageNumber.matches(it) }
                .filterNot { (counts[footerKey(it)] ?: 0) >= threshold }
                .distinct()
            section.copy(lines = lines)
        }.filter { it.lines.isNotEmpty() }
    }

    /**
     * Short footers often differ only by the page number ("Semana 3 - 12"), so their digits are ignored. Longer
     * lines must repeat exactly: "Tema 7: el móvil recorre 70 m" is content, not a footer.
     */
    private fun footerKey(line: String): String {
        val lower = line.lowercase()
        return if (lower.split(' ').size <= MAX_FOOTER_WORDS) lower.replace(Regex("\\d+"), "#") else lower
    }

    private const val MAX_FOOTER_WORDS = 4

    private fun score(section: Section, keywords: Set<String>): Int {
        val text = section.lines.joinToString(" ")
        val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(text).count()
        val formulas = text.count { it in formulaChars }
        val stems = QuestionQuality.stems(text)
        val hits = keywords.count { it in stems }
        return numbers + formulas * 2 + hits * 3
    }

    private fun render(sections: List<Section>): String = sections.joinToString(SEPARATOR) { section ->
        listOfNotNull(section.heading).plus(section.lines.map { "- $it" }).joinToString("\n")
    }
}
