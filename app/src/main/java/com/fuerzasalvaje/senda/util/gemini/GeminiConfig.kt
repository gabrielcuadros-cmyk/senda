package com.fuerzasalvaje.senda.util.gemini

/** AI settings read from BuildConfig (sourced from local.properties). */
class GeminiConfig(
    val apiKey: String,
    val model: String,
    val provider: String,
    /** Tried in order when a model keeps failing with 429, 500 or 503 (after its retries). */
    val fallbackModels: List<String> = emptyList(),
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    // Never expose the key through logs or crash reports.
    override fun toString(): String =
        "GeminiConfig(model=$model, fallbackModels=$fallbackModels, provider=$provider, " +
            "apiKey=${if (hasApiKey) "***" else "<empty>"})"

    companion object {
        const val PROVIDER_API_KEY = "apikey"
        const val PROVIDER_FIREBASE = "firebase"

        /** "a, b,,c " -> [a, b, c] */
        fun parseModels(value: String): List<String> = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}

/**
 * Retries for overload and rate limits: one wait per retry (2 s, 5 s and 10 s by default), then a
 * single attempt with the fallback model.
 */
data class RetryPolicy(val delaysMillis: List<Long> = listOf(2_000L, 5_000L, 10_000L)) {
    companion object {
        val RETRYABLE_HTTP_CODES = setOf(429, 500, 503)
    }
}
