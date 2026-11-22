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
        // v0.1.0 persisted the old enum name; keep that user's choice after the rename.
        if (stored == "WARM_LIGHT") return ThemeMode.LIGHT
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

    fun vibrationEnabled(): Boolean =
        preferences.getBoolean(KEY_VIBRATION, true)

    fun setVibrationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun devModeEnabled(): Boolean =
        preferences.getBoolean(KEY_DEV_MODE, false)

    fun setDevModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DEV_MODE, enabled).apply()
    }

    fun updateChecksEnabled(): Boolean =
        preferences.getBoolean(KEY_UPDATE_CHECKS_ENABLED, true)

    fun setUpdateChecksEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_UPDATE_CHECKS_ENABLED, enabled).apply()
    }

    fun lastUpdateCheckEpochMillis(): Long =
        preferences.getLong(KEY_LAST_UPDATE_CHECK_EPOCH, 0L)

    fun setLastUpdateCheckEpochMillis(epochMillis: Long) {
        preferences.edit().putLong(KEY_LAST_UPDATE_CHECK_EPOCH, epochMillis).apply()
    }

    fun snoozedUpdateVersion(): String? =
        preferences.getString(KEY_SNOOZED_UPDATE_VERSION, null)

    fun snoozedUpdateUntilEpochMillis(): Long =
        preferences.getLong(KEY_SNOOZED_UPDATE_UNTIL, 0L)

    fun snoozeUpdate(version: String, untilEpochMillis: Long) {
        preferences.edit()
            .putString(KEY_SNOOZED_UPDATE_VERSION, version)
            .putLong(KEY_SNOOZED_UPDATE_UNTIL, untilEpochMillis)
            .apply()
    }

    fun clearUpdateSnooze() {
        preferences.edit()
            .remove(KEY_SNOOZED_UPDATE_VERSION)
            .remove(KEY_SNOOZED_UPDATE_UNTIL)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "empathie_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_DEV_MODE = "dev_mode_enabled"
        const val KEY_UPDATE_CHECKS_ENABLED = "update_checks_enabled"
        const val KEY_LAST_UPDATE_CHECK_EPOCH = "last_update_check_epoch"
        const val KEY_SNOOZED_UPDATE_VERSION = "snoozed_update_version"
        const val KEY_SNOOZED_UPDATE_UNTIL = "snoozed_update_until"
    }
}
