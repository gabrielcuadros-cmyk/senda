package com.fuerzasalvaje.senda.util.gemini

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import javax.inject.Inject

/**
 * Alternative implementation through Firebase AI Logic (firebase-ai).
 * Uses the Firebase project from google-services.json; no API key is shipped in the app.
 */
class FirebaseGeminiService @Inject constructor(
    private val config: GeminiConfig,
    prompts: GeminiPrompts,
) : BaseGeminiService(prompts) {

    override suspend fun generateText(prompt: GeminiPrompt): String {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = config.model,
            generationConfig = if (prompt.expectsJson) {
                generationConfig { responseMimeType = "application/json" }
            } else {
                null
            },
            systemInstruction = content { text(prompt.system) },
        )
        return model.generateContent(prompt.user).text?.takeIf { it.isNotBlank() }
            ?: throw GeminiException("Firebase AI returned no text")
    }
}
