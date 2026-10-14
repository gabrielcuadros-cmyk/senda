package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.PdfText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMarkdownTest {

    // Conversion

    @Test
    fun `each slide becomes a section with its lines as bullets, formulas and numbers untouched`() {
        val text = PdfText(
            listOf(
                "Ley de Ohm\n• La tensión es  V = I × R\n▪ R = 4 Ω, V = 12 V",
                "",
                "Temperatura mínima: -5 °C\n- Δx = 2,5 m\n  ",
            ),
            slides = true,
        )

        val markdown = PdfMarkdown.convert(text)

        assertEquals(
            "## Diapositiva 1\n- Ley de Ohm\n- La tensión es V = I × R\n- R = 4 Ω, V = 12 V\n\n" +
                "## Diapositiva 3\n- Temperatura mínima: -5 °C\n- Δx = 2,5 m",
            markdown,
        )
    }

    @Test
    fun `a document gets page sections`() {
        val markdown = PdfMarkdown.convert(PdfText(listOf("Introducción", "Capítulo 1")))
        assertEquals("## Página 1\n- Introducción\n\n## Página 2\n- Capítulo 1", markdown)
    }

    @Test
    fun `presentations are detected by their landscape pages`() {
        assertTrue(PdfText.of(listOf("a", "b", "c"), landscapePages = 2).slides)
        assertFalse(PdfText.of(listOf("a", "b"), landscapePages = 1).slides)
    }

    // PDFs without text

    @Test
    fun `a PDF with fewer than 200 characters of text has no readable text`() {
        assertFalse(PdfText(emptyList()).hasReadableText)
        assertFalse(PdfText(listOf("   ", "Página 1", "x".repeat(150))).hasReadableText)
        assertFalse(PdfText(listOf("x".repeat(199))).hasReadableText)
        assertTrue(PdfText(listOf("x".repeat(120), "y".repeat(80))).hasReadableText)
    }

    // Fitting into 12 000 characters

    @Test
    fun `text within the limit is kept as it is`() {
        val markdown = "## Diapositiva 1\n- v = d / t"
        assertEquals(FittedMarkdownCheck(markdown, false), PdfMarkdown.fit(markdown).let { FittedMarkdownCheck(it.text, it.summarized) })
    }

    @Test
    fun `repeated lines, running headers and footers and page numbers go first`() {
        val pages = (1..40).map { n ->
            listOf(
                "Tecsup - Física I - Semana 5",
                "Tema $n: el móvil recorre ${n * 10} m en $n s",
                "Tema $n: el móvil recorre ${n * 10} m en $n s",
                "Pág. $n de 40",
                "$n",
            ).joinToString("\n")
        }
        val markdown = PdfMarkdown.convert(PdfText(pages))
        val limit = markdown.length - 1

        val fitted = PdfMarkdown.fit(markdown, maxChars = limit)

        assertTrue(fitted.summarized)
        assertTrue(fitted.text.length <= limit)
        assertFalse(fitted.text.contains("Tecsup - Física I"))
        assertFalse(fitted.text.contains("Pág. "))
        assertTrue((1..40).all { fitted.text.contains("## Página $it\n- Tema $it: el móvil recorre ${it * 10} m en $it s") })
        assertEquals(1, Regex("Tema 7: ").findAll(fitted.text).count())
    }

    @Test
    fun `a long PDF is cut to 12000 characters keeping pages with numbers, formulas and syllabus words`() {
        val filler = (1..200).map { n -> "Página de lectura $n con texto general sobre la historia del curso y su contexto académico." }
        val key = listOf(
            "Ley de Ohm: V = I × R con R = 4 Ω y V = 12 V",
            "Potencia eléctrica P = V × I = 36 W",
            "Caso: circuito en serie con resistencias de 2 Ω, 3 Ω y 5 Ω",
        )
        val pages = filler.take(100) + key + filler.drop(100)
        val markdown = PdfMarkdown.convert(PdfText(pages))
        assertTrue(markdown.length > PdfMarkdown.MAX_CHARS)

        val fitted = PdfMarkdown.fit(markdown, syllabus = "Ley de Ohm, potencia eléctrica y circuitos en serie")

        assertTrue(fitted.summarized)
        assertTrue(fitted.text.length <= PdfMarkdown.MAX_CHARS)
        key.forEach { assertTrue(it, fitted.text.contains(it)) }
        // Pages keep their original order and their headings, so questions can cite them.
        val first = fitted.text.indexOf("## Página 101")
        assertTrue(first >= 0 && first < fitted.text.indexOf("## Página 103"))
    }

    @Test
    fun `a single huge page is cut instead of dropped`() {
        val markdown = PdfMarkdown.convert(PdfText(listOf((1..3000).joinToString("\n") { "Dato $it = ${it * 2}" })))

        val fitted = PdfMarkdown.fit(markdown)

        assertTrue(fitted.summarized)
        assertTrue(fitted.text.startsWith("## Página 1\n- Dato 1 = 2"))
        assertTrue(fitted.text.length <= PdfMarkdown.MAX_CHARS)
    }

    private data class FittedMarkdownCheck(val text: String, val summarized: Boolean)
}
