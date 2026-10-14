package com.fuerzasalvaje.senda.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Wipes the offline cache so the next student never sees the previous one's data. */
@Singleton
class LocalDataCleaner @Inject constructor(private val database: SendaDatabase) {
    suspend fun clear() = withContext(Dispatchers.IO) { database.clearAllTables() }
}
