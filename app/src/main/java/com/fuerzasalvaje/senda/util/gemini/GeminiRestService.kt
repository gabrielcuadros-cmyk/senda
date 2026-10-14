package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Base64
import javax.inject.Inject

/**
 * Gemini Developer API over REST (generateContent).
 * The key travels in the x-goog-api-key header, never in the URL, and is never logged.
 */
class GeminiRestService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: GeminiConfig,
    prompts: GeminiPrompts,
    logger: AiLogger,
) : BaseGeminiService(prompts, logger, config) {

    override suspend fun send(prompt: GeminiPrompt, model: String): String {
        if (!config.hasApiKey) throw AiError.Configuration("GEMINI_API_KEY is not configured in local.properties")

        val parts = prompt.attachments.map { Part(inlineData = InlineData(PDF, Base64.getEncoder().encodeToString(it.bytes))) } +
            Part(text = prompt.user)
        val body = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = prompt.system))),
            contents = listOf(Content(role = "user", parts = parts)),
            generationConfig = if (prompt.expectsJson) {
                GenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = if (prompt.questionSchema) QUESTION_SCHEMA else null,
                )
            } else {
                null
            },
        )
        val request = Request.Builder()
            .url("$BASE_URL/$model:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val started = System.currentTimeMillis()
        val (code, responseText) = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { it.code to it.body.string() }
            } catch (e: InterruptedIOException) {
                logger.warn("Gemini timeout after ${System.currentTimeMillis() - started} ms (model=$model)")
                throw AiError.Timeout(e)
            } catch (e: IOException) {
                logger.warn("Gemini network error: ${e.javaClass.simpleName}: ${e.message}")
                throw AiError.Network(e)
            }
        }
        val elapsed = System.currentTimeMillis() - started

        if (code !in 200..299) {
            val error = runCatching { json.decodeFromString<ErrorResponse>(responseText).error }.getOrNull()
            logger.warn("Gemini HTTP $code status=${error?.status} model=$model: ${error?.message ?: "no message"}")
            throw httpError(code, error, responseText)
        }

        val parsed = try {
            json.decodeFromString<GenerateContentResponse>(responseText)
        } catch (e: Exception) {
            logger.warn("Gemini HTTP $code but the envelope is not readable: ${e.message?.take(120)}")
            throw AiError.InvalidResponse("Unreadable response", e)
        }
        val candidate = parsed.candidates.firstOrNull()
        val text = candidate?.content?.parts?.mapNotNull { it.text }?.joinToString("").orEmpty()
        logger.info("Gemini HTTP $code in $elapsed ms model=$model finishReason=${candidate?.finishReason} chars=${text.length}")

        parsed.promptFeedback?.blockReason?.let { throw AiError.Blocked(it) }
        if (text.isBlank()) throw AiError.InvalidResponse("Empty answer (finishReason=${candidate?.finishReason})")
        return text
    }

    /** [body] is scanned for per-day quota ids such as "GenerateRequestsPerDayPerProjectPerModel-FreeTier". */
    private fun httpError(code: Int, error: ErrorBody?, body: String): AiError {
        val message = error?.message.orEmpty()
        return when {
            code == 429 || error?.status == "RESOURCE_EXHAUSTED" -> AiError.QuotaExceeded(message, daily = isDailyQuota(body))
            code == 401 || code == 403 || code == 404 || message.contains("API key", ignoreCase = true) ->
                AiError.Configuration(message)
            else -> AiError.Server(code, message)
        }
    }

    @Serializable
    private data class GenerateContentRequest(
        val contents: List<Content>,
        val systemInstruction: Content? = null,
        val generationConfig: GenerationConfig? = null,
    )

    @Serializable
    private data class Content(val role: String? = null, val parts: List<Part> = emptyList())

    @Serializable
    private data class Part(val text: String? = null, val inlineData: InlineData? = null)

    @Serializable
    private data class InlineData(val mimeType: String, val data: String)

    @Serializable
    private data class GenerationConfig(
        val responseMimeType: String? = null,
        val responseSchema: JsonObject? = null,
    )

    @Serializable
    private data class GenerateContentResponse(
        val candidates: List<Candidate> = emptyList(),
        val promptFeedback: PromptFeedback? = null,
    )

    @Serializable
    private data class Candidate(val content: Content? = null, val finishReason: String? = null)

    @Serializable
    private data class PromptFeedback(val blockReason: String? = null)

    @Serializable
    private data class ErrorResponse(val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(val code: Int? = null, val message: String? = null, val status: String? = null)

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val PDF = "application/pdf"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** {"questions": [{statement, scenario?, options[4], correctOptionIndex, explanation}]} */
        val QUESTION_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("questions") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("statement") { put("type", "STRING") }
                            putJsonObject("scenario") { put("type", "STRING"); put("nullable", true) }
                            putJsonObject("options") {
                                put("type", "ARRAY")
                                putJsonObject("items") { put("type", "STRING") }
                                put("minItems", 4)
                                put("maxItems", 4)
                            }
                            putJsonObject("correctOptionIndex") { put("type", "INTEGER") }
                            putJsonObject("explanation") { put("type", "STRING") }
                        }
                        putJsonArray("required") {
                            add("statement"); add("options"); add("correctOptionIndex"); add("explanation")
                        }
                        putJsonArray("propertyOrdering") {
                            add("scenario"); add("statement"); add("options"); add("correctOptionIndex"); add("explanation")
                        }
                    }
                }
            }
            putJsonArray("required") { add("questions") }
        }
    }
}
