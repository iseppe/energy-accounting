package com.empathie.energyaccounting.data.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Applies the user's in-app language choice to a [Context].
 *
 * A configuration context works on every supported API level (26+); it overrides both
 * string resources and locale-sensitive formatting such as weekday names. Selecting
 * [AppLanguage.SYSTEM] restores the device locale instead of leaving the previous
 * override in `Locale.getDefault()`.
 */
object LocaleHelper {

    fun wrap(context: Context, language: AppLanguage): Context {
        val locale = language.tag?.let(Locale::forLanguageTag) ?: systemLocale()
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun systemLocale(): Locale =
        Resources.getSystem().configuration.locales.get(0)
}
