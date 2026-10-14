package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Alternative implementation through Firebase AI Logic (firebase-ai).
 * Uses the Firebase project from google-services.json; no API key is shipped in the app.
 */
class FirebaseGeminiService @Inject constructor(
    private val config: GeminiConfig,
    prompts: GeminiPrompts,
    logger: AiLogger,
) : BaseGeminiService(prompts, logger, config) {

    override suspend fun send(prompt: GeminiPrompt, model: String): String {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = model,
            generationConfig = if (prompt.expectsJson) {
                generationConfig {
                    responseMimeType = "application/json"
                    if (prompt.questionSchema) responseSchema = QUESTION_SCHEMA
                }
            } else {
                null
            },
            systemInstruction = content { text(prompt.system) },
        )
        val request = content {
            prompt.attachments.forEach { inlineData(it.bytes, "application/pdf") }
            text(prompt.user)
        }
        val text = try {
            model.generateContent(request).text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Firebase AI error: ${e.javaClass.simpleName}: ${e.message}")
            throw e.toAiError()
        }
        logger.info("Firebase AI answered model=$model chars=${text?.length ?: 0}")
        return text?.takeIf { it.isNotBlank() } ?: throw AiError.InvalidResponse("Firebase AI returned no text")
    }

    /** Matched by name so the mapping does not depend on firebase-ai internals. */
    private fun Exception.toAiError(): AiError {
        val chain = generateSequence<Throwable>(this) { it.cause }.toList()
        val names = chain.map { it.javaClass.simpleName }
        return when {
            chain.any { it is IOException } -> AiError.Network(this)
            "QuotaExceededException" in names -> AiError.QuotaExceeded(message.orEmpty())
            names.any { it in setOf("InvalidAPIKeyException", "APINotConfiguredException", "ServiceDisabledException") } ->
                AiError.Configuration(message.orEmpty())
            names.any { it in setOf("PromptBlockedException", "ResponseStoppedException") } -> AiError.Blocked(message.orEmpty())
            names.any { it in setOf("SerializationException", "InvalidStateException") } -> AiError.InvalidResponse(message.orEmpty(), this)
            names.any { it.contains("Timeout") } -> AiError.Timeout(this)
            // 5xx: keep the HTTP code when the message carries it, so 500 and 503 are retried.
            "ServerException" in names -> AiError.Server(
                Regex("\\b(5\\d\\d)\\b").find(message.orEmpty())?.value?.toInt() ?: 503,
                message.orEmpty(),
            )
            else -> AiError.Server(-1, message.orEmpty())
        }
    }

    private companion object {
        val QUESTION_SCHEMA: Schema = Schema.obj(
            mapOf(
                "questions" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "scenario" to Schema.string(nullable = true),
                            "statement" to Schema.string(),
                            "options" to Schema.array(Schema.string()),
                            "correctOptionIndex" to Schema.integer(),
                            "explanation" to Schema.string(),
                        ),
                        optionalProperties = listOf("scenario"),
                    )
                )
            )
        )
    }
}
