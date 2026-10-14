package com.fuerzasalvaje.senda.util.gemini

/** AI settings read from BuildConfig (sourced from local.properties). */
class GeminiConfig(
    val apiKey: String,
    val model: String,
    val provider: String,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    // Never expose the key through logs or crash reports.
    override fun toString(): String =
        "GeminiConfig(model=$model, provider=$provider, apiKey=${if (hasApiKey) "***" else "<empty>"})"

    companion object {
        const val PROVIDER_API_KEY = "apikey"
        const val PROVIDER_FIREBASE = "firebase"
    }
}

class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause)
