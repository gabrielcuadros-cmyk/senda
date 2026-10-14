package com.fuerzasalvaje.senda.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fuerzasalvaje.senda.data.model.AnswerData
import com.fuerzasalvaje.senda.data.model.ClassSlotData
import com.fuerzasalvaje.senda.data.model.PhaseData
import com.fuerzasalvaje.senda.data.model.RubricItemData

// Every row carries ownerUid so the cache never mixes data of different students.
// Times are epoch milliseconds; enums are stored by name.

@Entity(tableName = "courses", indices = [Index("ownerUid")])
data class CourseEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val name: String,
    val teacher: String?,
    val importance: String,
    val colorArgb: Long,
    val schedule: List<ClassSlotData>,
    val createdAt: Long,
)

/** Single table for the three evaluation types; type-specific columns are nullable. */
@Entity(tableName = "evaluations", indices = [Index("ownerUid"), Index("ownerUid", "courseId")])
data class EvaluationEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val courseId: String,
    val type: String,
    val title: String,
    val weightPercent: Double,
    val difficulty: Int,
    val studyHours: Double,
    val status: String,
    val createdAt: Long,
    /** Exam start or lab report deadline. */
    val startAt: Long?,
    val endAt: Long?,
    // Exam
    val room: String?,
    val examType: String?,
    val syllabus: String?,
    val syllabusPdfPath: String?,
    // Lab
    val guideNumber: String?,
    val requiredTools: List<String>,
    val modality: String?,
    val guidePdfPath: String?,
    // Project
    val members: List<String>,
    val phases: List<PhaseData>,
    val rubric: List<RubricItemData>,
)

@Entity(tableName = "questions", indices = [Index("ownerUid"), Index("ownerUid", "courseId")])
data class QuestionEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val courseId: String,
    val evaluationId: String?,
    val statement: String,
    val scenario: String?,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val source: String,
    val createdAt: Long,
    /** QuestionReport name when the student reported it (schema v2). */
    val reportReason: String? = null,
)

@Entity(tableName = "simulacra", indices = [Index("ownerUid"), Index("ownerUid", "courseId")])
data class SimulacrumEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val courseId: String,
    val evaluationId: String?,
    val questionIds: List<String>,
    val answers: List<AnswerData>,
    val timeLimitMinutes: Int,
    val startedAt: Long,
    val finishedAt: Long?,
)

@Entity(tableName = "ai_recommendations", indices = [Index("ownerUid")])
data class AIRecommendationEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val type: String,
    val content: String,
    val courseId: String?,
    val evaluationId: String?,
    val stressLevel: Int?,
    val createdAt: Long,
)

@Entity(tableName = "pomodoro_sessions", indices = [Index("ownerUid")])
data class PomodoroSessionEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val courseId: String?,
    val evaluationId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val completedCycles: Int,
)

@Entity(tableName = "study_blocks", indices = [Index("ownerUid")])
data class StudyBlockEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val courseId: String,
    val evaluationId: String?,
    val startAt: Long,
    val endAt: Long,
    val status: String,
)

/** Primary key is ownerUid + ISO date: one check-in per student and day. */
@Entity(tableName = "stress_checkins", primaryKeys = ["ownerUid", "id"])
data class StressCheckInEntity(
    val id: String,
    val ownerUid: String,
    val level: Int,
    val createdAt: Long,
)

/** Study summary of an evaluation's PDF (id = evaluation id). [extractedText] stays on this device. */
@Entity(tableName = "pdf_digests", indices = [Index("ownerUid"), Index("courseId")])
data class PdfDigestEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val courseId: String,
    val pdfKey: String,
    val status: String,
    val pageCount: Int,
    val extractedChars: Int,
    val scanned: Boolean,
    val summary: String,
    val updatedAt: Long,
    val extractedText: String?,
)
