package com.fuerzasalvaje.senda.data.local

import android.content.Context
import androidx.core.content.edit
import com.fuerzasalvaje.senda.domain.repository.AiUsageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** AI usage counters in SharedPreferences: they belong to this device and its quota, not to the student's data. */
@Singleton
class PrefsAiUsageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AiUsageStore {

    private val prefs by lazy { context.getSharedPreferences(NAME, Context.MODE_PRIVATE) }

    override suspend fun callsOn(date: LocalDate): Int = io {
        if (prefs.getString(CALLS_DATE, null) == date.toString()) prefs.getInt(CALLS_COUNT, 0) else 0
    }

    override suspend fun addCall(date: LocalDate): Int = io {
        val total = (if (prefs.getString(CALLS_DATE, null) == date.toString()) prefs.getInt(CALLS_COUNT, 0) else 0) + 1
        prefs.edit(commit = true) {
            putString(CALLS_DATE, date.toString())
            putInt(CALLS_COUNT, total)
        }
        total
    }

    override suspend fun quotaExhaustedUntil(): Instant? = io {
        prefs.getLong(EXHAUSTED_UNTIL, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    }

    override suspend fun setQuotaExhaustedUntil(until: Instant?) = io {
        prefs.edit(commit = true) { if (until == null) remove(EXHAUSTED_UNTIL) else putLong(EXHAUSTED_UNTIL, until.toEpochMilli()) }
    }

    override suspend fun materialHash(courseId: String): String? = io { prefs.getString(HASH_PREFIX + courseId, null) }

    override suspend fun setMaterialHash(courseId: String, hash: String) = io {
        prefs.edit(commit = true) { putString(HASH_PREFIX + courseId, hash) }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val NAME = "senda_ai_usage"
        const val CALLS_DATE = "calls_date"
        const val CALLS_COUNT = "calls_count"
        const val EXHAUSTED_UNTIL = "exhausted_until"
        const val HASH_PREFIX = "bank_hash_"
    }
}
