package com.empathie.energyaccounting.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Executes the real [AndroidApkVerifier] against real APK files on a device.
 *
 * The release APK must be pushed before running this class:
 * `adb push <release.apk> /sdcard/Android/data/<package>/files/release.apk`
 * (where `<package>` is the installed test application package). The test fails if it is
 * missing, because a skipped signature check would not be evidence that the verifier works.
 */
@RunWith(AndroidJUnit4::class)
class ApkVerifierInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val verifier = AndroidApkVerifier(context)

    @Test
    fun installedAppApkIsValid() {
        val apk = installedApk()
        val result = verifier.verify(apk, Sha256.of(apk), context.packageName)

        assertEquals(VerificationResult.Valid, result)
    }

    @Test
    fun wrongPackageIsRejected() {
        val apk = installedApk()
        val result = verifier.verify(apk, Sha256.of(apk), "com.example.wrong")

        assertEquals(VerificationResult.Invalid(VerificationError.PACKAGE), result)
    }

    @Test
    fun truncatedApkIsUnreadable() {
        val corrupt = File(context.cacheDir, "corrupt.apk")
        installedApk().inputStream().use { input ->
            corrupt.outputStream().use { output -> input.copyTo(output) }
        }
        RandomAccessFile(corrupt, "rw").use { it.setLength(1024L) }

        val result = verifier.verify(corrupt, Sha256.of(corrupt), context.packageName)

        assertEquals(VerificationResult.Invalid(VerificationError.UNREADABLE), result)
    }

    @Test
    fun releaseApkHasADifferentSignature() {
        val release = File(context.getExternalFilesDir(null), "release.apk")
        assertTrue(
            "Push the published v0.4.0 APK to ${release.absolutePath} before running this test",
            release.isFile,
        )
        // Same package name as the installed test app, but signed with the release key.
        val archiveInfo = context.packageManager.getPackageArchiveInfo(release.absolutePath, 0)
        assertNotNull("Could not read the release APK package", archiveInfo)
        val releasePackage = archiveInfo!!.packageName

        val result = verifier.verify(release, Sha256.of(release), releasePackage)

        assertEquals(VerificationResult.Invalid(VerificationError.SIGNATURE), result)
    }

    private fun installedApk(): File {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        return File(info.sourceDir)
    }
}
