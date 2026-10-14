package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.AiQuota
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
import java.util.concurrent.TimeUnit
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
    quota: AiQuota,
) : BaseGeminiService(prompts, logger, config, quota) {

    override suspend fun send(prompt: GeminiPrompt, model: String): String {
        if (!config.hasApiKey) throw AiError.Configuration("GEMINI_API_KEY is not configured in local.properties", invalidKey = true)

        // Text only: PDFs reach the model as Markdown made on the device, never as a file.
        val body = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = prompt.system))),
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt.user)))),
            generationConfig = generationConfig(prompt, model),
        )
        val request = Request.Builder()
            .url("$BASE_URL/$model:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // The shared client stops at 25 s; the whole question bank in one call gets its own, longer limits.
        val callClient = prompt.timeoutMillis?.let { limit ->
            client.newBuilder()
                .callTimeout(limit, TimeUnit.MILLISECONDS)
                .readTimeout(limit, TimeUnit.MILLISECONDS)
                .build()
        } ?: client
        val started = System.currentTimeMillis()
        val (code, responseText) = withContext(Dispatchers.IO) {
            try {
                callClient.newCall(request).execute().use { it.code to it.body.string() }
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

    private fun generationConfig(prompt: GeminiPrompt, model: String): GenerationConfig? {
        val thinking = when (val setting = Thinking.forModel(model, prompt.thinking)) {
            is ThinkingSetting.Level -> ThinkingConfig(thinkingLevel = setting.value)
            is ThinkingSetting.Budget -> ThinkingConfig(thinkingBudget = setting.tokens)
            null -> null
        }
        if (!prompt.expectsJson && thinking == null) return null
        return GenerationConfig(
            responseMimeType = if (prompt.expectsJson) "application/json" else null,
            responseSchema = when (prompt.schema) {
                ResponseSchema.QUESTIONS -> QUESTION_SCHEMA
                null -> null
            },
            thinkingConfig = thinking,
        )
    }

    /** [body] is scanned for per-day quota ids such as "GenerateRequestsPerDayPerProjectPerModel-FreeTier". */
    private fun httpError(code: Int, error: ErrorBody?, body: String): AiError {
        val message = error?.message.orEmpty()
        return when {
            code == 429 || error?.status == "RESOURCE_EXHAUSTED" -> AiError.QuotaExceeded(message, daily = isDailyQuota(body))
            code == 401 || code == 403 || message.contains("API key", ignoreCase = true) ->
                AiError.Configuration(message, invalidKey = true)
            // A model that does not exist (or not for this key): the next model of the chain is tried.
            code == 404 -> AiError.Configuration(message, invalidKey = false)
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
    private data class Part(val text: String? = null)

    @Serializable
    private data class GenerationConfig(
        val responseMimeType: String? = null,
        val responseSchema: JsonObject? = null,
        val thinkingConfig: ThinkingConfig? = null,
    )

    @Serializable
    private data class ThinkingConfig(val thinkingLevel: String? = null, val thinkingBudget: Int? = null)

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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** One question: {statement, scenario?, options[4], correctOptionIndex, explanation}. */
        private fun questionObject(): JsonObject = buildJsonObject {
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

        /** {"questions": [question]} */
        val QUESTION_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("questions") {
                    put("type", "ARRAY")
                    put("items", questionObject())
                }
            }
            putJsonArray("required") { add("questions") }
        }

    }
}
