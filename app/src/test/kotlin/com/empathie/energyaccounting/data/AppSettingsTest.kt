package com.empathie.energyaccounting.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    @Test
    fun `theme preference migrates the v1 WARM_LIGHT value to LIGHT`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("empathie_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "WARM_LIGHT")
            .commit()

        assertEquals(ThemeMode.LIGHT, AppSettings(context).themeMode())
    }

    @Test
    fun `vibration defaults to enabled and developer mode defaults to disabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettings(context)

        assertTrue(settings.vibrationEnabled())
        assertFalse(settings.devModeEnabled())

        settings.setVibrationEnabled(false)
        settings.setDevModeEnabled(true)

        assertFalse(settings.vibrationEnabled())
        assertTrue(settings.devModeEnabled())
    }
}
