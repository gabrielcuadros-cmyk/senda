package com.fuerzasalvaje.senda.ui

import com.fuerzasalvaje.senda.ui.components.Markdown
import com.fuerzasalvaje.senda.ui.components.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {

    @Test
    fun `parses headings, bullets, numbered items and collapses blank lines`() {
        val blocks = Markdown.parse("## Hoy\n\n\n- Repasa **Física**\n* Pausa\n2. Duerme bien\nTexto final\n\n")

        assertEquals(
            listOf(
                MarkdownBlock.Heading("Hoy"),
                MarkdownBlock.Gap,
                MarkdownBlock.Bullet("•", "Repasa **Física**"),
                MarkdownBlock.Bullet("•", "Pausa"),
                MarkdownBlock.Bullet("2.", "Duerme bien"),
                MarkdownBlock.Paragraph("Texto final"),
            ),
            blocks,
        )
    }

    @Test
    fun `inline markers are removed from the visible text`() {
        assertEquals("Repasa Física y luego descansa ya", Markdown.inline("Repasa **Física** y *luego* descansa `ya`").text)
    }
}
