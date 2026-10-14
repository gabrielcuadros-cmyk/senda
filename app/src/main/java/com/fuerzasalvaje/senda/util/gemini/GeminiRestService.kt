package com.fuerzasalvaje.senda.util.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
) : BaseGeminiService(prompts) {

    override suspend fun generateText(prompt: GeminiPrompt): String {
        if (!config.hasApiKey) throw GeminiException("GEMINI_API_KEY is not configured in local.properties")

        val body = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(prompt.system))),
            contents = listOf(Content(role = "user", parts = listOf(Part(prompt.user)))),
            generationConfig = if (prompt.expectsJson) GenerationConfig(responseMimeType = "application/json") else null,
        )
        val request = Request.Builder()
            .url("$BASE_URL/${config.model}:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val responseText = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    throw GeminiException("Gemini request failed (HTTP ${response.code}): ${errorMessage(text)}")
                }
                text
            }
        }

        val parsed = json.decodeFromString<GenerateContentResponse>(responseText)
        return parsed.candidates.firstOrNull()?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }
            ?: throw GeminiException(
                "Gemini returned no text" +
                    (parsed.promptFeedback?.blockReason?.let { " (blocked: $it)" } ?: "")
            )
    }

    private fun errorMessage(body: String): String =
        runCatching { json.decodeFromString<ErrorResponse>(body).error?.message }.getOrNull()
            ?: "unknown error"

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
    private data class GenerationConfig(val responseMimeType: String? = null)

    @Serializable
    private data class GenerateContentResponse(
        val candidates: List<Candidate> = emptyList(),
        val promptFeedback: PromptFeedback? = null,
    )

    @Serializable
    private data class Candidate(val content: Content? = null)

    @Serializable
    private data class PromptFeedback(val blockReason: String? = null)

    @Serializable
    private data class ErrorResponse(val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(val message: String? = null)

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
