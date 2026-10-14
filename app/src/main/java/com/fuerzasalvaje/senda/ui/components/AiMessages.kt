package com.fuerzasalvaje.senda.ui.components

import com.fuerzasalvaje.senda.domain.model.AiError

const val FREE_LIMIT_MESSAGE = "Alcanzaste el límite gratuito de IA de hoy"

/** Shown while the daily Gemini quota is used up: practice keeps working from the saved questions. */
const val AI_RESTING_MESSAGE = "La IA está descansando hasta mañana. Puedes seguir practicando con tus preguntas guardadas"

/** Spanish message for an AI failure. The technical detail is in Logcat (tag SendaAI). */
fun aiErrorMessage(error: Throwable, default: String): String = when (error) {
    is AiError.Resting -> AI_RESTING_MESSAGE
    is AiError.FreeLimitReached -> "$FREE_LIMIT_MESSAGE. Vuelve a intentarlo mañana."
    is AiError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    is AiError.Timeout -> "La IA tardó demasiado en responder. Inténtalo de nuevo."
    is AiError.QuotaExceeded -> "Llegaste al límite de uso de Gemini. Espera unos minutos e inténtalo de nuevo."
    is AiError.Configuration -> "La IA no está bien configurada: revisa GEMINI_API_KEY y GEMINI_MODEL en local.properties."
    is AiError.Blocked -> "La IA no pudo usar este material por sus filtros de seguridad. Prueba con otros temas."
    is AiError.InvalidResponse -> "La IA respondió en un formato inválido. Inténtalo otra vez."
    is AiError.Server -> "El servicio de IA falló (código ${error.code}). Inténtalo en unos minutos."
    else -> default
}
