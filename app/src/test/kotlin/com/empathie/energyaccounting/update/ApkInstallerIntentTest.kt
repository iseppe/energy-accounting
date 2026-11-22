package com.empathie.energyaccounting.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApkInstallerIntentTest {

    @Test
    fun `install intent grants read access to an APK content URI`() {
        val installer = AndroidApkInstaller(ApplicationProvider.getApplicationContext())
        val intent = installer.installIntent(Uri.parse("content://com.empathie.energyaccounting.fileprovider/updates/app.apk"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `unknown sources intent targets this package`() {
        val installer = AndroidApkInstaller(ApplicationProvider.getApplicationContext())
        val intent = installer.unknownSourcesIntent()
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:com.empathie.energyaccounting", intent.data.toString())
    }
}
