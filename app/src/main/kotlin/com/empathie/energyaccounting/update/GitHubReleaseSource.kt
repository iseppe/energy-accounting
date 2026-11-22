package com.empathie.energyaccounting.update

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * [ReleaseSource] backed by GitHub's releases API.
 *
 * This class is the gate that refuses an unverifiable update: it only returns a release whose APK
 * can be checked against a SHA-256 digest, resolving one from the release's `SHA256SUMS` asset when
 * GitHub does not publish a digest. A release with no digest and no usable checksums file yields
 * `null` instead of an update nobody can verify.
 */
class GitHubReleaseSource(
    private val repo: String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val userAgent: String = "EmpathieEnergyAccounting",
) : ReleaseSource {

    override suspend fun latestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = try {
            transport.getText(releasesUrl, apiHeaders())
        } catch (e: HttpStatusException) {
            // A repository without releases is a normal "no update", every other failure is not.
            if (e.code == 404) return@withContext null else throw e
        }

        val info = GitHubReleaseParser.parse(json) ?: return@withContext null
        if (info.apkSha256.isNotBlank()) return@withContext info

        val checksumsUrl = info.checksumsUrl ?: return@withContext null
        val hash = resolveHashFromChecksums(checksumsUrl, info.apkAssetName) ?: return@withContext null
        info.copy(apkSha256 = hash)
    }

    override suspend fun downloadApk(
        info: UpdateInfo,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        transport.download(info.apkUrl, destination) { downloaded ->
            context.ensureActive()
            onProgress(downloaded, info.apkSizeBytes)
        }
        if (info.apkSizeBytes > 0 && destination.length() != info.apkSizeBytes) {
            throw IOException(
                "Downloaded ${destination.length()} bytes for ${info.apkAssetName}, expected ${info.apkSizeBytes}",
            )
        }
    }

    /**
     * Fetches the `SHA256SUMS` asset and extracts the APK's hash, or `null` when the file cannot be
     * fetched, is oversized, or has no trustworthy entry for [apkAssetName].
     */
    private fun resolveHashFromChecksums(checksumsUrl: String, apkAssetName: String): String? {
        val text = try {
            transport.getText(checksumsUrl, mapOf("User-Agent" to userAgent))
        } catch (e: IOException) {
            // Without the checksums file there is nothing to verify against, so there is no update.
            return null
        }
        return hashFromChecksums(text, apkAssetName)
    }

    /**
     * Parses `sha256sum`-style `<hash>  <name>` lines (CRLF tolerated, an optional `*` marks binary
     * mode) and returns [apkAssetName]'s hash lowercased. A malformed hash on the APK's own line
     * fails closed instead of being skipped.
     */
    private fun hashFromChecksums(text: String, apkAssetName: String): String? {
        if (text.length > MAX_CHECKSUMS_CHARS) return null
        for (line in text.lineSequence()) {
            val match = CHECKSUM_LINE.matchEntire(line.trimEnd()) ?: continue
            val hash = match.groupValues[1]
            val name = match.groupValues[2]
            if (name == apkAssetName) {
                return hash.takeIf { SHA256_HEX.matches(it) }?.lowercase()
            }
        }
        return null
    }

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to userAgent,
    )

    private val releasesUrl: String
        get() = "https://api.github.com/repos/$repo/releases/latest"

    private companion object {
        /** A checksums file is a few dozen lines; anything bigger is not one we should trust. */
        const val MAX_CHECKSUMS_CHARS = 64 * 1024

        val CHECKSUM_LINE = Regex("""^(\S+)[ \t]+\*?(.+)$""")
        val SHA256_HEX = Regex("""^[0-9a-fA-F]{64}$""")
    }
}
