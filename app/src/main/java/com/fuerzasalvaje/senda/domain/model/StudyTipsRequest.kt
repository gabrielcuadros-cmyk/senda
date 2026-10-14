package com.fuerzasalvaje.senda.domain.model

/**
 * Input for personalized study tips: the daily stress check-in plus the computed workload.
 */
data class StudyTipsRequest(
    /** Daily check-in value, 1 (calm) to 5 (very stressed). */
    val stressLevel: Int,
    val pendingItems: List<PendingStudyItem>,
)

data class PendingStudyItem(
    val title: String,
    val courseName: String,
    val priorityScore: Double,
    val hoursUntilDue: Long,
    val pendingStudyHours: Double,
)
