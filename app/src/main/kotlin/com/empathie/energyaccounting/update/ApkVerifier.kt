package com.empathie.energyaccounting.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Computes the SHA-256 digest of a file, streaming it in 64 KB chunks so large APKs never
 * load into memory at once. The result is lowercase hex.
 */
object Sha256 {

    private const val CHUNK_SIZE_BYTES = 64 * 1024

    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/** Outcome of checking a downloaded APK before it is offered to the system installer. */
sealed interface VerificationResult {
    data object Valid : VerificationResult
    data class Invalid(val error: VerificationError) : VerificationResult
}

enum class VerificationError { CHECKSUM, PACKAGE, SIGNATURE, UNREADABLE }

/**
 * Checks that a downloaded APK matches the release metadata (checksum), names the expected
 * package, and is signed with the same certificate as the installed app.
 */
interface ApkVerifier {
    fun verify(apk: File, expectedSha256: String, expectedPackageName: String): VerificationResult
}

class AndroidApkVerifier(private val context: Context) : ApkVerifier {

    override fun verify(
        apk: File,
        expectedSha256: String,
        expectedPackageName: String,
    ): VerificationResult {
        val actualSha256 = try {
            Sha256.of(apk)
        } catch (_: IOException) {
            return VerificationResult.Invalid(VerificationError.UNREADABLE)
        }
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            return VerificationResult.Invalid(VerificationError.CHECKSUM)
        }

        val packageManager = context.packageManager
        val archiveInfo = packageManager.getPackageArchiveInfo(apk.absolutePath, signingFlags())
            ?: return VerificationResult.Invalid(VerificationError.UNREADABLE)
        if (archiveInfo.packageName != expectedPackageName) {
            return VerificationResult.Invalid(VerificationError.PACKAGE)
        }

        val archiveSigner = firstCertificate(archiveInfo)
            ?: return VerificationResult.Invalid(VerificationError.SIGNATURE)
        val installedInfo = packageManager.getPackageInfo(context.packageName, signingFlags())
        val installedSigner = firstCertificate(installedInfo)
            ?: return VerificationResult.Invalid(VerificationError.SIGNATURE)
        if (!archiveSigner.contentEquals(installedSigner)) {
            return VerificationResult.Invalid(VerificationError.SIGNATURE)
        }

        return VerificationResult.Valid
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun firstCertificate(packageInfo: PackageInfo): ByteArray? {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        return signatures?.firstOrNull()?.toByteArray()
    }
}
