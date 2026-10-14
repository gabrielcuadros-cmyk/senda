package com.fuerzasalvaje.senda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The small Markdown subset Gemini uses in tips: headings, bullets, numbered lists, bold, italic and code. */
sealed interface MarkdownBlock {
    data class Heading(val text: String) : MarkdownBlock
    data class Bullet(val marker: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data object Gap : MarkdownBlock
}

object Markdown {
    private val bullet = Regex("^\\s*[-*•]\\s+(.*)$")
    private val numbered = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
    private val heading = Regex("^\\s*#{1,6}\\s+(.*)$")
    private val inline = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|\\*(.+?)\\*|_(.+?)_|`(.+?)`")

    fun parse(text: String): List<MarkdownBlock> = text.lines()
        .map { line ->
            heading.matchEntire(line)?.let { return@map MarkdownBlock.Heading(it.groupValues[1]) }
            bullet.matchEntire(line)?.let { return@map MarkdownBlock.Bullet("•", it.groupValues[1]) }
            numbered.matchEntire(line)?.let { return@map MarkdownBlock.Bullet("${it.groupValues[1]}.", it.groupValues[2]) }
            if (line.isBlank()) MarkdownBlock.Gap else MarkdownBlock.Paragraph(line.trim())
        }
        // Collapse repeated blank lines and drop leading/trailing ones.
        .fold(mutableListOf<MarkdownBlock>()) { acc, block ->
            if (!(block == MarkdownBlock.Gap && (acc.isEmpty() || acc.last() == MarkdownBlock.Gap))) acc.add(block)
            acc
        }
        .dropLastWhile { it == MarkdownBlock.Gap }

    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        var index = 0
        for (match in inline.findAll(text)) {
            append(text.substring(index, match.range.first))
            val (bold, boldAlt, italic, italicAlt, code) = match.destructured
            when {
                bold.isNotEmpty() || boldAlt.isNotEmpty() ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold.ifEmpty { boldAlt }) }
                italic.isNotEmpty() || italicAlt.isNotEmpty() ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic.ifEmpty { italicAlt }) }
                else -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            }
            index = match.range.last + 1
        }
        append(text.substring(index))
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { Markdown.parse(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(Markdown.inline(block.text), style = MaterialTheme.typography.titleSmall)
                is MarkdownBlock.Paragraph -> Text(Markdown.inline(block.text), style = MaterialTheme.typography.bodyMedium)
                is MarkdownBlock.Bullet -> Row {
                    Text(block.marker, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp))
                    Text(Markdown.inline(block.text), style = MaterialTheme.typography.bodyMedium)
                }
                MarkdownBlock.Gap -> Spacer(Modifier.height(4.dp))
            }
        }
    }
}
