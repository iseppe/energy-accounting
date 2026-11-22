package com.empathie.energyaccounting.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

internal sealed interface ReleaseNoteBlock {
    data class Heading(val level: Int, val text: String) : ReleaseNoteBlock
    data class Paragraph(val text: String) : ReleaseNoteBlock
    data class Bullet(val text: String) : ReleaseNoteBlock
}

/**
 * Parses the small Markdown subset used by this project's release notes.
 *
 * A leading H1 is the release title and is intentionally skipped; the update sheet already shows
 * the version. Inline emphasis and links stay in the block text and are rendered by
 * [renderInlineMarkdown].
 */
internal fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> {
    val blocks = mutableListOf<ReleaseNoteBlock>()
    val paragraph = StringBuilder()
    val bullet = StringBuilder()
    var skipLeadingH1 = true

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += ReleaseNoteBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    fun flushBullet() {
        if (bullet.isNotEmpty()) {
            blocks += ReleaseNoteBlock.Bullet(bullet.toString().trim())
            bullet.clear()
        }
    }

    markdown.lineSequence().forEach { rawLine ->
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            flushBullet()
            flushParagraph()
            return@forEach
        }

        val heading = Regex("^(#{1,6})\\s+(.*)$").matchEntire(trimmed)
        if (heading != null) {
            flushBullet()
            flushParagraph()
            val level = heading.groupValues[1].length
            val text = heading.groupValues[2].trim()
            if (skipLeadingH1 && level == 1) {
                skipLeadingH1 = false
                return@forEach
            }
            skipLeadingH1 = false
            if (text.isNotEmpty()) {
                blocks += ReleaseNoteBlock.Heading(level.coerceAtMost(3), text)
            }
            return@forEach
        }

        skipLeadingH1 = false
        val bulletMatch = Regex("^[-*]\\s+(.*)$").matchEntire(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            flushBullet()
            bullet.append(bulletMatch.groupValues[1])
            return@forEach
        }

        if (bullet.isNotEmpty()) {
            bullet.append(' ').append(trimmed)
        } else {
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(trimmed)
        }
    }

    flushBullet()
    flushParagraph()
    return blocks
}

/**
 * Renders the inline subset: `**bold**` and `[label](url)`. Malformed markers stay literal.
 */
internal fun renderInlineMarkdown(
    text: String,
    linkColor: Color = Color.Unspecified,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val boldStart = text.indexOf("**", index)
        val linkStart = text.indexOf('[', index)
        val codeStart = text.indexOf('`', index)
        val next = listOf(boldStart, linkStart, codeStart)
            .filter { it >= 0 }
            .minOrNull()
        if (next == null) {
            append(text.substring(index))
            break
        }
        if (next > index) append(text.substring(index, next))

        if (next == boldStart) {
            val boldEnd = text.indexOf("**", boldStart + 2)
            if (boldEnd < 0) {
                append(text.substring(boldStart))
                break
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
            append(text.substring(boldStart + 2, boldEnd))
            pop()
            index = boldEnd + 2
        } else if (next == codeStart) {
            val codeEnd = text.indexOf('`', codeStart + 1)
            if (codeEnd < 0) {
                append(text.substring(codeStart))
                break
            }
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
            append(text.substring(codeStart + 1, codeEnd))
            pop()
            index = codeEnd + 1
        } else {
            val labelEnd = text.indexOf(']', linkStart + 1)
            val urlStart = if (labelEnd >= 0) text.indexOf('(', labelEnd + 1) else -1
            val urlEnd = if (urlStart >= 0) text.indexOf(')', urlStart + 1) else -1
            if (labelEnd < 0 || urlStart < 0 || urlEnd < 0) {
                append(text.substring(linkStart, if (labelEnd >= 0) labelEnd + 1 else linkStart + 1))
                index = if (labelEnd >= 0) labelEnd + 1 else linkStart + 1
                continue
            }
            val label = text.substring(linkStart + 1, labelEnd)
            val url = text.substring(urlStart + 1, urlEnd)
            val styles = if (linkColor != Color.Unspecified) {
                TextLinkStyles(style = SpanStyle(color = linkColor))
            } else {
                null
            }
            withLink(LinkAnnotation.Url(url, styles)) {
                append(label)
            }
            index = urlEnd + 1
        }
    }
}

@Composable
internal fun ReleaseNotesMarkdown(
    notes: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(notes) { parseReleaseNotes(notes) }
    val linkColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNoteBlock.Heading -> Text(
                    text = renderInlineMarkdown(block.text, linkColor),
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )

                is ReleaseNoteBlock.Paragraph -> Text(
                    text = renderInlineMarkdown(block.text, linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )

                is ReleaseNoteBlock.Bullet -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = renderInlineMarkdown(block.text, linkColor),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                    )
                }
            }
        }
    }
}
