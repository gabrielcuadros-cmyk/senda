package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.PendingStudyItem
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.testing.FakeAiLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
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

    /** Each call takes the next outcome: a text, or an AiError to throw. Records the model used. */
    private inner class FlakyService(fallback: String = "backup-model", vararg outcomes: Any) :
        BaseGeminiService(GeminiPrompts(Json), logger, GeminiConfig("key", "main-model", "apikey", fallback)) {
        private val queue = ArrayDeque(outcomes.toList())
        val models = mutableListOf<String>()
        override suspend fun send(prompt: GeminiPrompt, model: String): String {
            models += model
            return when (val next = queue.removeFirst()) {
                is AiError -> throw next
                else -> next as String
            }
        }
    }

    private fun overloaded() = AiError.Server(503, "The model is overloaded")

    @Test
    fun `503 is retried after 2 and 5 seconds until it succeeds`() = runTest {
        val service = FlakyService("backup-model", overloaded(), AiError.Server(500, "internal"), "Consejo")

        assertEquals("Consejo", service.studyTips(tips).getOrThrow())
        assertEquals(listOf("main-model", "main-model", "main-model"), service.models)
        assertEquals(7_000, currentTime)
        assertFalse(service.retrying.value)
    }

    @Test
    fun `after 3 retries the fallback model is used`() = runTest {
        val service = FlakyService(
            "backup-model",
            overloaded(), AiError.QuotaExceeded("rate"), overloaded(), AiError.Server(500, "x"), "Desde el respaldo",
        )

        assertEquals("Desde el respaldo", service.studyTips(tips).getOrThrow())
        assertEquals(listOf("main-model", "main-model", "main-model", "main-model", "backup-model"), service.models)
        assertEquals(17_000, currentTime) // 2 s + 5 s + 10 s
        assertTrue(logger.lines.any { it.contains("using fallback model backup-model") })
    }

    @Test
    fun `when the fallback also fails its error is reported`() = runTest {
        val service = FlakyService("backup-model", overloaded(), overloaded(), overloaded(), overloaded(), AiError.QuotaExceeded("limit"))

        assertTrue(service.studyTips(tips).exceptionOrNull() is AiError.QuotaExceeded)
        assertEquals(5, service.models.size)
    }

    @Test
    fun `without a fallback model the last error is returned after the retries`() = runTest {
        val service = FlakyService("", overloaded(), overloaded(), overloaded(), overloaded())

        val error = service.studyTips(tips).exceptionOrNull()

        assertTrue(error is AiError.Server && error.code == 503)
        assertEquals(4, service.models.size)
    }

    @Test
    fun `other errors are not retried`() = runTest {
        val service = FlakyService("backup-model", AiError.Configuration("API key not valid"))

        assertTrue(service.studyTips(tips).exceptionOrNull() is AiError.Configuration)
        assertEquals(listOf("main-model"), service.models)
        assertEquals(0, currentTime)

        val notFound = FlakyService("backup-model", AiError.Server(404, "not found"))
        assertTrue(notFound.studyTips(tips).isFailure)
        assertEquals(1, notFound.models.size)
    }

    @Test
    fun `retrying is true only while waiting for a retry`() = runTest {
        val service = FlakyService("backup-model", overloaded(), "Listo")

        val call = async { service.studyTips(tips) }
        runCurrent()
        assertTrue(service.retrying.value)

        advanceTimeBy(1_000)
        assertTrue(service.retrying.value)

        advanceUntilIdle()
        assertEquals("Listo", call.await().getOrThrow())
        assertFalse(service.retrying.value)
    }

    @Test
    fun `question generation also retries before parsing`() = runTest {
        val json = """{"questions":[{"statement":"¿A?","options":["1","2","3","4"],"correctOptionIndex":0,"explanation":"x"}]}"""
        val service = FlakyService("backup-model", overloaded(), json)

        val questions = service.generateQuestions(com.fuerzasalvaje.senda.domain.model.QuestionRequest("Física", "Cinemática", 10))

        assertEquals(1, questions.getOrThrow().size)
        assertEquals(2_000, currentTime)
    }
}
