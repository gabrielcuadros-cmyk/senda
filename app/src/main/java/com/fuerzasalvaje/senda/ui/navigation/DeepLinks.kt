package com.fuerzasalvaje.senda.ui.navigation

import com.fuerzasalvaje.senda.domain.model.NotificationTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Screen requested by a tapped notification. It waits here until the signed-in navigation consumes it,
 * so a notification tapped from the login screen still opens its screen after signing in.
 */
object DeepLinks {
    private val pending = MutableStateFlow<NotificationTarget?>(null)
    val target: StateFlow<NotificationTarget?> = pending.asStateFlow()

    /** Evaluation the Focus tab should link to the Pomodoro (from a study block alert). */
    private val focusEvaluation = MutableStateFlow<String?>(null)
    val pendingFocusEvaluation: StateFlow<String?> = focusEvaluation.asStateFlow()

    /** Evaluation whose detail sheet Home should open (from an evaluation reminder). */
    private val detailEvaluation = MutableStateFlow<String?>(null)
    val pendingEvaluationDetail: StateFlow<String?> = detailEvaluation.asStateFlow()

    fun open(target: NotificationTarget) {
        if (target is NotificationTarget.Focus && target.evaluationId != null) focusEvaluation.value = target.evaluationId
        if (target is NotificationTarget.EvaluationDetail) detailEvaluation.value = target.evaluationId
        pending.value = target
    }

    fun consumeEvaluationDetail() {
        detailEvaluation.value = null
    }

    fun consume() {
        pending.value = null
    }

    fun consumeFocusEvaluation() {
        focusEvaluation.value = null
    }
}
