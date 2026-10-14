package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PdfDigestStatus
import com.fuerzasalvaje.senda.domain.model.PdfText
import com.fuerzasalvaje.senda.domain.model.courseDigestStatus
import com.fuerzasalvaje.senda.domain.model.pdfKeyOf
import com.fuerzasalvaje.senda.domain.usecase.evaluation.DeleteEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.PdfDigestOutcome
import com.fuerzasalvaje.senda.domain.usecase.pdf.RunPdfDigestUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.TrackPdfDigestUseCase
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeDocumentStorage
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeGeminiService
import com.fuerzasalvaje.senda.testing.FakePdfDigestRepository
import com.fuerzasalvaje.senda.testing.FakePdfDigestScheduler
import com.fuerzasalvaje.senda.testing.FakePdfTextExtractor
import com.fuerzasalvaje.senda.testing.FakeQuestionPrefetcher
import com.fuerzasalvaje.senda.testing.FakeStudyBlockRepository
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.testing.pdfDigest
import com.fuerzasalvaje.senda.testing.testClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration

class PdfDigestUseCasesTest {

    private val courses = FakeCourseRepository().apply { seed(course()) }
    private val evaluations = FakeEvaluationRepository()
    private val digests = FakePdfDigestRepository()
    private val scheduler = FakePdfDigestScheduler()
    private val extractor = FakePdfTextExtractor()
    private val gemini = FakeGeminiService()
    private val prefetcher = FakeQuestionPrefetcher()
    private val logger = FakeAiLogger()

    private val track = TrackPdfDigestUseCase(digests, scheduler, testClock)
    private val run = RunPdfDigestUseCase(evaluations, courses, digests, extractor, gemini, prefetcher, logger, testClock)

    private val pdfPath = "/data/files/pdfs/abc.pdf"
    private val withPdf = exam("e1", 30).copy(syllabus = "Circuitos", syllabusPdfPath = pdfPath)

    // Extraction and scanned detection

    @Test
    fun `text with page markers, and a PDF without text counts as scanned`() {
        val text = PdfText(listOf("Ley de Ohm: la tensión es V = I x R", "", "Caso: una resistencia de 4 ohm conectada a 12 V"))

        assertFalse(text.isScanned)
        assertEquals(3, text.pageCount)
        assertTrue(text.marked().startsWith("[Página 1]\nLey de Ohm"))
        assertTrue(text.marked().contains("[Página 3]\nCaso"))
        assertFalse(text.marked().contains("[Página 2]")) // empty pages are skipped
        assertTrue(PdfText(listOf(" ", "12", "")).isScanned)
        assertTrue(PdfText(emptyList()).isScanned)
        assertEquals(10, text.marked(maxChars = 10).length)
    }

    @Test
    fun `pdf key is the file name of the local copy`() {
        assertEquals("abc.pdf", pdfKeyOf("/data/files/pdfs/abc.pdf"))
        assertEquals("abc.pdf", pdfKeyOf("C:\\files\\abc.pdf"))
    }

    // Tracking

    @Test
    fun `attaching a PDF starts reading it, removing it drops the summary`() = runTest {
        val save = SaveEvaluationUseCase(evaluations, prefetcher, track)

        save(withPdf).getOrThrow()
        val reading = digests.items.value.getValue("e1")
        assertEquals(PdfDigestStatus.READING, reading.status)
        assertEquals("abc.pdf", reading.pdfKey)
        assertEquals(listOf("e1" to true), scheduler.scheduled)

        save(withPdf.copy(title = "Parcial 1")).getOrThrow() // same PDF: nothing new
        assertEquals(1, scheduler.scheduled.size)

        save(withPdf.copy(syllabusPdfPath = null)).getOrThrow()
        assertNull(digests.items.value["e1"])
    }

    @Test
    fun `deleting the evaluation deletes its summary`() = runTest {
        evaluations.seed(withPdf)
        digests.seed(pdfDigest("e1", "abc.pdf"))

        DeleteEvaluationUseCase(evaluations, FakeDocumentStorage(), FakeStudyBlockRepository(), track)(withPdf).getOrThrow()

        assertTrue(digests.items.value.isEmpty())
    }

    // Worker logic

    @Test
    fun `extracts once, summarizes the text, stores it and refreshes the questions`() = runTest {
        evaluations.seed(withPdf)

        assertEquals(PdfDigestOutcome.SUCCESS, run("e1"))

        val digest = digests.items.value.getValue("e1")
        assertEquals(PdfDigestStatus.READY, digest.status)
        assertEquals("## Fórmulas\n- V = I x R (diap. 3)", digest.summary)
        assertEquals(2, digest.pageCount)
        assertTrue(digest.extractedText!!.contains("[Página 2]"))
        val request = gemini.digestRequests.single()
        assertNull(request.pdfPath) // text, not the PDF
        assertTrue(request.text.contains("[Página 1]\nLey de Ohm"))
        assertEquals(listOf("c1" to true), prefetcher.refills)

        assertEquals(PdfDigestOutcome.SUCCESS, run("e1")) // already ready: no new call
        assertEquals(1, gemini.digestRequests.size)
        assertEquals(1, extractor.calls.size)
    }

    @Test
    fun `a scanned PDF is sent as a file once`() = runTest {
        evaluations.seed(withPdf)
        extractor.result = Result.success(PdfText(listOf("", " ")))

        assertEquals(PdfDigestOutcome.SUCCESS, run("e1"))

        val request = gemini.digestRequests.single()
        assertEquals(pdfPath, request.pdfPath)
        assertEquals("", request.text)
        assertTrue(digests.items.value.getValue("e1").scanned)
    }

    @Test
    fun `an unreadable PDF is treated as scanned instead of failing`() = runTest {
        evaluations.seed(withPdf)
        extractor.result = Result.failure(IOException("broken xref"))

        assertEquals(PdfDigestOutcome.SUCCESS, run("e1"))
        assertEquals(pdfPath, gemini.digestRequests.single().pdfPath)
    }

    @Test
    fun `a timeout is retried without extracting again, then it succeeds`() = runTest {
        evaluations.seed(withPdf)
        gemini.digestResult = Result.failure(AiError.Timeout())

        assertEquals(PdfDigestOutcome.RETRY, run("e1"))
        assertEquals(PdfDigestStatus.RETRYING, digests.items.value.getValue("e1").status)
        assertTrue(prefetcher.refills.isEmpty())

        gemini.digestResult = Result.success(com.fuerzasalvaje.senda.domain.model.PdfDigestResult("- Resumen (diap. 1)", "m"))
        assertEquals(PdfDigestOutcome.SUCCESS, run("e1"))
        assertEquals(PdfDigestStatus.READY, digests.items.value.getValue("e1").status)
        assertEquals(1, extractor.calls.size)
    }

    @Test
    fun `an invalid API key is final`() = runTest {
        evaluations.seed(withPdf)
        gemini.digestResult = Result.failure(AiError.Configuration("API key not valid"))

        assertEquals(PdfDigestOutcome.FAIL, run("e1"))
        assertEquals(PdfDigestStatus.FAILED, digests.items.value.getValue("e1").status)
    }

    @Test
    fun `a new PDF replaces the summary of the previous one`() = runTest {
        evaluations.seed(withPdf)
        digests.seed(pdfDigest("e1", "old.pdf", summary = "- Tema viejo (diap. 9)"))

        assertEquals(PdfDigestOutcome.SUCCESS, run("e1"))

        val digest = digests.items.value.getValue("e1")
        assertEquals("abc.pdf", digest.pdfKey)
        assertFalse(digest.summary.contains("Tema viejo"))
    }

    @Test
    fun `without a local PDF there is nothing to do`() = runTest {
        evaluations.seed(exam("e1", 30))

        assertEquals(PdfDigestOutcome.SUCCESS, run("e1"))
        assertTrue(gemini.digestRequests.isEmpty())
    }

    @Test
    fun `the log has sizes, time and model but never the PDF content`() = runTest {
        evaluations.seed(
            Lab(id = "e1", courseId = "c1", title = "Lab", difficulty = 2, createdAt = TEST_NOW,
                dueAt = TEST_NOW + Duration.ofDays(2), guidePdfPath = pdfPath),
        )

        run("e1")

        assertTrue(logger.lines.any { it.contains("PDF extracted") && it.contains("pages=2") && it.contains("chars=") && it.contains(" ms") })
        assertTrue(logger.lines.any { it.contains("PDF digest ready") && it.contains("model=gemini-test") })
        assertTrue(logger.lines.none { it.contains("Ohm") || it.contains("V = I x R") })
    }

    // Course status

    @Test
    fun `course status shows reading first, then retrying, failed, and ready only when all are ready`() {
        val ready = pdfDigest("a", "a.pdf")
        assertNull(courseDigestStatus(emptyList()))
        assertEquals(PdfDigestStatus.READY, courseDigestStatus(listOf(ready)))
        assertEquals(PdfDigestStatus.READING, courseDigestStatus(listOf(ready, pdfDigest("b", "b.pdf", PdfDigestStatus.READING))))
        assertEquals(PdfDigestStatus.RETRYING, courseDigestStatus(listOf(ready, pdfDigest("b", "b.pdf", PdfDigestStatus.RETRYING))))
        assertEquals(PdfDigestStatus.FAILED, courseDigestStatus(listOf(ready, pdfDigest("b", "b.pdf", PdfDigestStatus.FAILED))))
    }
}
