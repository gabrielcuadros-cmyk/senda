package com.fuerzasalvaje.senda.domain.repository

/** Local copies of PDFs (syllabus, lab guides) in the app's internal storage (D-11). */
interface DocumentStorage {
    /** Copies the document behind [sourceUri] and returns the path of the local copy. */
    suspend fun importPdf(sourceUri: String): Result<String>

    suspend fun delete(path: String)
}
