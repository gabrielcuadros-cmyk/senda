package com.fuerzasalvaje.senda.ui

import com.fuerzasalvaje.senda.testing.FakeStudyBlockRepository
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.fuerzasalvaje.senda.domain.engine.ValidationError
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCoursesUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.DeleteEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ObserveEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.pdf.ExtractPdfMarkdownUseCase
import com.fuerzasalvaje.senda.domain.model.PdfText
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.testing.FakePdfTextExtractor
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeDocumentStorage
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.MainDispatcherRule
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.testing.testClock
import com.fuerzasalvaje.senda.ui.evaluation.EvaluationFormUiState
import com.fuerzasalvaje.senda.ui.evaluation.EvaluationFormViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EvaluationFormViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val courses = FakeCourseRepository().apply { seed(course()) }
    private val evaluations = FakeEvaluationRepository()
    private val storage = FakeDocumentStorage()
    private val extractor = FakePdfTextExtractor()

    private fun viewModel(args: Map<String, Any?> = emptyMap()) = EvaluationFormViewModel(
        savedStateHandle = SavedStateHandle(args),
        observeCourses = ObserveCoursesUseCase(courses),
        observeEvaluation = ObserveEvaluationUseCase(evaluations),
        saveEvaluation = SaveEvaluationUseCase(evaluations),
        deleteEvaluation = DeleteEvaluationUseCase(evaluations, storage, FakeStudyBlockRepository()),
        storage = storage,
        extractPdf = ExtractPdfMarkdownUseCase(extractor, FakeAiLogger(), testClock),
        clock = testClock,
    )

    private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `a valid new exam is saved with its local date and time`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.courseId == "c1" }
            vm.onTitle("Parcial de Física")
            vm.onDate(LocalDate.of(2026, 10, 14))
            vm.onStartTime(LocalTime.of(10, 0))
            vm.onWeight("30")
            vm.save()
            awaitUntil { it.finished }
            cancelAndIgnoreRemainingEvents()
        }

        val saved = evaluations.items.value.values.single() as Exam
        assertEquals("Parcial de Física", saved.title)
        assertEquals(30.0, saved.weightPercent, 0.0)
        assertEquals(LocalDate.of(2026, 10, 14).atTime(10, 0).atZone(TEST_ZONE).toInstant(), saved.startAt)
        assertEquals(6.0, saved.studyHours, 0.0)
    }

    @Test
    fun `invalid input shows errors and saves nothing`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.courseId == "c1" }
            vm.onWeight("0")
            vm.save()
            val state = awaitUntil(EvaluationFormUiState::showErrors)
            val errors = state.errors(TEST_ZONE)
            assertTrue(ValidationError.TITLE_REQUIRED in errors)
            assertTrue(ValidationError.WEIGHT_INVALID in errors)
            assertTrue(ValidationError.DATE_REQUIRED in errors)
            assertEquals(ValidationError.WEIGHT_INVALID.message, state.visibleError(ValidationError.WEIGHT_INVALID, TEST_ZONE))
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(evaluations.saved.isEmpty())
    }

    @Test
    fun `difficulty updates suggested hours until the student edits them`() = runTest {
        val vm = viewModel()
        vm.onDifficulty(5)
        assertEquals(10.0, vm.state.value.studyHours, 0.0)
        vm.incrementHours()
        vm.onDifficulty(1)
        assertEquals(10.5, vm.state.value.studyHours, 0.0)
        assertEquals(2.0, vm.state.value.suggestedHours, 0.0)
        vm.useSuggestedHours()
        assertEquals(2.0, vm.state.value.studyHours, 0.0)
    }

    @Test
    fun `labs start with a 10 percent weight`() = runTest {
        val vm = viewModel(mapOf("type" to EvaluationType.LAB.name))
        assertEquals(EvaluationType.LAB, vm.state.value.type)
        assertEquals("10", vm.state.value.weight)
    }

    @Test
    fun `course weight above 100 percent shows a warning`() = runTest {
        evaluations.seed(exam("a", 10, weight = 90.0))
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.courseId == "c1" }
            vm.onWeight("20")
            assertEquals(110.0, awaitUntil { it.courseWeightTotal != null }.courseWeightTotal!!, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the date of an existing exam asks to reschedule`() = runTest {
        evaluations.seed(exam("e1", inHours = 26, weight = 25.0))
        val vm = viewModel(mapOf("evaluationId" to "e1"))
        vm.state.test {
            awaitUntil { !it.loading && it.title == "e1" }
            vm.onDate(LocalDate.of(2026, 10, 20))
            vm.save()
            val asking = awaitUntil { it.reschedule != null }
            assertNotNull(asking.reschedule?.from)
            assertTrue(evaluations.saved.isEmpty())

            vm.confirmReschedule()
            awaitUntil { it.finished }
            cancelAndIgnoreRemainingEvents()
        }
        val saved = evaluations.items.value.getValue("e1") as Exam
        assertEquals(LocalDate.of(2026, 10, 20), saved.startAt.atZone(TEST_ZONE).toLocalDate())
    }

    @Test
    fun `deleting removes the evaluation and its pdf`() = runTest {
        val lab = Lab(
            id = "l1", courseId = "c1", title = "Lab", difficulty = 2, createdAt = testClock.instant(),
            dueAt = testClock.instant(), guidePdfPath = "/files/pdfs/guia.pdf",
        )
        evaluations.seed(lab)
        val vm = viewModel(mapOf("evaluationId" to "l1"))
        vm.state.test {
            awaitUntil { !it.loading && it.isEdit && it.title == "Lab" }
            vm.requestDelete()
            vm.confirmDelete()
            awaitUntil { it.finished }
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(evaluations.items.value.isEmpty())
        assertEquals(listOf("/files/pdfs/guia.pdf"), storage.deleted)
    }

    @Test
    fun `an attached PDF becomes editable Markdown that is saved with the exam`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.courseId == "c1" }
            vm.onTitle("Parcial de Electricidad")
            vm.onDate(LocalDate.of(2026, 10, 14))
            vm.onWeight("30")
            vm.onPdfPicked("content://temario.pdf")
            val read = awaitUntil { it.pdfMarkdown.isNotBlank() && !it.extractingPdf }
            assertTrue(read.pdfMarkdown.startsWith("## Diapositiva 1\n- Ley de Ohm\n- La tensión es V = I x R"))
            assertFalse(read.pdfNoText)
            vm.onPdfMarkdown(read.pdfMarkdown + "\n- Nota del alumno: repasar potencia")
            vm.save()
            awaitUntil { it.finished }
            cancelAndIgnoreRemainingEvents()
        }

        val saved = evaluations.items.value.values.single() as Exam
        assertEquals("/files/pdfs/copy.pdf", saved.syllabusPdfPath)
        assertTrue(saved.syllabusPdfMarkdown.contains("## Diapositiva 2\n- Caso de estudio"))
        assertTrue(saved.syllabusPdfMarkdown.endsWith("- Nota del alumno: repasar potencia"))
    }

    @Test
    fun `a PDF without readable text is flagged and leaves no AI content`() = runTest {
        extractor.result = Result.success(PdfText(listOf("", "3")))
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.courseId == "c1" }
            vm.onPdfPicked("content://escaneado.pdf")
            val state = awaitUntil { it.pdfNoText }
            assertEquals("", state.pdfMarkdown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-extracting replaces the edited text, and removing the PDF clears it`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.courseId == "c1" }
            vm.onPdfPicked("content://temario.pdf")
            awaitUntil { it.pdfMarkdown.isNotBlank() && !it.extractingPdf }
            vm.onPdfMarkdown("texto roto")
            vm.reextractPdf()
            awaitUntil { it.pdfMarkdown.startsWith("## Diapositiva 1") && !it.extractingPdf }
            vm.removePdf()
            val cleared = awaitUntil { it.pdfPath == null }
            assertEquals("", cleared.pdfMarkdown)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, extractor.calls.size)
    }
}
