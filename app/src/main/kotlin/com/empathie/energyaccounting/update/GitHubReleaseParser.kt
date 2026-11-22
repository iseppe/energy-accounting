package com.empathie.energyaccounting.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns the JSON of a single GitHub release (as returned by the releases API) into [UpdateInfo].
 *
 * The parser is deliberately strict about anything that affects security: the APK and the release
 * page must be served over HTTPS, and a digest is trusted only when it is a complete SHA-256.
 * Any release it cannot fully understand yields `null`, so the caller can treat "unknown" as
 * "no update" instead of guessing.
 */
object GitHubReleaseParser {

    /** GitHub's release body can be arbitrarily long; the notes UI only ever shows this much. */
    private const val MAX_RELEASE_NOTES = 8_000

    private const val HTTPS_PREFIX = "https://"

    private const val CHECKSUMS_ASSET_NAME = "SHA256SUMS"

    private val DIGEST_PATTERN = Regex("""^sha256:[0-9a-fA-F]{64}$""")

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun parse(json: String): UpdateInfo? = runCatching {
        val release = jsonFormat.decodeFromString<GitHubReleaseDto>(json)

        if (release.draft || release.prerelease) return null

        val tagName = release.tagName?.takeIf { it.isNotBlank() } ?: return null
        val version = SemanticVersion.parse(tagName) ?: return null

        val releaseUrl = release.htmlUrl?.takeIf { it.isHttpsUrl() } ?: return null

        val apk = release.assets.firstOrNull { asset ->
            asset.name?.endsWith(".apk", ignoreCase = true) == true
        } ?: return null

        val apkName = apk.name ?: return null
        val apkUrl = apk.browserDownloadUrl?.takeIf { it.isHttpsUrl() } ?: return null

        val digest = apk.digest?.takeIf { DIGEST_PATTERN.matches(it) }?.substringAfter(':')
        val checksumsUrl = if (digest == null) {
            release.assets
                .firstOrNull { it.name == CHECKSUMS_ASSET_NAME }
                ?.browserDownloadUrl
                ?.takeIf { it.isHttpsUrl() }
        } else {
            null
        }

        UpdateInfo(
            version = version,
            tagName = tagName,
            releaseName = release.name?.takeIf { it.isNotBlank() } ?: tagName,
            releaseNotes = (release.body ?: "").take(MAX_RELEASE_NOTES),
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            apkAssetName = apkName,
            apkSizeBytes = apk.size,
            apkSha256 = digest?.lowercase() ?: "",
            checksumsUrl = checksumsUrl,
        )
    }.getOrNull()

    private fun String.isHttpsUrl(): Boolean = startsWith(HTTPS_PREFIX)

    @Serializable
    private data class GitHubReleaseDto(
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("body") val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("draft") val draft: Boolean = false,
        @SerialName("prerelease") val prerelease: Boolean = false,
        @SerialName("assets") val assets: List<GitHubAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubAssetDto(
        @SerialName("name") val name: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
        @SerialName("size") val size: Long = 0L,
        @SerialName("digest") val digest: String? = null,
        @SerialName("content_type") val contentType: String? = null,
    )
}
