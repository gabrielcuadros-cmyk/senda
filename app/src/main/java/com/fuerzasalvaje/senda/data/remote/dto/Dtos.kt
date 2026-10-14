package com.fuerzasalvaje.senda.data.remote.dto

import com.fuerzasalvaje.senda.data.model.AnswerData
import com.fuerzasalvaje.senda.data.model.ClassSlotData
import com.fuerzasalvaje.senda.data.model.PhaseData
import com.fuerzasalvaje.senda.data.model.RubricItemData
import com.google.firebase.firestore.IgnoreExtraProperties

// Firestore documents under users/{uid}/... The document id is the entity id, so it is not repeated.
// Firestore maps them by reflection: mutable properties with defaults, no "is" prefixes.
// Local PDF paths are deliberately absent: files stay on the device (D-11).

@IgnoreExtraProperties
data class UserDto(
    var name: String = "",
    var email: String = "",
    var plan: String = "FREE",
    var createdAt: Long = 0,
)

@IgnoreExtraProperties
data class CourseDto(
    var name: String = "",
    var teacher: String? = null,
    var importance: String = "MEDIUM",
    var colorArgb: Long = 0,
    var schedule: List<ClassSlotData> = emptyList(),
    var createdAt: Long = 0,
)

@IgnoreExtraProperties
data class EvaluationDto(
    var courseId: String = "",
    var type: String = "EXAM",
    var title: String = "",
    var weightPercent: Double = 10.0,
    var difficulty: Int = 3,
    var studyHours: Double = 0.0,
    var status: String = "PENDING",
    var createdAt: Long = 0,
    var startAt: Long? = null,
    var endAt: Long? = null,
    var room: String? = null,
    var examType: String? = null,
    var syllabus: String? = null,
    var guideNumber: String? = null,
    var requiredTools: List<String> = emptyList(),
    var modality: String? = null,
    var members: List<String> = emptyList(),
    var phases: List<PhaseData> = emptyList(),
    var rubric: List<RubricItemData> = emptyList(),
)

@IgnoreExtraProperties
data class QuestionDto(
    var courseId: String = "",
    var evaluationId: String? = null,
    var statement: String = "",
    var scenario: String? = null,
    var options: List<String> = emptyList(),
    var correctOptionIndex: Int = 0,
    var explanation: String = "",
    var source: String = "AI",
    var createdAt: Long = 0,
    var reportReason: String? = null,
)

@IgnoreExtraProperties
data class SimulacrumDto(
    var courseId: String = "",
    var evaluationId: String? = null,
    var questionIds: List<String> = emptyList(),
    var answers: List<AnswerData> = emptyList(),
    var timeLimitMinutes: Int = 20,
    var startedAt: Long = 0,
    var finishedAt: Long? = null,
)

@IgnoreExtraProperties
data class AIRecommendationDto(
    var type: String = "STUDY_TIPS",
    var content: String = "",
    var courseId: String? = null,
    var evaluationId: String? = null,
    var stressLevel: Int? = null,
    var createdAt: Long = 0,
)

@IgnoreExtraProperties
data class PomodoroSessionDto(
    var courseId: String? = null,
    var evaluationId: String? = null,
    var startedAt: Long = 0,
    var endedAt: Long? = null,
    var focusMinutes: Int = 25,
    var shortBreakMinutes: Int = 5,
    var longBreakMinutes: Int = 15,
    var completedCycles: Int = 0,
)

@IgnoreExtraProperties
data class StudyBlockDto(
    var courseId: String = "",
    var evaluationId: String? = null,
    var startAt: Long = 0,
    var endAt: Long = 0,
    var status: String = "SUGGESTED",
)

@IgnoreExtraProperties
data class StressCheckInDto(
    var level: Int = 1,
    var createdAt: Long = 0,
)
