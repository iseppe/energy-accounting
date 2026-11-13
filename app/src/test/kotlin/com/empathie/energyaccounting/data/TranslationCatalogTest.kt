package com.empathie.energyaccounting.data

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TranslationCatalogTest {

    @Test
    fun `translations_json asset contains non-blank en and it strings for every key and generates valid Android resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawJson = context.assets.open("translations.json").bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(rawJson).jsonObject
        assertTrue("Expected at least 85 translation keys", root.size >= 85)

        root.forEach { (key, element) ->
            val obj = element.jsonObject
            val en = obj["en"]?.jsonPrimitive?.content
            val it = obj["it"]?.jsonPrimitive?.content
            assertFalse("Missing 'en' for key '$key'", en.isNullOrBlank())
            assertFalse("Missing 'it' for key '$key'", it.isNullOrBlank())
        }

        val enConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val itConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ITALIAN) }
        val enCtx = context.createConfigurationContext(enConfig)
        val itCtx = context.createConfigurationContext(itConfig)

        assertEquals("Manage & Export", enCtx.getString(R.string.tab_manage))
        assertEquals(
            "Update default template for \"sveglia\"",
            enCtx.getString(R.string.update_default_template, "sveglia"),
        )
        assertTrue(itCtx.getString(R.string.reminder_permission_rationale).contains("all'app"))
        assertEquals("Condividi PDF", itCtx.getString(R.string.share_pdf))
    }
}
