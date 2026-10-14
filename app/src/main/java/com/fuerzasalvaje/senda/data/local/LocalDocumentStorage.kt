package com.fuerzasalvaje.senda.data.local

import android.content.Context
import androidx.core.net.toUri
import com.fuerzasalvaje.senda.domain.repository.DocumentStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Copies picked PDFs into filesDir/pdfs; they never leave the device (D-11). */
@Singleton
class LocalDocumentStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentStorage {

    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    override suspend fun importPdf(sourceUri: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(directory, "${UUID.randomUUID()}.pdf")
            val input = context.contentResolver.openInputStream(sourceUri.toUri())
                ?: error("Cannot open the selected file")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            target.absolutePath
        }
    }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            val file = File(path)
            // Only files created by this class can be removed.
            if (file.parentFile == directory) file.delete()
        }
    }

    companion object {
        const val DIRECTORY = "pdfs"
    }
}
