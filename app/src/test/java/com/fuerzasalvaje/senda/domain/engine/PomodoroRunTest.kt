package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroPhase
import com.fuerzasalvaje.senda.domain.model.PomodoroRun
import com.fuerzasalvaje.senda.testing.TEST_NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** Timer transitions computed only from timestamps, so they survive the app being closed. */
class PomodoroRunTest {

    private val config = PomodoroConfig()
    private var ids = 0
    private fun newId() = "s${++ids}"

    private fun linked(): PomodoroRun = PomodoroEngine.link(PomodoroEngine.idle(config), "e1", "c1", "Parcial de Física")

    @Test
    fun `starting opens a session for the linked evaluation and sets the end time`() {
        val run = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)

        assertTrue(run.running)
        assertEquals(TEST_NOW + Duration.ofMinutes(25), run.endsAt)
        assertEquals("e1", run.session?.evaluationId)
        assertEquals("c1", run.session?.courseId)
        assertEquals(25, run.session?.focusMinutes)
    }

    @Test
    fun `remaining time is computed from the end timestamp`() {
        val run = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)

        assertEquals(Duration.ofMinutes(15), run.remainingAt(TEST_NOW + Duration.ofMinutes(10)))
        assertEquals(Duration.ZERO, run.remainingAt(TEST_NOW + Duration.ofHours(3)))
        assertEquals(0.4f, run.fractionAt(TEST_NOW + Duration.ofMinutes(10)), 0.001f)
    }

    @Test
    fun `pausing freezes the remaining time and resuming continues from it`() {
        val started = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)
        val paused = PomodoroEngine.pause(started, TEST_NOW + Duration.ofMinutes(10))

        assertFalse(paused.running)
        assertEquals(Duration.ofMinutes(15), paused.remainingAt(TEST_NOW + Duration.ofHours(5)))

        val resumed = PomodoroEngine.start(paused, TEST_NOW + Duration.ofHours(1), ::newId, config)
        assertEquals(TEST_NOW + Duration.ofHours(1) + Duration.ofMinutes(15), resumed.endsAt)
        assertEquals(started.session?.id, resumed.session?.id)
    }

    @Test
    fun `a focus block that ended while the app was closed is credited on settle`() {
        val started = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)

        // The process comes back an hour later.
        val settled = PomodoroEngine.settle(started, TEST_NOW + Duration.ofHours(1), config)

        assertEquals(PomodoroPhase.SHORT_BREAK, settled.run.step.phase)
        assertFalse(settled.run.running)
        val completed = assertNotNullAndGet(settled.completed)
        assertEquals(1, completed.completedCycles)
        assertEquals(25, completed.focusedMinutes)
        assertEquals("e1", completed.evaluationId)
        // One record per block, covering the block itself, so overlaps with study blocks can be measured.
        assertEquals(TEST_NOW, completed.startedAt)
        assertEquals(TEST_NOW + Duration.ofMinutes(25), completed.endedAt)
    }

    @Test
    fun `settle does nothing before the end and breaks credit nothing`() {
        val started = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)
        assertEquals(started, PomodoroEngine.settle(started, TEST_NOW + Duration.ofMinutes(5), config).run)

        val inBreak = PomodoroEngine.settle(started, TEST_NOW + Duration.ofMinutes(25), config).run
        val breakRunning = PomodoroEngine.start(inBreak, TEST_NOW + Duration.ofMinutes(26), ::newId, config)
        val afterBreak = PomodoroEngine.settle(breakRunning, TEST_NOW + Duration.ofMinutes(40), config)

        assertNull(afterBreak.completed)
        assertEquals(PomodoroPhase.FOCUS, afterBreak.run.step.phase)
    }

    @Test
    fun `cycles accumulate in the same session`() {
        var run = linked()
        var now = TEST_NOW
        repeat(2) {
            run = PomodoroEngine.start(run, now, ::newId, config)
            now += Duration.ofMinutes(25)
            run = PomodoroEngine.settle(run, now, config).run
            run = PomodoroEngine.start(run, now, ::newId, config)
            now += Duration.ofMinutes(5)
            run = PomodoroEngine.settle(run, now, config).run
        }

        assertEquals(2, run.session?.completedCycles)
        assertEquals(50, run.session?.focusedMinutes)
        assertEquals(1, ids)
    }

    @Test
    fun `every finished block is saved as its own record`() {
        var run = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)
        val first = PomodoroEngine.settle(run, TEST_NOW + Duration.ofMinutes(25), config)
        run = PomodoroEngine.start(first.run, TEST_NOW + Duration.ofMinutes(25), ::newId, config)
        run = PomodoroEngine.settle(run, TEST_NOW + Duration.ofMinutes(30), config).run
        run = PomodoroEngine.start(run, TEST_NOW + Duration.ofMinutes(30), ::newId, config)
        val second = PomodoroEngine.settle(run, TEST_NOW + Duration.ofMinutes(55), config)

        assertEquals(listOf("s1-1", "s1-2"), listOf(first.completed?.id, second.completed?.id))
        assertEquals(TEST_NOW + Duration.ofMinutes(30), second.completed?.startedAt)
        assertEquals(1, second.completed?.completedCycles)
    }

    @Test
    fun `skipping a focus block does not credit it`() {
        val started = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)
        val skipped = PomodoroEngine.skip(started, config)

        assertFalse(skipped.running)
        assertEquals(PomodoroPhase.SHORT_BREAK, skipped.step.phase)
        assertEquals(0, skipped.session?.completedCycles)
    }

    @Test
    fun `the evaluation cannot change while running and changing it closes the session`() {
        val started = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)
        assertEquals(started, PomodoroEngine.link(started, "e2", "c2", "Otro"))

        val paused = PomodoroEngine.pause(started, TEST_NOW + Duration.ofMinutes(1))
        val relinked = PomodoroEngine.link(paused, "e2", "c2", "Otro")
        assertEquals("e2", relinked.evaluationId)
        assertNull(relinked.session)
    }

    @Test
    fun `idle keeps the chosen evaluation and is idle`() {
        val stopped = PomodoroEngine.idle(config, previous = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config))

        assertTrue(stopped.isIdle)
        assertEquals("e1", stopped.evaluationId)
        assertNull(stopped.session)
    }

    @Test
    fun `new durations apply only to a step that has not started`() {
        val longer = config.copy(focusMinutes = 50)

        assertEquals(50, PomodoroEngine.applyConfig(linked(), longer).step.minutes)
        assertEquals(Duration.ofMinutes(50), PomodoroEngine.applyConfig(linked(), longer).remaining)

        val started = PomodoroEngine.start(linked(), TEST_NOW, ::newId, config)
        assertEquals(started, PomodoroEngine.applyConfig(started, longer))
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
