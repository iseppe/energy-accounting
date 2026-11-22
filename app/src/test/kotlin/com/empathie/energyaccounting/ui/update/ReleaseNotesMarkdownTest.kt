package com.empathie.energyaccounting.ui.update

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesMarkdownTest {

    private val notes = """
        # Energy Accounting v0.4.1

        ## Launcher icon

        - **Why the icon was missing** — the old vector failed to inflate.
          It fell back to the default icon.
        - **Verified by drawing it** — the test now draws the icon.

        The dialog should show **formatted** notes and a [release link](https://example.com).
    """.trimIndent()

    @Test
    fun `parses headings bullets and paragraphs without the leading title`() {
        val blocks = parseReleaseNotes(notes)

        assertEquals(ReleaseNoteBlock.Heading(2, "Launcher icon"), blocks[0])
        assertEquals(
            ReleaseNoteBlock.Bullet(
                "**Why the icon was missing** — the old vector failed to inflate. " +
                    "It fell back to the default icon."
            ),
            blocks[1],
        )
        assertEquals(
            ReleaseNoteBlock.Bullet("**Verified by drawing it** — the test now draws the icon."),
            blocks[2],
        )
        val paragraph = blocks[3] as ReleaseNoteBlock.Paragraph
        assertTrue(paragraph.text.startsWith("The dialog should show **formatted** notes"))
        assertTrue(blocks.none { it is ReleaseNoteBlock.Heading && it.level == 1 })
    }

    @Test
    fun `renders inline code without backticks`() {
        val annotated = renderInlineMarkdown("Verify `SHA256SUMS` before installing.")

        assertEquals("Verify SHA256SUMS before installing.", annotated.text)
        assertTrue(
            annotated.spanStyles.any { it.item.fontFamily == FontFamily.Monospace }
        )
    }

    @Test
    fun `renders bold spans and link annotations`() {
        val annotated = renderInlineMarkdown(
            "The **formatted** [release](https://example.com)."
        )

        assertEquals("The formatted release.", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertTrue(
            links.any {
                val link = it.item
                link is LinkAnnotation.Url && link.url == "https://example.com"
            }
        )
    }
}
