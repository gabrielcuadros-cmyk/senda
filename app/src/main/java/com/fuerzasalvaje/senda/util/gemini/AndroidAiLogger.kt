package com.fuerzasalvaje.senda.util.gemini

import android.util.Log
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logcat output with the SendaAI tag. Callers only pass status codes, counts, model names and
 * Gemini's error text; the API key is never part of any message.
 */
@Singleton
class AndroidAiLogger @Inject constructor() : AiLogger {
    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun warn(message: String) {
        Log.w(TAG, message)
    }

    private companion object {
        const val TAG = "SendaAI"
    }
}
