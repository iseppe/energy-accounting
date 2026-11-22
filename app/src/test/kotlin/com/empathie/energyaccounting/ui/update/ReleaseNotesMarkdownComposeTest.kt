package com.empathie.energyaccounting.ui.update

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReleaseNotesMarkdownComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val notes = """
        # Energy Accounting v0.4.1

        ## Launcher icon

        - **Why the icon was missing** — the old vector failed to inflate.
        - **Verified by drawing it** — the test now draws the icon.

        Verify `SHA256SUMS` before installing.
    """.trimIndent()

    @Test
    fun `renders formatted notes without raw markers`() {
        compose.setContent {
            EmpathieTheme {
                ReleaseNotesMarkdown(notes)
            }
        }

        compose.onNodeWithText("Launcher icon").assertExists()
        compose.onNodeWithText("Why the icon was missing", substring = true).assertExists()
        compose.onNodeWithText("# Energy Accounting", substring = true).assertDoesNotExist()
        compose.onNodeWithText("**", substring = true).assertDoesNotExist()
        compose.onNodeWithText("`", substring = true).assertDoesNotExist()
    }
}
