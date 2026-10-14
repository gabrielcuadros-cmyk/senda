package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.EvaluationFilter
import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.EvaluationType
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class EpicOneLogicTest {

    private val monday = LocalDate.of(2026, 10, 12)

    @Test
    fun `study hours estimator follows D-7 and never goes negative`() {
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 10.0), (1..5).map(StudyHoursEstimator::suggested))
        assertEquals(6.5, StudyHoursEstimator.increment(6.0), 0.0)
        assertEquals(0.0, StudyHoursEstimator.decrement(0.0), 0.0)
        assertEquals(200.0, StudyHoursEstimator.increment(200.0), 0.0)
    }

    @Test
    fun `validators cover weight, title, dates, hours and class slots`() {
        assertEquals(ValidationError.WEIGHT_INVALID, Validators.weight(Validators.parseNumber("0")))
        assertNull(Validators.weight(Validators.parseNumber("30,5")))
        assertEquals(ValidationError.WEIGHT_INVALID, Validators.weight(Validators.parseNumber("abc")))
        assertEquals(ValidationError.TITLE_REQUIRED, Validators.title("  "))
        assertEquals(ValidationError.DATE_REQUIRED, Validators.dateRange(null, null))
        assertEquals(ValidationError.END_BEFORE_START, Validators.dateRange(TEST_NOW, TEST_NOW))
        assertNull(Validators.dateRange(TEST_NOW, TEST_NOW + Duration.ofHours(1)))
        assertEquals(ValidationError.HOURS_INVALID, Validators.studyHours(-1.0))
        assertEquals(ValidationError.CLASS_SLOT_INVALID, Validators.classSlot(LocalTime.of(10, 0), LocalTime.of(9, 0)))
    }

    @Test
    fun `course weight warning only when the total exceeds 100 percent`() {
        assertEquals(110.0, Validators.courseWeightTotalIfExceeded(listOf(60.0, 30.0), 20.0)!!, 0.0)
        assertNull(Validators.courseWeightTotalIfExceeded(listOf(60.0, 20.0), 20.0))
    }

    @Test
    fun `week contains only blocks from Monday to Sunday`() {
        val entries = CalendarPlanner.entries(
            listOf(exam("mon", inHours = 1), exam("sun", inHours = 6 * 24 + 10), exam("nextMon", inHours = 7 * 24)),
            listOf(course()),
        )

        assertEquals(listOf("mon", "sun"), CalendarPlanner.inWeek(entries, monday, TEST_ZONE).map { it.id })
        assertEquals(monday, CalendarPlanner.weekStartOf(LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun `weekly progress counts completed exams and delivered phases`() {
        val project = Project(
            id = "p", courseId = "c1", title = "P", difficulty = 3, createdAt = TEST_NOW,
            phases = listOf(
                ProjectPhase("f1", "Avance 1", TEST_NOW + Duration.ofHours(5), PhaseStatus.DELIVERED),
                ProjectPhase("f2", "Avance 2", TEST_NOW + Duration.ofHours(30)),
            ),
        )
        val entries = CalendarPlanner.entries(
            listOf(exam("a", 1).copy(status = EvaluationStatus.COMPLETED), exam("b", 3), project),
            listOf(course()),
        )

        val progress = CalendarPlanner.weeklyProgress(entries)

        assertEquals(2, progress.completed)
        assertEquals(4, progress.total)
        assertEquals(50, progress.percent)
    }

    @Test
    fun `filters combine type and course`() {
        val lab = Lab(id = "lab", courseId = "c2", title = "Lab", difficulty = 2, createdAt = TEST_NOW, dueAt = TEST_NOW)
        val entries = CalendarPlanner.entries(listOf(exam("e1", 1), exam("e2", 2, courseId = "c2"), lab), listOf(course(), course("c2")))

        fun ids(filter: EvaluationFilter) = entries.filter(filter::matches).map { it.id }.toSet()

        assertEquals(setOf("e1", "e2", "lab"), ids(EvaluationFilter()))
        assertEquals(setOf("e1", "e2"), ids(EvaluationFilter(types = setOf(EvaluationType.EXAM))))
        assertEquals(setOf("e2", "lab"), ids(EvaluationFilter(courseId = "c2")))
        assertEquals(setOf("e2"), ids(EvaluationFilter(types = setOf(EvaluationType.EXAM), courseId = "c2")))
        assertEquals(setOf("e2", "lab"), ids(EvaluationFilter(types = setOf(EvaluationType.EXAM, EvaluationType.LAB), courseId = "c2")))
    }

    @Test
    fun `calendar blocks use default durations and phases as deadlines`() {
        val project = Project(
            id = "p", courseId = "c1", title = "P", difficulty = 3, createdAt = TEST_NOW,
            phases = listOf(ProjectPhase("f1", "Avance 1", TEST_NOW + Duration.ofHours(5))),
        )
        val entries = CalendarPlanner.entries(listOf(exam("e", 1), project), listOf(course()))

        val examEntry = entries.first { it.id == "e" }
        assertEquals(Duration.ofHours(2), Duration.between(examEntry.start, examEntry.end))
        val phaseEntry = entries.first { it.phase != null }
        assertEquals("p:f1", phaseEntry.id)
        assertEquals(phaseEntry.start, phaseEntry.end)
        assertEquals(Instant.parse("2026-10-12T18:00:00Z"), phaseEntry.start)
    }
}
