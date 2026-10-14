package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class CriticalCourseAnalyzerTest {

    private val monday = LocalDate.of(2026, 10, 12)
    private val courses = listOf(course("c1", "Física"), course("c2", "Química"), course("c3", "Cálculo"))

    @Test
    fun `hours per course this week and the course above 40 percent`() {
        val evaluations = listOf(
            exam("fis", inHours = 50, courseId = "c1").copy(studyHours = 6.0),
            Lab(id = "lab", courseId = "c2", title = "Lab", difficulty = 1, studyHours = 2.0, createdAt = TEST_NOW, dueAt = TEST_NOW + Duration.ofHours(70)),
            exam("nextWeek", inHours = 8 * 24, courseId = "c3").copy(studyHours = 10.0),
            exam("done", inHours = 20, courseId = "c2").copy(studyHours = 4.0, status = EvaluationStatus.COMPLETED),
        )

        val report = CriticalCourseAnalyzer.analyze(evaluations, courses, monday, TEST_ZONE)

        assertEquals(8.0, report.totalHours, 0.0)
        assertEquals(listOf("c1", "c2"), report.loads.map { it.courseId })
        assertEquals(0.75, report.loads.first().share, 1e-9)
        assertEquals(listOf("c1"), report.critical.map { it.courseId })
        assertEquals("Física", report.critical.single().course?.name)
    }

    @Test
    fun `a single course with hours is never critical`() {
        val evaluations = listOf(
            exam("a", 10, courseId = "c1").copy(studyHours = 6.0),
            exam("b", 30, courseId = "c1").copy(studyHours = 4.0),
            // Next week: does not count, so this week only one course has hours.
            exam("c", 8 * 24, courseId = "c2").copy(studyHours = 3.0),
        )

        val report = CriticalCourseAnalyzer.analyze(evaluations, courses, monday, TEST_ZONE)

        assertEquals(1.0, report.loads.single().share, 0.0)
        assertFalse(report.loads.single().isCritical)
        assertTrue(report.critical.isEmpty())
    }

    @Test
    fun `exactly 40 percent is not critical`() {
        val evaluations = listOf(
            exam("a", 10, courseId = "c1").copy(studyHours = 4.0),
            exam("b", 30, courseId = "c2").copy(studyHours = 3.0),
            exam("c", 50, courseId = "c3").copy(studyHours = 3.0),
        )

        val report = CriticalCourseAnalyzer.analyze(evaluations, courses, monday, TEST_ZONE)

        assertFalse(report.loads.first().isCritical)
        assertTrue(report.critical.isEmpty())
    }
}
