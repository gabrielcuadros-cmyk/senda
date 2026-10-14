package com.fuerzasalvaje.senda.util.gemini

/** AI settings read from BuildConfig (sourced from local.properties). */
class GeminiConfig(
    val apiKey: String,
    val model: String,
    val provider: String,
    /** Used when the main model keeps failing with 500, 503 or 429 after every retry. */
    val fallbackModel: String = "",
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    // Never expose the key through logs or crash reports.
    override fun toString(): String =
        "GeminiConfig(model=$model, fallbackModel=$fallbackModel, provider=$provider, " +
            "apiKey=${if (hasApiKey) "***" else "<empty>"})"

    companion object {
        const val PROVIDER_API_KEY = "apikey"
        const val PROVIDER_FIREBASE = "firebase"
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
