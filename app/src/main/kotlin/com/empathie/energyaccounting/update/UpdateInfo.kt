package com.empathie.energyaccounting.update

/**
 * Everything the updater needs to offer a release: which version it is, where to download the
 * APK from, and what to show the user before they install it.
 *
 * [apkSha256] is a lowercase hex digest when GitHub publishes one. Older releases only ship a
 * `SHA256SUMS` file, in which case [apkSha256] is blank and [checksumsUrl] points at it.
 */
data class UpdateInfo(
    val version: SemanticVersion,
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkAssetName: String,
    val apkSizeBytes: Long,
    val apkSha256: String,
    val checksumsUrl: String? = null,
)
