package com.fuerzasalvaje.senda.domain.usecase

import com.fuerzasalvaje.senda.domain.engine.PriorityEngine
import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.usecase.ai.DailyAiQuota
import com.fuerzasalvaje.senda.domain.usecase.calendar.ObservePrioritiesUseCase
import com.fuerzasalvaje.senda.domain.usecase.wellbeing.GenerateStudyTipsUseCase
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.testing.FakeAiUsageStore
import com.fuerzasalvaje.senda.testing.FakeCourseRepository
import com.fuerzasalvaje.senda.testing.FakeEvaluationRepository
import com.fuerzasalvaje.senda.testing.FakeGeminiService
import com.fuerzasalvaje.senda.testing.FakePomodoroSessionRepository
import com.fuerzasalvaje.senda.testing.FakeRecommendationRepository
import com.fuerzasalvaje.senda.testing.FakeStudyBlockRepository
import com.fuerzasalvaje.senda.testing.TEST_NOW
import com.fuerzasalvaje.senda.testing.TEST_ZONE
import com.fuerzasalvaje.senda.testing.course
import com.fuerzasalvaje.senda.testing.exam
import com.fuerzasalvaje.senda.ui.components.AI_RESTING_MESSAGE
import com.fuerzasalvaje.senda.ui.components.aiErrorMessage
import com.fuerzasalvaje.senda.util.gemini.BaseGeminiService
import com.fuerzasalvaje.senda.util.gemini.GeminiConfig
import com.fuerzasalvaje.senda.util.gemini.GeminiPrompt
import com.fuerzasalvaje.senda.util.gemini.GeminiPrompts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Daily Gemini budget (points 5 and 6) and the once-a-day tutor tip (point 4). */
class AiQuotaTest {

    /** A clock the test can move forward. */
    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = TEST_ZONE
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MovableClock(TEST_NOW) // 08:00 in Lima
    private val store = FakeAiUsageStore()
    private val logger = FakeAiLogger()
    private val quota = DailyAiQuota(store, logger, clock)

    private val good = """{"questions":[{"statement":"¿A?","options":["1","2","3","4"],"correctOptionIndex":0,"explanation":"x"}]}"""
    private val dailyLimit = AiError.QuotaExceeded("Quota GenerateRequestsPerDayPerProjectPerModel-FreeTier exceeded", daily = true)

    /** Answers with the scripted results in order and counts what really reaches the transport. */
    private inner class ScriptedService(vararg answers: Any) :
        BaseGeminiService(GeminiPrompts(Json { ignoreUnknownKeys = true }), logger, GeminiConfig("key", "main", "apikey", listOf("fb-1")), quota) {
        private val queue = ArrayDeque(answers.toList())
        var sent = 0
        override suspend fun send(prompt: GeminiPrompt, model: String): String {
            sent++
            return when (val next = queue.removeFirst()) {
                is AiError -> throw next
                else -> next as String
            }
        }
    }

    // 5. Daily counter and background limit

    @Test
    fun `every request is counted and logged as Gemini calls today, and a new day starts at zero`() = runTest {
        val service = ScriptedService("no es JSON", good, "Consejo")

        service.generateQuestions(QuestionRequest("Física", "Cinemática", 15)).getOrThrow() // invalid JSON + retry
        service.explainMistake("Física", "¿A?", listOf("1", "2"), "1", "2").getOrThrow()

        assertEquals(3, store.callsOn(clock.now.atZone(TEST_ZONE).toLocalDate()))
        assertEquals(listOf("I Gemini calls today: 1", "I Gemini calls today: 2", "I Gemini calls today: 3"), logger.lines.filter { it.contains("Gemini calls today") })

        clock.now = TEST_NOW + Duration.ofDays(1)
        assertEquals(1, quota.afterCall())
    }

    @Test
    fun `at 40 calls background work stops but the student's own actions still reach the AI`() = runTest {
        store.callsDate = clock.now.atZone(TEST_ZONE).toLocalDate()
        store.calls = SendaRules.AI_DAILY_BACKGROUND_LIMIT - 1
        assertTrue(quota.allowsBackground())

        store.calls = SendaRules.AI_DAILY_BACKGROUND_LIMIT
        assertFalse(quota.allowsBackground())
        assertTrue(logger.lines.any { it.contains("Gemini calls today: 40; background generation paused") })

        val service = ScriptedService("Explicación")
        assertEquals("Explicación", service.explainMistake("Física", "¿A?", listOf("1", "2"), "1", "2").getOrThrow())

        clock.now = TEST_NOW + Duration.ofDays(1)
        assertTrue(quota.allowsBackground())
    }

    // 6. Daily quota used up

    @Test
    fun `a daily 429 is not retried, rests the AI until midnight and no call is made meanwhile`() = runTest {
        val service = ScriptedService(dailyLimit, "Desde fb-1", "Mañana")

        val error = service.studyTips(com.fuerzasalvaje.senda.domain.model.StudyTipsRequest(3, emptyList())).exceptionOrNull()

        assertTrue(error is AiError.Resting)
        assertEquals(1, service.sent) // no retry, no fallback model
        val midnight = Instant.parse("2026-10-13T05:00:00Z") // 00:00 in Lima
        assertEquals(midnight, store.exhaustedUntil)
        assertTrue(quota.isResting())
        assertTrue(quota.observeResting().first())
        assertFalse(quota.allowsBackground())
        assertEquals(AI_RESTING_MESSAGE, aiErrorMessage(error!!, "x"))

        // Later the same day: refused before any request.
        clock.now = TEST_NOW + Duration.ofHours(10)
        assertTrue(service.explainMistake("Física", "¿A?", listOf("1", "2"), "1", "2").exceptionOrNull() is AiError.Resting)
        assertEquals(1, service.sent)

        // After midnight the AI works again.
        clock.now = midnight + Duration.ofMinutes(1)
        assertFalse(quota.isResting())
        service.explainMistake("Física", "¿A?", listOf("1", "2"), "1", "2") // "Desde fb-1"
        assertEquals(2, service.sent)
    }

    @Test
    fun `the resting state survives a restart of the app`() = runTest {
        store.exhaustedUntil = Instant.parse("2026-10-13T05:00:00Z")

        val afterRestart = DailyAiQuota(store, logger, clock)

        assertTrue(afterRestart.isResting())
        assertTrue(runCatching { afterRestart.beforeCall() }.exceptionOrNull() is AiError.Resting)
    }

    // 4. Tutor tip: at most one per day unless the stress check-in changes

    @Test
    fun `the tutor tip is generated once per day and again only if the stress check-in changes`() = runTest {
        val gemini = FakeGeminiService()
        val courses = FakeCourseRepository().apply { seed(course()) }
        val evaluations = FakeEvaluationRepository().apply { seed(exam("Parcial 1", 30)) }
        val recommendations = FakeRecommendationRepository()
        val tips = GenerateStudyTipsUseCase(
            gemini, ObservePrioritiesUseCase(evaluations, courses, PriorityEngine(clock)), courses,
            FakeStudyBlockRepository(), FakePomodoroSessionRepository(), recommendations, clock,
        )

        val first = tips(3).getOrThrow()
        clock.now = TEST_NOW + Duration.ofHours(4)
        assertEquals(first, tips(3).getOrThrow()) // same day, same stress: cached
        assertEquals(1, gemini.tipsCalls)

        tips(5).getOrThrow() // the check-in changed
        assertEquals(2, gemini.tipsCalls)

        clock.now = TEST_NOW + Duration.ofDays(1) // a new day
        tips(5).getOrThrow()
        assertEquals(3, gemini.tipsCalls)
    }
}
