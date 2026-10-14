package com.fuerzasalvaje.senda.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Wipes the offline cache and the local PDF copies so the next student never sees the previous one's data. */
@Singleton
class LocalDataCleaner @Inject constructor(
    private val database: SendaDatabase,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun clear() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        File(context.filesDir, LocalDocumentStorage.DIRECTORY).deleteRecursively()
    }
}
