package com.empathie.energyaccounting.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocaleHelperTest {

    @Test
    fun `SYSTEM restores the device locale after an in-app override`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val systemLocale = context.resources.configuration.locales.get(0)

        LocaleHelper.wrap(context, AppLanguage.ITALIAN)
        assertEquals(Locale.ITALIAN, Locale.getDefault())

        val restored = LocaleHelper.wrap(context, AppLanguage.SYSTEM)

        assertEquals(systemLocale, restored.resources.configuration.locales.get(0))
        assertEquals(systemLocale, Locale.getDefault())
    }
}
