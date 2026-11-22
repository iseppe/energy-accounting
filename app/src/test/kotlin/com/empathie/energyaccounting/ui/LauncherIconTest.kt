package com.empathie.energyaccounting.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {

    @Test
    fun `manifest declares both launcher and round icons`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertNotEquals("application android:icon is missing", 0, info.icon)
        assertNotEquals("application android:roundIcon is missing", 0, roundIconRes(info))
    }

    /**
     * `ApplicationInfo.roundIcon` is a hidden framework field and is absent from the SDK
     * stubs, so read it reflectively from the runtime ApplicationInfo.
     */
    private fun roundIconRes(info: ApplicationInfo): Int =
        ApplicationInfo::class.java.getField("roundIconRes").getInt(info)
}
