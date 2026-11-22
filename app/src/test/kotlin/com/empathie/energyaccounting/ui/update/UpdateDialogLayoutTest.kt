package com.empathie.energyaccounting.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import com.empathie.energyaccounting.update.SemanticVersion
import com.empathie.energyaccounting.update.UpdateInfo
import com.empathie.energyaccounting.update.UpdateUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateDialogLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `actions stay visible in a short sheet`() {
        val info = UpdateInfo(
            version = SemanticVersion(0, 4, 3),
            tagName = "v0.4.3",
            releaseName = "Energy Accounting v0.4.3",
            releaseNotes = buildString {
                appendLine("# Energy Accounting v0.4.3")
                appendLine()
                appendLine("This patch fixes the update sheet on short screens.")
                appendLine()
                appendLine("## Notes")
                repeat(8) { appendLine("- A release note that needs scrolling on a short window.") }
            },
            releaseUrl = "https://example.com/releases/v0.4.3",
            apkUrl = "https://example.com/app.apk",
            apkAssetName = "app.apk",
            apkSizeBytes = 1L,
            apkSha256 = "a".repeat(64),
        )

        compose.setContent {
            EmpathieTheme {
                Column(Modifier.height(320.dp)) {
                    AvailableContent(
                        state = UpdateUiState.Available(info),
                        notesModifier = Modifier.weight(1f, fill = false),
                        onLater = {},
                        onUpdate = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Later").assertIsDisplayed()
        compose.onNodeWithText("Update").assertIsDisplayed()
    }
}
