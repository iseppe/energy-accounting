package com.empathie.energyaccounting.data.settings

import android.content.Context
import com.empathie.energyaccounting.ui.theme.ThemeMode

/**
 * Tiny on-device preference store for the appearance choice.
 *
 * Data backups deliberately exclude this: it is device ergonomics, not clinical data.
 */
class AppSettings(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode {
        val stored = preferences.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "empathie_settings"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
