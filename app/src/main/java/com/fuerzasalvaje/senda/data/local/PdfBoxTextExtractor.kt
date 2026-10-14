package com.fuerzasalvaje.senda.data.local

import android.content.Context
import com.fuerzasalvaje.senda.domain.model.PdfText
import com.fuerzasalvaje.senda.domain.repository.PdfTextExtractor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Reads the text layer of a local PDF with PdfBox-Android, page by page, without leaving the device. */
@Singleton
class PdfBoxTextExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PdfTextExtractor {

    @Volatile private var initialized = false

    override suspend fun extract(path: String): Result<PdfText> = withContext(Dispatchers.IO) {
        try {
            if (!initialized) {
                PDFBoxResourceLoader.init(context)
                initialized = true
            }
            // Temp files instead of heap for big documents.
            PDDocument.load(File(path), MemoryUsageSetting.setupTempFileOnly()).use { document ->
                val stripper = PDFTextStripper()
                val pages = (1..document.numberOfPages).map { page ->
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document)
                }
                Result.success(PdfText(pages))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
