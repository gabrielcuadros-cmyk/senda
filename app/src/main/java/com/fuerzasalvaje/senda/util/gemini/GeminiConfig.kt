package com.fuerzasalvaje.senda.util.gemini

/** AI settings read from BuildConfig (sourced from local.properties). */
class GeminiConfig(
    val apiKey: String,
    val model: String,
    val provider: String,
    /** Tried in order when a model fails (timeout, 404, 429, 5xx); only an invalid API key stops the chain. */
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
 * [callTimeoutMillis] limits every Gemini call (25 s); a timeout moves on to the next model.
 * A per-minute rate limit (429) waits 2 s, 5 s and 10 s on the same model before moving on.
 */
data class RetryPolicy(
    val delaysMillis: List<Long> = listOf(2_000L, 5_000L, 10_000L),
    val callTimeoutMillis: Long = CALL_TIMEOUT_MILLIS,
) {
    companion object {
        const val CALL_TIMEOUT_MILLIS = 25_000L

        /** The one-time PDF summary reads the whole document: it gets 120 s per call. */
        const val DIGEST_TIMEOUT_MILLIS = 120_000L
    }
}
