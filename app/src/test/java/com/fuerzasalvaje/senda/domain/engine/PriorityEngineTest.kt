package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.EvaluationStatus
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.domain.model.Lab
import com.fuerzasalvaje.senda.domain.model.PhaseStatus
import com.fuerzasalvaje.senda.domain.model.Project
import com.fuerzasalvaje.senda.domain.model.ProjectPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PriorityEngineTest {

    private val now: Instant = Instant.parse("2026-10-12T08:00:00Z")
    private val engine = PriorityEngine(Clock.fixed(now, ZoneOffset.UTC))

    private fun exam(
        id: String,
        difficulty: Int,
        weight: Double,
        dueIn: Duration,
        courseId: String = "c1",
        status: EvaluationStatus = EvaluationStatus.PENDING,
    ) = Exam(
        id = id, courseId = courseId, title = id, weightPercent = weight, difficulty = difficulty,
        status = status, createdAt = now, startAt = now + dueIn, examType = ExamType.PARTIAL,
    )

    private fun ids(items: List<PriorityItem>) = items.map { it.evaluation.id }

    @Test
    fun `basic formula is difficulty times weight over remaining hours`() {
        val item = engine.rank(listOf(exam("e", difficulty = 4, weight = 30.0, dueIn = Duration.ofHours(10)))).single()

        assertEquals(12.0, item.score, 1e-9)
        assertEquals(10.0, item.hoursRemaining, 1e-9)
        assertEquals(2, item.normalizedScore) // 12 / 500 * 100 = 2.4
        assertFalse(item.isOverdue)
        assertEquals("Faltan 10 horas", item.timeLabel)
    }

    @Test
    fun `less than one hour uses one hour as minimum`() {
        val item = engine.rank(listOf(exam("e", difficulty = 3, weight = 20.0, dueIn = Duration.ofMinutes(30)))).single()

        assertEquals(60.0, item.score, 1e-9)
        assertEquals("Faltan menos de 1 hora", item.timeLabel)
    }

    @Test
    fun `normalized score is capped at 100`() {
        val item = engine.rank(listOf(exam("e", difficulty = 5, weight = 100.0, dueIn = Duration.ofMinutes(10)))).single()

        assertEquals(100, item.normalizedScore)
    }

    @Test
    fun `overdue evaluations go first and are labeled Vencida`() {
        val urgent = exam("urgent", difficulty = 5, weight = 100.0, dueIn = Duration.ofHours(2))
        val overdue = Lab(
            id = "overdue", courseId = "c1", title = "Informe", difficulty = 1, createdAt = now,
            dueAt = now - Duration.ofHours(3),
        )

        val ranked = engine.rank(listOf(urgent, overdue))

        assertEquals(listOf("overdue", "urgent"), ids(ranked))
        assertTrue(ranked.first().isOverdue)
        assertEquals("Vencida", ranked.first().timeLabel)
    }

    @Test
    fun `completed evaluations are excluded`() {
        val done = exam("done", difficulty = 5, weight = 50.0, dueIn = Duration.ofHours(5), status = EvaluationStatus.COMPLETED)
        val pending = exam("pending", difficulty = 1, weight = 10.0, dueIn = Duration.ofHours(50))

        assertEquals(listOf("pending"), ids(engine.rank(listOf(done, pending))))
    }

    @Test
    fun `equal score is broken by higher difficulty`() {
        val easy = exam("easy", difficulty = 2, weight = 50.0, dueIn = Duration.ofHours(10))
        val hard = exam("hard", difficulty = 4, weight = 25.0, dueIn = Duration.ofHours(10))

        assertEquals(listOf("hard", "easy"), ids(engine.rank(listOf(easy, hard))))
    }

    @Test
    fun `equal score and difficulty is broken by course importance`() {
        val low = exam("low", difficulty = 3, weight = 20.0, dueIn = Duration.ofHours(10), courseId = "lowCourse")
        val high = exam("high", difficulty = 3, weight = 20.0, dueIn = Duration.ofHours(10), courseId = "highCourse")

        val ranked = engine.rank(
            listOf(low, high),
            mapOf("lowCourse" to Importance.LOW, "highCourse" to Importance.HIGH),
        )

        assertEquals(listOf("high", "low"), ids(ranked))
        assertEquals(Importance.HIGH, ranked.first().importance)
    }

    @Test
    fun `remaining ties are broken by the closest date`() {
        // 2 x 20 / 10 = 4 and 2 x 40 / 20 = 4: same score, difficulty and importance.
        val later = exam("later", difficulty = 2, weight = 40.0, dueIn = Duration.ofHours(20))
        val sooner = exam("sooner", difficulty = 2, weight = 20.0, dueIn = Duration.ofHours(10))

        assertEquals(listOf("sooner", "later"), ids(engine.rank(listOf(later, sooner))))
    }

    @Test
    fun `project uses its next undelivered phase and the project weight`() {
        val project = Project(
            id = "p", courseId = "c1", title = "Proyecto", weightPercent = 40.0, difficulty = 4, createdAt = now,
            phases = listOf(
                ProjectPhase("a1", "Avance 1", now - Duration.ofHours(5), PhaseStatus.DELIVERED),
                ProjectPhase("final", "Entrega Final", now + Duration.ofHours(48)),
                ProjectPhase("a2", "Avance 2", now + Duration.ofHours(8), PhaseStatus.IN_PROGRESS),
            ),
        )

        val item = engine.rank(listOf(project)).single()

        assertEquals("a2", item.phase?.id)
        assertEquals(now + Duration.ofHours(8), item.dueAt)
        assertEquals(20.0, item.score, 1e-9) // 4 x 40 / 8
    }

    @Test
    fun `project with every phase delivered is excluded`() {
        val project = Project(
            id = "p", courseId = "c1", title = "Trabajo", difficulty = 3, createdAt = now,
            phases = listOf(ProjectPhase("only", "Entrega", now + Duration.ofHours(3), PhaseStatus.DELIVERED)),
        )

        assertEquals(EvaluationStatus.COMPLETED, project.status)
        assertNull(engine.evaluate(project, Importance.MEDIUM))
    }

    @Test
    fun `remaining time labels`() {
        assertEquals("Faltan 1 día y 4 horas", PriorityEngine.remainingLabel(Duration.ofHours(28)))
        assertEquals("Faltan 2 días y 1 hora", PriorityEngine.remainingLabel(Duration.ofHours(49)))
        assertEquals("Falta 1 día", PriorityEngine.remainingLabel(Duration.ofHours(24)))
        assertEquals("Faltan 3 días", PriorityEngine.remainingLabel(Duration.ofHours(72)))
        assertEquals("Falta 1 hora", PriorityEngine.remainingLabel(Duration.ofMinutes(90)))
        assertEquals("Faltan menos de 1 hora", PriorityEngine.remainingLabel(Duration.ofMinutes(59)))
    }
}
