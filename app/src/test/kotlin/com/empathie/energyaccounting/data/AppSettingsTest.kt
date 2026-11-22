package com.empathie.energyaccounting.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `update preferences default to automatic, never checked and no snooze`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("empathie_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = AppSettings(context)

        assertTrue(settings.updateChecksEnabled())
        assertEquals(0L, settings.lastUpdateCheckEpochMillis())
        assertNull(settings.snoozedUpdateVersion())
        assertEquals(0L, settings.snoozedUpdateUntilEpochMillis())
    }

    @Test
    fun `notification permission prompt flag defaults to false and persists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("empathie_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = AppSettings(context)

        assertFalse(settings.notificationPermissionPrompted())

        settings.setNotificationPermissionPrompted(true)

        assertTrue(AppSettings(context).notificationPermissionPrompted())
    }

    @Test
    fun `update preferences persist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("empathie_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = AppSettings(context)

        settings.setUpdateChecksEnabled(false)
        settings.setLastUpdateCheckEpochMillis(1234L)
        settings.snoozeUpdate("0.4.0", 5678L)

        assertFalse(AppSettings(context).updateChecksEnabled())
        assertEquals(1234L, AppSettings(context).lastUpdateCheckEpochMillis())
        assertEquals("0.4.0", AppSettings(context).snoozedUpdateVersion())
        assertEquals(5678L, AppSettings(context).snoozedUpdateUntilEpochMillis())

        settings.clearUpdateSnooze()
        assertNull(settings.snoozedUpdateVersion())
        assertEquals(0L, settings.snoozedUpdateUntilEpochMillis())
    }
}
