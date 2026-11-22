package com.empathie.energyaccounting.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {

    @Test
    fun `parses a valid release and selects the APK asset`() {
        val info = GitHubReleaseParser.parse(VALID_JSON)!!

        assertEquals(SemanticVersion(0, 4, 0), info.version)
        assertEquals("v0.4.0", info.tagName)
        assertEquals("Energy Accounting v0.4.0", info.releaseName)
        assertEquals("empathie-energy-accounting-v0.4.0.apk", info.apkAssetName)
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            info.apkSha256,
        )
        assertEquals(12345678L, info.apkSizeBytes)
        assertEquals(APK_URL, info.apkUrl)
        assertEquals(RELEASE_URL, info.releaseUrl)
        assertTrue(info.releaseNotes.contains("In-app updates"))
        assertNull(info.checksumsUrl)
    }

    @Test
    fun `keeps the checksums URL when no digest is present`() {
        val info = GitHubReleaseParser.parse(validJson(digest = ""))!!

        assertEquals("", info.apkSha256)
        assertEquals(SHA256SUMS_URL, info.checksumsUrl)
        assertEquals("empathie-energy-accounting-v0.4.0.apk", info.apkAssetName)
    }

    @Test
    fun `rejects releases with no APK`() {
        val json = validJson(includeApk = false)

        assertNull(GitHubReleaseParser.parse(json))
    }

    @Test
    fun `rejects drafts and prereleases`() {
        assertNull(GitHubReleaseParser.parse(validJson(draft = true)))
        assertNull(GitHubReleaseParser.parse(validJson(prerelease = true)))
    }

    @Test
    fun `rejects a non-HTTPS APK URL`() {
        val json = validJson(apkUrl = "http://example.com/empathie-energy-accounting-v0.4.0.apk")

        assertNull(GitHubReleaseParser.parse(json))
    }

    @Test
    fun `rejects a non-HTTPS release URL`() {
        val json = validJson(releaseUrl = "http://github.com/iseppe/energy-accounting/releases/tag/v0.4.0")

        assertNull(GitHubReleaseParser.parse(json))
    }

    @Test
    fun `rejects malformed JSON and junk input`() {
        assertNull(GitHubReleaseParser.parse("""{"tag_name": "v0.4.0", """))
        assertNull(GitHubReleaseParser.parse("not json at all"))
        assertNull(GitHubReleaseParser.parse(""))
        assertNull(GitHubReleaseParser.parse("{}"))
    }

    @Test
    fun `rejects an unparseable tag`() {
        assertNull(GitHubReleaseParser.parse(validJson(tagName = "nightly")))
    }

    @Test
    fun `ignores a malformed digest and falls back to the checksums asset`() {
        val info = GitHubReleaseParser.parse(validJson(digest = "sha256:abc"))!!

        assertEquals("", info.apkSha256)
        assertEquals(SHA256SUMS_URL, info.checksumsUrl)
    }

    @Test
    fun `truncates an enormous release body`() {
        val info = GitHubReleaseParser.parse(validJson(body = "x".repeat(20_000)))!!

        assertEquals(8_000, info.releaseNotes.length)
    }

    @Test
    fun `falls back to the tag when the release name is blank`() {
        val info = GitHubReleaseParser.parse(validJson(name = "  "))!!

        assertEquals("v0.4.0", info.releaseName)
    }

    private companion object {
        const val RELEASE_URL = "https://github.com/iseppe/energy-accounting/releases/tag/v0.4.0"
        const val APK_URL =
            "https://github.com/iseppe/energy-accounting/releases/download/v0.4.0/empathie-energy-accounting-v0.4.0.apk"
        const val SHA256SUMS_URL =
            "https://github.com/iseppe/energy-accounting/releases/download/v0.4.0/SHA256SUMS"
        const val SHA256_DIGEST =
            "sha256:0123456789ABCDEF0123456789abcdef0123456789ABCDEF0123456789abcdef"

        val VALID_JSON: String = validJson()

        fun validJson(
            tagName: String = "v0.4.0",
            name: String = "Energy Accounting v0.4.0",
            body: String = "## What's new\n- In-app updates",
            releaseUrl: String = RELEASE_URL,
            draft: Boolean = false,
            prerelease: Boolean = false,
            apkUrl: String = APK_URL,
            digest: String = SHA256_DIGEST,
            includeApk: Boolean = true,
        ): String {
            val assets = buildList {
                if (includeApk) {
                    add(
                        """
                        {
                          "name": "empathie-energy-accounting-v0.4.0.apk",
                          "browser_download_url": "$apkUrl",
                          "size": 12345678,
                          "digest": ${digest.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "null"},
                          "content_type": "application/vnd.android.package-archive"
                        }
                        """.trimIndent(),
                    )
                }
                add(
                    """
                    {
                      "name": "SHA256SUMS",
                      "browser_download_url": "$SHA256SUMS_URL",
                      "size": 98,
                      "digest": null,
                      "content_type": "text/plain"
                    }
                    """.trimIndent(),
                )
            }

            return """
                {
                  "tag_name": "$tagName",
                  "name": "$name",
                  "body": ${jsonString(body)},
                  "html_url": "$releaseUrl",
                  "draft": $draft,
                  "prerelease": $prerelease,
                  "assets": [
                    ${assets.joinToString(",\n        ")}
                  ]
                }
            """.trimIndent()
        }

        fun jsonString(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}
