package com.fuerzasalvaje.senda.domain.model

/** Screen a notification opens when tapped. Encoded as a plain string in the notification intent. */
sealed interface NotificationTarget {
    data object Home : NotificationTarget
    data object Wellbeing : NotificationTarget
    /** The Focus tab; [evaluationId] preselects what the Pomodoro is for. */
    data class Focus(val evaluationId: String? = null) : NotificationTarget
    data class EvaluationDetail(val evaluationId: String) : NotificationTarget

    fun encode(): String = when (this) {
        Home -> HOME
        Wellbeing -> WELLBEING
        is Focus -> if (evaluationId == null) FOCUS else "$FOCUS:$evaluationId"
        is EvaluationDetail -> "$EVALUATION:$evaluationId"
    }

    companion object {
        private const val HOME = "home"
        private const val WELLBEING = "wellbeing"
        private const val FOCUS = "focus"
        private const val EVALUATION = "evaluation"

        fun decode(value: String?): NotificationTarget? {
            if (value.isNullOrBlank()) return null
            val kind = value.substringBefore(':')
            val id = value.substringAfter(':', "").ifBlank { null }
            return when (kind) {
                HOME -> Home
                WELLBEING -> Wellbeing
                FOCUS -> Focus(id)
                EVALUATION -> id?.let(::EvaluationDetail)
                else -> null
            }
        }
    }
}
