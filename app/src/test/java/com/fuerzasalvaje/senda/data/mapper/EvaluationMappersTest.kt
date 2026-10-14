package com.fuerzasalvaje.senda.data.mapper

import com.fuerzasalvaje.senda.domain.model.ClassSlot
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.LabModality
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.domain.model.RubricItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class EvaluationMappersTest {

    private val now = Instant.parse("2026-10-12T08:00:00Z")

    private val exam = Exam(
        id = "e1", courseId = "c1", title = "Parcial", weightPercent = 30.0, difficulty = 4, createdAt = now,
        startAt = now + Duration.ofDays(2), room = "A-201", examType = ExamType.PARTIAL,
        syllabus = "Unidad 1", syllabusPdfPath = "/data/files/temario.pdf",
    )
    private val lab = Lab(
        id = "l1", courseId = "c1", title = "Lab 3", difficulty = 2, createdAt = now, guideNumber = "3",
        dueAt = now + Duration.ofDays(1), requiredTools = listOf("Arduino", "Multímetro"),
        modality = LabModality.GROUP, guidePdfPath = "/data/files/guia.pdf",
    )
    private val project = Project(
        id = "p1", courseId = "c1", title = "Proyecto", weightPercent = 25.0, difficulty = 5, createdAt = now,
        members = listOf("Ana", "Luis"),
        phases = listOf(
            ProjectPhase("a1", "Avance 1", now + Duration.ofDays(3), PhaseStatus.DELIVERED),
            ProjectPhase("a2", "Avance 2", now + Duration.ofDays(10)),
        ),
        rubric = listOf(RubricItem("Diseño", 5.0)),
    )

    @Test
    fun `every evaluation type survives the Room round trip`() {
        listOf(exam, lab, project).forEach { assertEquals(it, it.toEntity("uid").toDomain()) }
    }

    @Test
    fun `remote documents never carry local pdf paths but keep the local copy`() {
        val dto = exam.toDto()
        assertEquals(exam.copy(syllabusPdfPath = null), dto.toDomain("e1"))
        assertEquals(exam, dto.toDomain("e1", localPdfPath = "/data/files/temario.pdf"))
        assertEquals(lab, lab.toDto().toDomain("l1", localPdfPath = "/data/files/guia.pdf"))
        assertEquals(project, project.toDto().toDomain("p1"))
    }

    @Test
    fun `the PDF Markdown is kept in Room and synced to Firestore with the evaluation`() {
        val withText = exam.copy(syllabusPdfMarkdown = "## Diapositiva 1\n- V = I x R", syllabusPdfSummarized = true)
        val labText = lab.copy(guidePdfMarkdown = "## Página 2\n- T = 2 s")
        assertEquals(withText, withText.toEntity("uid").toDomain())
        assertEquals("## Diapositiva 1\n- V = I x R", withText.toDto().pdfMarkdown)
        assertTrue(withText.toDto().pdfSummarized)
        // Another device has no local copy of the PDF but gets the text the AI reads.
        assertEquals(withText.copy(syllabusPdfPath = null), withText.toDto().toDomain("e1"))
        assertEquals(labText.copy(guidePdfPath = null), labText.toDto().toDomain("l1"))
    }

    @Test
    fun `invalid remote weight and difficulty are sanitized`() {
        val dto = lab.toDto().apply { weightPercent = 0.0; difficulty = 9 }
        val restored = dto.toDomain("l1") as Lab
        assertEquals(10.0, restored.weightPercent, 0.0)
        assertEquals(5, restored.difficulty)
        assertNull(restored.guidePdfPath)
    }

    @Test
    fun `course with class schedule survives both round trips`() {
        val course = Course(
            id = "c1", name = "Física", teacher = null, importance = Importance.HIGH, colorArgb = 0xFFB5D8FF,
            schedule = listOf(ClassSlot(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 30), "Lab 2")),
            createdAt = now,
        )
        assertEquals(course, course.toEntity("uid").toDomain())
        assertEquals(course, course.toDto().toDomain("c1"))
    }
}
