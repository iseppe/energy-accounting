package com.empathie.energyaccounting.data.settings

import android.content.Context
import com.empathie.energyaccounting.ui.theme.ThemeMode

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    ITALIAN("it"),
}

/**
 * Tiny on-device preference store for appearance and language.
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

    fun appLanguage(): AppLanguage {
        val stored = preferences.getString(KEY_LANGUAGE, null) ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.SYSTEM
    }

    fun setAppLanguage(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "empathie_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "app_language"
    }
}
