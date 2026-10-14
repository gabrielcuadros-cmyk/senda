package com.fuerzasalvaje.senda.util.gemini

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.firebase.ai.type.ThinkingLevel as FirebaseThinkingLevel
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
            generationConfig = generationConfig {
                if (prompt.expectsJson) responseMimeType = "application/json"
                when (prompt.schema) {
                    ResponseSchema.QUESTIONS -> responseSchema = QUESTION_SCHEMA
                    ResponseSchema.REVIEW -> responseSchema = REVIEW_SCHEMA
                    null -> Unit
                }
                when (val setting = Thinking.forModel(model, prompt.thinking)) {
                    is ThinkingSetting.Level -> thinkingConfig = thinkingConfig { thinkingLevel = setting.toFirebase() }
                    is ThinkingSetting.Budget -> thinkingConfig = thinkingConfig { thinkingBudget = setting.tokens }
                    null -> Unit
                }
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
            "QuotaExceededException" in names -> AiError.QuotaExceeded(message.orEmpty(), daily = isDailyQuota(message.orEmpty()))
            names.any { it in setOf("InvalidAPIKeyException", "APINotConfiguredException", "ServiceDisabledException") } ->
                AiError.Configuration(message.orEmpty(), invalidKey = true)
            message.orEmpty().contains("404") || message.orEmpty().contains("not found", ignoreCase = true) ->
                AiError.Configuration(message.orEmpty(), invalidKey = false)
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

    private fun ThinkingSetting.Level.toFirebase(): FirebaseThinkingLevel = when (value) {
        "minimal" -> FirebaseThinkingLevel.MINIMAL
        "low" -> FirebaseThinkingLevel.LOW
        "medium" -> FirebaseThinkingLevel.MEDIUM
        else -> FirebaseThinkingLevel.HIGH
    }

    private companion object {
        fun question(nullable: Boolean = false): Schema = Schema.obj(
            mapOf(
                "scenario" to Schema.string(nullable = true),
                "statement" to Schema.string(),
                "options" to Schema.array(Schema.string()),
                "correctOptionIndex" to Schema.integer(),
                "explanation" to Schema.string(),
            ),
            optionalProperties = listOf("scenario"),
            nullable = nullable,
        )

        val QUESTION_SCHEMA: Schema = Schema.obj(mapOf("questions" to Schema.array(question())))

        val REVIEW_SCHEMA: Schema = Schema.obj(
            mapOf(
                "reviews" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "index" to Schema.integer(),
                            "verdict" to Schema.enumeration(listOf("ok", "corregir", "descartar")),
                            "reason" to Schema.string(),
                            "question" to question(nullable = true),
                        ),
                        optionalProperties = listOf("reason", "question"),
                    )
                )
            )
        )
    }
}
