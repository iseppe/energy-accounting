package com.empathie.energyaccounting.update

import java.io.File

/**
 * Where the updater learns about releases and downloads their APKs.
 *
 * Implementations only return updates they can verify: a release without usable verification
 * material (a digest or a checksums file) must yield `null` from [latestRelease] rather than an
 * unverifiable [UpdateInfo].
 */
interface ReleaseSource {

    /** The newest release worth offering, or `null` when there is none or it cannot be trusted. */
    suspend fun latestRelease(): UpdateInfo?

    /** Downloads [info]'s APK to [destination], reporting cumulative progress as it streams. */
    suspend fun downloadApk(
        info: UpdateInfo,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    )
}
