package com.fuerzasalvaje.senda.domain.model

/** Text read from a PDF on the device, one entry per page. [slides] is true for presentations (landscape pages). */
data class PdfText(val pages: List<String>, val slides: Boolean = false) {
    val pageCount: Int get() = pages.size
    val chars: Int get() = pages.sumOf { it.trim().length }

    /** Scanned or image-only PDFs have almost no text layer: they are never sent to the AI. */
    val hasReadableText: Boolean get() = chars >= MIN_TEXT_CHARS

    companion object {
        /** Below this the PDF is treated as scanned (no readable text). */
        const val MIN_TEXT_CHARS = 200

        /** Most pages in landscape means a presentation: its sections are titled "Diapositiva N". */
        fun of(pages: List<String>, landscapePages: Int) = PdfText(pages, slides = landscapePages * 2 > pages.size)
    }
}

/** Markdown that fits the AI budget; [summarized] is true when something had to be cut. */
data class FittedMarkdown(val text: String, val summarized: Boolean)
