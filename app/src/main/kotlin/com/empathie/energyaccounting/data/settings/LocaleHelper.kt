package com.empathie.energyaccounting.data.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the user's in-app language choice to a [Context].
 *
 * A configuration context works on every supported API level (26+); it overrides both
 * string resources and locale-sensitive formatting such as weekday names.
 */
object LocaleHelper {

    fun wrap(context: Context, language: AppLanguage): Context {
        val tag = language.tag ?: return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
