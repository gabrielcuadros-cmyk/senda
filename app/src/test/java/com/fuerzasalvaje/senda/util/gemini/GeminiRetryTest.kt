package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.PendingStudyItem
import com.fuerzasalvaje.senda.domain.model.QuestionRequest
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import com.fuerzasalvaje.senda.ui.components.AI_RESTING_MESSAGE
import com.fuerzasalvaje.senda.ui.components.FREE_LIMIT_MESSAGE
import com.fuerzasalvaje.senda.ui.components.aiErrorMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiRetryTest {

    private val logger = FakeAiLogger()
    private val tips = StudyTipsRequest(3, listOf(PendingStudyItem("Parcial", "Física", 50.0, 20, 4.0)))

    /** Answers per model: each entry is a text or an AiError to throw. Records the models called. */
    private inner class ChainService(
        fallbacks: List<String> = listOf("fb-1", "fb-2"),
        private val script: Map<String, List<Any>>,
    ) : BaseGeminiService(GeminiPrompts(Json), logger, GeminiConfig("key", "main", "apikey", fallbacks)) {
        private val queues = script.mapValues { ArrayDeque(it.value) }
        val calls = mutableListOf<String>()
        override suspend fun send(prompt: GeminiPrompt, model: String): String {
            calls += model
            return when (val next = queues.getValue(model).removeFirst()) {
                is AiError -> throw next
                SLOW -> { delay(60_000); "demasiado tarde" }
                else -> next as String
            }
        }
    }

    private fun busy() = AiError.Server(503, "The model is overloaded")
    private val SLOW = "SLOW"
    private fun rate() = AiError.QuotaExceeded("Too many requests")
    private fun daily() = AiError.QuotaExceeded("Quota GenerateRequestsPerDayPerProjectPerModel-FreeTier exceeded", daily = true)
    private fun times(n: Int, error: () -> AiError) = List(n) { error() }

    @Test
    fun `503 moves on to the next model at once`() = runTest {
        val service = ChainService(script = mapOf("main" to listOf(busy()), "fb-1" to listOf("Consejo"), "fb-2" to emptyList()))

        assertEquals("Consejo", service.studyTips(tips).getOrThrow())
        assertEquals(listOf("main", "fb-1"), service.calls)
        assertEquals(0, currentTime)
        assertFalse(service.retrying.value)
    }

    @Test
    fun `a timeout after 25 seconds and a missing model do not cut the chain`() = runTest {
        val service = ChainService(
            script = mapOf(
                "main" to listOf(SLOW),
                "fb-1" to listOf(AiError.Configuration("models/fb-1 is not found", invalidKey = false)),
                "fb-2" to listOf("Desde fb-2"),
            ),
        )

        assertEquals("Desde fb-2", service.studyTips(tips).getOrThrow())
        assertEquals(listOf("main", "fb-1", "fb-2"), service.calls)
        assertEquals(RetryPolicy.CALL_TIMEOUT_MILLIS, currentTime)
        assertTrue(logger.lines.any { it.contains("Gemini timeout after 25 s (model=main)") })
        assertTrue(logger.lines.any { it.contains("Switching to fallback model fb-2 (2/2) after model not found") })
    }

    @Test
    fun `a per-minute 429 is retried on the same model before moving on`() = runTest {
        val service = ChainService(
            script = mapOf(
                "main" to times(4, ::rate),
                "fb-1" to listOf(AiError.Server(500, "internal")),
                "fb-2" to listOf("Desde fb-2"),
            ),
        )

        assertEquals("Desde fb-2", service.studyTips(tips).getOrThrow())
        assertEquals(List(4) { "main" } + "fb-1" + "fb-2", service.calls)
        assertEquals(17_000, currentTime) // 2 + 5 + 10 s of waits on main only
        assertTrue(logger.lines.any { it.contains("Switching to fallback model fb-1 (1/2)") })
        assertTrue(logger.lines.any { it.contains("Switching to fallback model fb-2 (2/2)") })
    }

    @Test
    fun `a daily quota stops at once, with no retry and no fallback, and the AI rests`() = runTest {
        val service = ChainService(script = mapOf("main" to listOf(daily()), "fb-1" to listOf("Desde fb-1"), "fb-2" to emptyList()))

        val error = service.studyTips(tips).exceptionOrNull()

        assertTrue(error is AiError.Resting)
        assertEquals(listOf("main"), service.calls)
        assertEquals(0, currentTime)
        assertEquals(AI_RESTING_MESSAGE, aiErrorMessage(error!!, "x"))
        assertFalse(service.retrying.value)
    }

    @Test
    fun `when every model ends with a per-minute 429 the free limit is reached`() = runTest {
        val service = ChainService(
            script = mapOf("main" to times(4, ::rate), "fb-1" to times(4, ::rate), "fb-2" to times(4, ::rate)),
        )

        val error = service.studyTips(tips).exceptionOrNull()

        assertTrue(error is AiError.FreeLimitReached)
        assertEquals(listOf("main", "fb-1", "fb-2"), (error as AiError.FreeLimitReached).models)
        assertEquals("$FREE_LIMIT_MESSAGE. Vuelve a intentarlo mañana.", aiErrorMessage(error, "x"))
        assertFalse(service.retrying.value)
    }

    @Test
    fun `if the last model fails with 503 that error is reported, not the free limit`() = runTest {
        val service = ChainService(
            fallbacks = listOf("fb-1"),
            script = mapOf("main" to times(4, ::rate), "fb-1" to listOf(busy())),
        )

        val error = service.studyTips(tips).exceptionOrNull()

        assertTrue(error is AiError.Server && error.code == 503)
        assertEquals(5, service.calls.size)
    }

    @Test
    fun `without fallbacks the last error is returned`() = runTest {
        val service = ChainService(fallbacks = emptyList(), script = mapOf("main" to listOf(busy())))

        assertTrue(service.studyTips(tips).exceptionOrNull() is AiError.Server)
        assertEquals(1, service.calls.size)
    }

    @Test
    fun `only an invalid API key stops the chain`() = runTest {
        val service = ChainService(script = mapOf("main" to listOf(AiError.Configuration("API key not valid"))))

        assertTrue(service.studyTips(tips).exceptionOrNull() is AiError.Configuration)
        assertEquals(listOf("main"), service.calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun `retrying stays on while switching models and turns off at the end`() = runTest {
        val service = ChainService(script = mapOf("main" to listOf(busy()), "fb-1" to listOf(rate(), "Listo"), "fb-2" to emptyList()))

        val call = async { service.studyTips(tips) }
        runCurrent()
        assertTrue(service.retrying.value)

        advanceUntilIdle()
        assertEquals("Listo", call.await().getOrThrow())
        assertFalse(service.retrying.value)
    }

    @Test
    fun `question generation also uses the chain`() = runTest {
        val json = """{"questions":[{"statement":"¿A?","options":["1","2","3","4"],"correctOptionIndex":0,"explanation":"x"}]}"""
        val service = ChainService(script = mapOf("main" to listOf(busy()), "fb-1" to listOf(json), "fb-2" to emptyList()))

        assertEquals(1, service.generateQuestions(QuestionRequest("Física", "Cinemática", 10)).getOrThrow().size)
        assertEquals(listOf("main", "fb-1"), service.calls)
    }

    @Test
    fun `model list and daily quota parsing`() {
        assertEquals(listOf("gemini-3-flash-preview", "gemini-2.5-flash"), GeminiConfig.parseModels(" gemini-3-flash-preview, ,gemini-2.5-flash,gemini-2.5-flash "))
        assertTrue(GeminiConfig.parseModels("").isEmpty())
        assertTrue(isDailyQuota("""{"quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier"}"""))
        assertFalse(isDailyQuota("""{"quotaId": "GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}"""))
    }
}
