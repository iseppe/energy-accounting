package com.empathie.energyaccounting.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Launches the system package installer for a downloaded APK, and reports whether the user
 * has allowed installs from this app (unknown sources).
 */
interface ApkInstaller {
    fun canInstall(): Boolean
    fun openInstallPermissionSettings()
    fun install(apk: File)
}

class AndroidApkInstaller(private val context: Context) : ApkInstaller {

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        context.startActivity(unknownSourcesIntent())
    }

    override fun install(apk: File) {
        val apkUri: Uri =
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(installIntent(apkUri))
    }

    internal fun installIntent(apkUri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    internal fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
