package com.fuerzasalvaje.senda.data.sync

import com.fuerzasalvaje.senda.domain.repository.Ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Emits right away and then once a minute while collected. */
@Singleton
class MinuteTicker @Inject constructor() : Ticker {
    override val ticks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
}
