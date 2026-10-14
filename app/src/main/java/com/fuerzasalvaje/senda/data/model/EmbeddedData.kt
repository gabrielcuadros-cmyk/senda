package com.fuerzasalvaje.senda.data.model

import kotlinx.serialization.Serializable

// Nested values shared by Room (stored as JSON) and Firestore (stored as maps).
// Firestore needs mutable properties with defaults; Room converters use kotlinx-serialization.

@Serializable
data class ClassSlotData(
    var day: Int = 1,
    var start: String = "08:00",
    var end: String = "09:00",
    var room: String = "",
)

@Serializable
data class PhaseData(
    var id: String = "",
    var name: String = "",
    var dueAt: Long = 0,
    var status: String = "PENDING",
)

@Serializable
data class RubricItemData(
    var criterion: String = "",
    var maxScore: Double = 0.0,
)

@Serializable
data class AnswerData(
    var questionId: String = "",
    var selectedOptionIndex: Int? = null,
    var correct: Boolean = false,
)
