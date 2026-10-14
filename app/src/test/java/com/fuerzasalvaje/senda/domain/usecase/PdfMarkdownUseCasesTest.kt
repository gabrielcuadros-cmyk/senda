package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.engine.PdfMarkdown
import com.fuerzasalvaje.senda.domain.model.PdfText
import com.fuerzasalvaje.senda.domain.repository.PdfTextExtractor
import com.fuerzasalvaje.senda.domain.usecase.pdf.ExtractPdfMarkdownUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.PdfExtraction
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.testing.testClock
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * PDF to Markdown with real test PDFs. They are built and read with desktop PDFBox, the same API as PdfBox-Android
 * (which needs an Android Context), and go through the same [PdfText] rules as the app's extractor.
 */
class PdfMarkdownUseCasesTest {

    @get:Rule val folder = TemporaryFolder()

    private val logger = FakeAiLogger()

    /** Reads a PDF the way PdfBoxTextExtractor does: text per page, landscape pages mean slides. */
    private val desktopExtractor = object : PdfTextExtractor {
        override suspend fun extract(path: String): Result<PdfText> = runCatching {
            PDDocument.load(File(path)).use { document ->
                val stripper = PDFTextStripper()
                val pages = (1..document.numberOfPages).map { page ->
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document)
                }
                PdfText.of(pages, document.pages.count { it.mediaBox.width > it.mediaBox.height })
            }
        }
    }

    private val extract = ExtractPdfMarkdownUseCase(desktopExtractor, logger, testClock)

    /** A slide deck: one landscape page per slide, each line written as text. */
    private fun slideDeck(vararg slides: List<String>): String {
        val file = folder.newFile("clase.pdf")
        PDDocument().use { document ->
            slides.forEach { lines ->
                val page = PDPage(PDRectangle(792f, 612f))
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, 18f)
                    content.setLeading(26f)
                    content.newLineAtOffset(60f, 540f)
                    lines.forEach { line ->
                        content.showText(line)
                        content.newLine()
                    }
                    content.endText()
                }
            }
            document.save(file)
        }
        return file.path
    }

    /** A scanned document: pages with drawings and no text layer. */
    private fun scannedPdf(): String {
        val file = folder.newFile("escaneado.pdf")
        PDDocument().use { document ->
            repeat(3) {
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.addRect(50f, 50f, 400f, 600f)
                    content.fill()
                }
            }
            document.save(file)
        }
        return file.path
    }

    @Test
    fun `a slide deck PDF becomes Markdown with one section per slide`() = runTest {
        val path = slideDeck(
            listOf("Ley de Ohm", "La tension es V = I x R", "Unidades: voltio, amperio y ohmio"),
            listOf("Caso de estudio", "Resistencia de 4 ohm conectada a 12 V", "Corriente I = 12 / 4 = 3 A"),
            listOf("Potencia", "P = V x I = 36 W", "Temperatura de trabajo: -5 a 40 grados"),
        )

        val result = extract(path) as PdfExtraction.Ready

        assertEquals(
            "## Diapositiva 1\n- Ley de Ohm\n- La tension es V = I x R\n- Unidades: voltio, amperio y ohmio\n\n" +
                "## Diapositiva 2\n- Caso de estudio\n- Resistencia de 4 ohm conectada a 12 V\n- Corriente I = 12 / 4 = 3 A\n\n" +
                "## Diapositiva 3\n- Potencia\n- P = V x I = 36 W\n- Temperatura de trabajo: -5 a 40 grados",
            result.markdown,
        )
        assertEquals(false, result.summarized)
    }

    @Test
    fun `a long PDF is shortened to 12000 characters and flagged`() = runTest {
        val slides = (1..80).map { n ->
            listOf(
                "Tema $n de la unidad de electricidad basica",
                "Resistencia R$n = ${n * 2} ohm con tension de ${n * 3} V",
                "Corriente calculada I$n = ${n * 3} / ${n * 2} A segun la ley de Ohm",
                "Texto de apoyo con explicaciones generales del docente y ejemplos",
                "Revisar la guia de laboratorio y las lecturas recomendadas",
            )
        }
        val path = slideDeck(*slides.toTypedArray())

        val result = extract(path, syllabus = "Ley de Ohm y resistencias") as PdfExtraction.Ready

        assertTrue(result.summarized)
        assertTrue(result.markdown.length <= PdfMarkdown.MAX_CHARS)
        assertTrue(result.markdown.startsWith("## Diapositiva"))
    }

    @Test
    fun `a scanned PDF has no readable text and nothing is produced`() = runTest {
        val result = extract(scannedPdf())

        assertEquals(PdfExtraction.NoText, result)
        assertTrue(logger.lines.any { it.contains("PDF without readable text: pages=3") })
    }

    @Test
    fun `the log has pages, characters and time, never the content`() = runTest {
        val path = slideDeck(
            listOf("Ley de Ohm", "La tension es V = I x R", "Unidades: voltio, amperio y ohmio y sus multiplos"),
            listOf("Caso de estudio", "Resistencia de 4 ohm conectada a 12 V", "Corriente I = 12 / 4 = 3 A en el circuito"),
            listOf("Potencia", "P = V x I = 36 W en la resistencia del caso", "Energia en una hora: 36 Wh"),
        )

        extract(path)

        val line = logger.lines.single { it.contains("PDF to Markdown") }
        listOf("pages=3", "slides=true", "extractedChars=", "markdownChars=", "sentChars=", " ms").forEach {
            assertTrue(it, line.contains(it))
        }
        assertTrue(logger.lines.none { it.contains("Ohm") || it.contains("V = I x R") })
    }

    @Test
    fun `a file that cannot be read fails without crashing`() = runTest {
        val broken = folder.newFile("roto.pdf").apply { writeText("no es un pdf") }

        assertTrue(extract(broken.path) is PdfExtraction.Failed)
        assertTrue(logger.lines.any { it.startsWith("W PDF text extraction failed") })
    }
}
