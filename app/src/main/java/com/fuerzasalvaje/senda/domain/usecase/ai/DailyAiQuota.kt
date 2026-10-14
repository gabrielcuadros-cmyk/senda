package com.fuerzasalvaje.senda.domain.usecase.ai

import com.fuerzasalvaje.senda.domain.model.AiError
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.repository.AiLogger
import com.fuerzasalvaje.senda.domain.repository.AiQuota
import com.fuerzasalvaje.senda.domain.repository.AiUsageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily Gemini budget kept on this device. Logs "Gemini calls today: N" (SendaAI) after every call. At
 * [SendaRules.AI_DAILY_BACKGROUND_LIMIT] calls background work stops; a daily 429 rests the AI until midnight.
 */
@Singleton
class DailyAiQuota @Inject constructor(
    private val store: AiUsageStore,
    private val logger: AiLogger,
    private val clock: Clock,
) : AiQuota {

    private val mutex = Mutex()
    private val restingUntil = MutableStateFlow<Instant?>(null)
    @Volatile private var loaded = false

    private suspend fun load() {
        if (loaded) return
        restingUntil.value = store.quotaExhaustedUntil()
        loaded = true
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun resting(until: Instant?): Boolean = until != null && clock.instant().isBefore(until)

    override suspend fun beforeCall() {
        val until = restingUntil()
        if (resting(until)) throw AiError.Resting(until)
    }

    override suspend fun afterCall(): Int = mutex.withLock {
        store.addCall(today()).also { logger.info("Gemini calls today: $it") }
    }

    override suspend fun onDailyQuotaExhausted() {
        val midnight = today().plusDays(1).atStartOfDay(clock.zone).toInstant()
        store.setQuotaExhaustedUntil(midnight)
        restingUntil.value = midnight
        loaded = true
        logger.warn("Daily Gemini quota exhausted: the AI rests until $midnight")
    }

    override suspend fun isResting(): Boolean = resting(restingUntil())

    override suspend fun allowsBackground(): Boolean {
        if (isResting()) return false
        val calls = store.callsOn(today())
        if (calls >= SendaRules.AI_DAILY_BACKGROUND_LIMIT) {
            logger.info("Gemini calls today: $calls; background generation paused until tomorrow")
            return false
        }
        return true
    }

    override fun observeResting(): Flow<Boolean> = flow {
        load()
        emitAll(restingUntil.map(::resting))
    }

    private suspend fun restingUntil(): Instant? {
        load()
        return restingUntil.value
    }
}
