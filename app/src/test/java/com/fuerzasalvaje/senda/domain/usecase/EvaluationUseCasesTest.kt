package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.StudyBlockStatus
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.testing.FakeSimulacrumRepository
import com.fuerzasalvaje.senda.testing.FakeQuestionRepository
import com.fuerzasalvaje.senda.testing.FakeStudyBlockRepository
import com.fuerzasalvaje.senda.domain.engine.ValidationException
import com.fuerzasalvaje.senda.domain.model.ClassSlot
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.domain.usecase.course.DeleteCourseUseCase
import com.fuerzasalvaje.senda.domain.usecase.course.SaveCourseUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.ChangeEvaluationStatusUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.DeleteEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.RescheduleEvaluationUseCase
import com.fuerzasalvaje.senda.domain.usecase.evaluation.SaveEvaluationUseCase
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeDocumentStorage
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

class EvaluationUseCasesTest {

    private val evaluations = FakeEvaluationRepository()
    private val courses = FakeCourseRepository()
    private val storage = FakeDocumentStorage()

    @Test
    fun `saving warns when course weights exceed 100 percent but still saves`() = runTest {
        evaluations.seed(exam("a", 10, weight = 60.0), exam("b", 20, weight = 30.0))
        val save = SaveEvaluationUseCase(evaluations)

        val outcome = save(exam("c", 30, weight = 20.0)).getOrThrow()

        assertEquals(110.0, outcome.courseWeightTotal!!, 0.0)
        assertEquals(3, evaluations.items.value.size)
        // Editing an existing evaluation does not count its old weight twice.
        assertNull(save(exam("a", 10, weight = 50.0)).getOrThrow().courseWeightTotal)
    }

    @Test
    fun `reschedule keeps the duration and detects date changes`() = runTest {
        val original = exam("a", inHours = 10, durationHours = 2)
        val reschedule = RescheduleEvaluationUseCase(evaluations)
        val newStart = TEST_NOW + Duration.ofDays(2)

        reschedule(original, newStart)

        val saved = evaluations.items.value.getValue("a") as Exam
        assertEquals(newStart, saved.startAt)
        assertEquals(newStart + Duration.ofHours(2), saved.endAt)
        assertTrue(RescheduleEvaluationUseCase.isReschedule(original, saved))
        assertFalse(RescheduleEvaluationUseCase.isReschedule(original, original.copy(title = "Otro")))
    }

    @Test
    fun `project status changes through its phases`() = runTest {
        val project = Project(
            id = "p", courseId = "c1", title = "P", difficulty = 3, createdAt = TEST_NOW,
            phases = listOf(ProjectPhase("f1", "A1", TEST_NOW), ProjectPhase("f2", "A2", TEST_NOW + Duration.ofDays(1))),
        )
        val change = ChangeEvaluationStatusUseCase(evaluations)

        change.changePhase(project, "f1", PhaseStatus.DELIVERED)
        assertEquals(EvaluationStatus.IN_PROGRESS, evaluations.items.value.getValue("p").status)

        change(project, EvaluationStatus.COMPLETED)
        assertEquals(EvaluationStatus.COMPLETED, evaluations.items.value.getValue("p").status)
    }

    @Test
    fun `deleting an evaluation removes its local pdf`() = runTest {
        val withPdf = exam("a", 10).copy(syllabusPdfPath = "/files/pdfs/x.pdf")
        evaluations.seed(withPdf)

        DeleteEvaluationUseCase(evaluations, storage, FakeStudyBlockRepository())(withPdf)

        assertTrue(evaluations.items.value.isEmpty())
        assertEquals(listOf("/files/pdfs/x.pdf"), storage.deleted)
    }

    @Test
    fun `deleting an evaluation removes its study blocks`() = runTest {
        val target = exam("a", 10)
        evaluations.seed(target)
        val blocks = FakeStudyBlockRepository().apply {
            seed(
                StudyBlock("b1", "c1", "a", TEST_NOW, TEST_NOW + Duration.ofHours(1), StudyBlockStatus.SCHEDULED),
                StudyBlock("b2", "c1", "other", TEST_NOW, TEST_NOW + Duration.ofHours(1), StudyBlockStatus.SCHEDULED),
            )
        }

        DeleteEvaluationUseCase(evaluations, storage, blocks)(target)

        assertEquals(listOf("b2"), blocks.items.value.keys.toList())
    }

    @Test
    fun `deleting a course removes its blocks, questions and attempts`() = runTest {
        courses.seed(course("c1"), course("c2", "Química"))
        evaluations.seed(exam("a", 10))
        val blocks = FakeStudyBlockRepository().apply {
            seed(
                StudyBlock("b1", "c1", "a", TEST_NOW, TEST_NOW + Duration.ofHours(1), StudyBlockStatus.SCHEDULED),
                StudyBlock("b2", "c2", null, TEST_NOW, TEST_NOW + Duration.ofHours(1), StudyBlockStatus.SCHEDULED),
            )
        }
        val questions = FakeQuestionRepository().apply {
            seed(
                Question("q1", "c1", statement = "¿?", options = listOf("a", "b"), correctOptionIndex = 0, createdAt = TEST_NOW),
                Question("q2", "c2", statement = "¿?", options = listOf("a", "b"), correctOptionIndex = 0, createdAt = TEST_NOW),
            )
        }
        val simulacra = FakeSimulacrumRepository().apply {
            seed(
                Simulacrum("s1", "c1", questionIds = listOf("q1"), startedAt = TEST_NOW),
                Simulacrum("s2", "c2", questionIds = listOf("q2"), startedAt = TEST_NOW),
            )
        }

        DeleteCourseUseCase(courses, evaluations, storage, blocks, questions, simulacra)("c1")

        assertEquals(listOf("c2"), courses.items.value.keys.toList())
        assertEquals(listOf("b2"), blocks.items.value.keys.toList())
        assertEquals(listOf("q2"), questions.items.value.keys.toList())
        assertEquals(listOf("s2"), simulacra.items.value.keys.toList())
    }

    @Test
    fun `course validation and cascade delete`() = runTest {
        val save = SaveCourseUseCase(courses)
        val invalid = course().copy(name = " ", schedule = listOf(ClassSlot(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0))))
        assertTrue(save(invalid).exceptionOrNull() is ValidationException)

        save(course().copy(teacher = "  "))
        assertNull(courses.items.value.getValue("c1").teacher)

        evaluations.seed(exam("a", 10), exam("other", 10, courseId = "c2"))
        DeleteCourseUseCase(courses, evaluations, storage, FakeStudyBlockRepository(), FakeQuestionRepository(), FakeSimulacrumRepository())("c1")

        assertTrue(courses.observeAll().first().isEmpty())
        assertEquals(listOf("other"), evaluations.items.value.keys.toList())
    }
}
