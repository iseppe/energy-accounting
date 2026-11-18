package com.empathie.energyaccounting.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IsolatedComposeSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun testRunsInSeparateApplication() {
        assertEquals("com.empathie.energyaccounting.uitest", ApplicationProvider.getApplicationContext<Context>().packageName)
        compose.setContent { Text("Isolated Compose test") }
        compose.onNodeWithText("Isolated Compose test").assertIsDisplayed()
    }
}
