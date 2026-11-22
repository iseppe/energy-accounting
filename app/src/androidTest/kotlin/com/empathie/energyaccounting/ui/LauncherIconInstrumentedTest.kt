package com.empathie.energyaccounting.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LauncherIconInstrumentedTest {

    @Test
    fun launcherIconDrawsSlateSageAmberAndPaperPalette() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 108, 108)
        icon.draw(Canvas(bitmap))

        val pixels = IntArray(108 * 108)
        bitmap.getPixels(pixels, 0, 108, 0, 0, 108, 108)

        fun countNear(rgb: Int): Int = pixels.count { pixel ->
            abs(Color.red(pixel) - Color.red(rgb)) <= 28 &&
                abs(Color.green(pixel) - Color.green(rgb)) <= 28 &&
                abs(Color.blue(pixel) - Color.blue(rgb)) <= 28
        }

        assertTrue("slate background missing", countNear(0xFF131720.toInt()) > 1_000)
        assertTrue("sage hemisphere missing", countNear(0xFF5EC2A6.toInt()) > 80)
        assertTrue("amber hemisphere missing", countNear(0xFFE29B61.toInt()) > 80)
        assertTrue("paper bolt missing", countNear(0xFFF7F5F0.toInt()) > 80)
    }
}
