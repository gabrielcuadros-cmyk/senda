package com.fuerzasalvaje.senda.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

/** Local-only bookkeeping of the AI usage on this device (never synced). */
interface AiUsageStore {
    /** Gemini calls made on [date]; 0 for any other day than the last one counted. */
    suspend fun callsOn(date: LocalDate): Int

    /** Counts one more call on [date] and returns the new total. */
    suspend fun addCall(date: LocalDate): Int

    suspend fun quotaExhaustedUntil(): Instant?
    suspend fun setQuotaExhaustedUntil(until: Instant?)

    /** Hash of the course material (syllabus + PDF Markdown) the current question bank was generated from. */
    suspend fun materialHash(courseId: String): String?
    suspend fun setMaterialHash(courseId: String, hash: String)
}

/**
 * Daily budget of Gemini calls. Every request goes through [beforeCall] and [afterCall]; background work also asks
 * [allowsBackground] first, so the last calls of the day are kept for what the student asks for.
 */
interface AiQuota {
    /** Throws [com.fuerzasalvaje.senda.domain.model.AiError.Resting] while the daily quota is exhausted. */
    suspend fun beforeCall()

    /** Counts the call just made and returns today's total. */
    suspend fun afterCall(): Int

    /** A 429 per day: no more calls until local midnight. */
    suspend fun onDailyQuotaExhausted()

    suspend fun isResting(): Boolean

    /** False once today's calls reach the background limit, or while resting. */
    suspend fun allowsBackground(): Boolean

    fun observeResting(): Flow<Boolean>

    /** No limits: for tests and code paths that do not count calls. */
    object None : AiQuota {
        override suspend fun beforeCall() = Unit
        override suspend fun afterCall() = 0
        override suspend fun onDailyQuotaExhausted() = Unit
        override suspend fun isResting() = false
        override suspend fun allowsBackground() = true
        override fun observeResting(): Flow<Boolean> = flowOf(false)
    }
}
