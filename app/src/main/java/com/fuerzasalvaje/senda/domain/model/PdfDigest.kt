package com.fuerzasalvaje.senda.domain.model

import java.time.Instant

/**
 * Where the study summary of a PDF stands. READING covers extraction and the first Gemini call; RETRYING means a
 * call failed and the worker will try again; FAILED is final (invalid key, or nothing readable in the file).
 */
enum class PdfDigestStatus { READING, READY, RETRYING, FAILED }

/** Text read from a PDF on the device, one entry per page (a slide in presentations). */
data class PdfText(val pages: List<String>) {
    val pageCount: Int get() = pages.size
    val chars: Int get() = pages.sumOf { it.trim().length }

    /** Scanned PDFs have no text layer: on average fewer than [MIN_CHARS_PER_PAGE] characters per page. */
    val isScanned: Boolean get() = pages.isEmpty() || chars < pages.size * MIN_CHARS_PER_PAGE

    /** The text with "[Página N]" markers, so the summary can cite where each point comes from. */
    fun marked(maxChars: Int = MAX_CHARS): String = buildString {
        pages.forEachIndexed { index, page ->
            val text = page.trim()
            if (text.isNotEmpty()) {
                appendLine("[Página ${index + 1}]")
                appendLine(text)
            }
        }
    }.take(maxChars)

    companion object {
        const val MIN_CHARS_PER_PAGE = 20

        /** About 75k tokens: enough for a full course PDF and still a single fast call. */
        const val MAX_CHARS = 300_000
    }
}

/**
 * Study summary of the PDF attached to an exam (syllabus) or a lab (guide), made once by Gemini. Questions are
 * generated from this text instead of sending the PDF every time. Synced to Firestore without [extractedText].
 */
data class PdfDigest(
    val evaluationId: String,
    val courseId: String,
    /** File name of the local copy. A new PDF gets a new name, so a digest of the previous one is stale. */
    val pdfKey: String,
    val status: PdfDigestStatus,
    val pageCount: Int = 0,
    val extractedChars: Int = 0,
    val scanned: Boolean = false,
    val summary: String = "",
    val updatedAt: Instant,
    /** Local only: text with page markers, empty for scanned PDFs. Null until extracted on this device. */
    val extractedText: String? = null,
) {
    val id: String get() = evaluationId
    val isReady: Boolean get() = status == PdfDigestStatus.READY && summary.isNotBlank()
    val isPending: Boolean get() = status == PdfDigestStatus.READING || status == PdfDigestStatus.RETRYING

    companion object {
        /** Firestore documents stop at 1 MB; a summary is a few thousand words. */
        const val MAX_SUMMARY_CHARS = 60_000
    }
}

/** Key that identifies a local PDF copy: its file name. */
fun pdfKeyOf(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

/** What a course card shows: reading first, then retrying, then failed; ready only when every PDF is ready. */
fun courseDigestStatus(digests: Collection<PdfDigest>): PdfDigestStatus? = when {
    digests.isEmpty() -> null
    digests.any { it.status == PdfDigestStatus.READING } -> PdfDigestStatus.READING
    digests.any { it.status == PdfDigestStatus.RETRYING } -> PdfDigestStatus.RETRYING
    digests.any { it.status == PdfDigestStatus.FAILED } -> PdfDigestStatus.FAILED
    else -> PdfDigestStatus.READY
}
