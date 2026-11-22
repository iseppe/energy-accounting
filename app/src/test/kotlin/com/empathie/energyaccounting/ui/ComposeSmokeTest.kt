package com.empathie.energyaccounting.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposeSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rendersComposeContent() {
        compose.setContent { Text("Compose smoke test") }
        compose.onNodeWithText("Compose smoke test").assertIsDisplayed()
    }
}
