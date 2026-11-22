package com.empathie.energyaccounting.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubReleaseSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val transport = FakeTransport()

    private val source = GitHubReleaseSource(REPO, transport)

    @Test
    fun `latestRelease returns a release with the digest`() = runTest {
        transport.textResponses += validJson()

        val info = source.latestRelease()!!

        assertEquals(SemanticVersion(0, 4, 0), info.version)
        assertEquals("v0.4.0", info.tagName)
        assertEquals(APK_NAME, info.apkAssetName)
        assertEquals(APK_URL, info.apkUrl)
        assertEquals(APK_SIZE, info.apkSizeBytes)
        assertEquals(HASH, info.apkSha256)
        assertNull(info.checksumsUrl)
    }

    @Test
    fun `latestRelease resolves the hash from SHA256SUMS when the digest is absent`() = runTest {
        transport.textResponses += validJson(digest = null)
        transport.textResponses += "$HASH_UPPER *$APK_NAME\r\n$OTHER_HASH  other.apk\r\n"

        val info = source.latestRelease()!!

        assertEquals(HASH, info.apkSha256)
        assertEquals(SHA256SUMS_URL, info.checksumsUrl)
        assertEquals(SHA256SUMS_URL, transport.requests[1].url)
    }

    @Test
    fun `latestRelease returns null when the release offers no way to verify the APK`() = runTest {
        transport.textResponses += validJson(digest = null, includeChecksums = false)

        assertNull(source.latestRelease())
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `latestRelease returns null when SHA256SUMS does not list the APK`() = runTest {
        transport.textResponses += validJson(digest = null)
        transport.textResponses += "$OTHER_HASH  other.apk\n"

        assertNull(source.latestRelease())
    }

    @Test
    fun `latestRelease returns null when the SHA256SUMS entry has a malformed hash`() = runTest {
        transport.textResponses += validJson(digest = null)
        transport.textResponses += "not-a-hash  $APK_NAME\n"

        assertNull(source.latestRelease())
    }

    @Test
    fun `latestRelease returns null when the checksums file cannot be fetched`() = runTest {
        transport.textResponses += validJson(digest = null)
        transport.textResponses += HttpStatusException(500)

        assertNull(source.latestRelease())
    }

    @Test
    fun `latestRelease returns null when the checksums file is oversized`() = runTest {
        transport.textResponses += validJson(digest = null)
        transport.textResponses += "padding\n".repeat(10_000) + "$HASH  $APK_NAME\n"

        assertNull(source.latestRelease())
    }

    @Test
    fun `latestRelease returns null on 404 and propagates other network errors`() = runTest {
        transport.textResponses += HttpStatusException(404)
        assertNull(source.latestRelease())

        transport.textResponses += HttpStatusException(500)
        val httpError = runCatching { source.latestRelease() }.exceptionOrNull()
        assertTrue("expected HttpStatusException, got $httpError", httpError is HttpStatusException)
        assertEquals(500, (httpError as HttpStatusException).code)

        transport.textResponses += IOException("connection reset")
        val ioError = runCatching { source.latestRelease() }.exceptionOrNull()
        assertTrue("expected IOException, got $ioError", ioError is IOException)
    }

    @Test
    fun `latestRelease sends the GitHub API headers and a user agent`() = runTest {
        transport.textResponses += validJson()

        source.latestRelease()

        val request = transport.requests.single()
        assertEquals(API_URL, request.url)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        assertEquals("2022-11-28", request.headers["X-GitHub-Api-Version"])
        assertEquals("EmpathieEnergyAccounting", request.headers["User-Agent"])
    }

    @Test
    fun `latestRelease uses the configured user agent`() = runTest {
        transport.textResponses += validJson()

        GitHubReleaseSource(REPO, transport, userAgent = "CustomAgent/1.0").latestRelease()

        assertEquals("CustomAgent/1.0", transport.requests.single().headers["User-Agent"])
    }

    @Test
    fun `downloadApk writes the bytes and reports progress`() = runTest {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        transport.downloadBytes = bytes
        val destination = temporaryFolder.newFile("update.apk")
        val progress = mutableListOf<Pair<Long, Long>>()

        source.downloadApk(updateInfo(apkSizeBytes = bytes.size.toLong()), destination) { downloaded, total ->
            progress += downloaded to total
        }

        assertArrayEquals(bytes, destination.readBytes())
        assertTrue("expected progress reports", progress.isNotEmpty())
        assertTrue("progress must increase: $progress", progress.zipWithNext().all { (a, b) -> a.first < b.first })
        assertEquals(bytes.size.toLong(), progress.last().first)
        assertTrue(progress.all { (_, total) -> total == bytes.size.toLong() })
    }

    @Test
    fun `downloadApk fails when the byte count does not match`() = runTest {
        transport.downloadBytes = ByteArray(10)
        val destination = temporaryFolder.newFile("update.apk")

        val error = runCatching {
            source.downloadApk(updateInfo(apkSizeBytes = 4096), destination) { _, _ -> }
        }.exceptionOrNull()

        assertTrue("expected IOException, got $error", error is IOException)
    }

    @Test
    fun `downloadApk does not verify the size when it is unknown`() = runTest {
        transport.downloadBytes = ByteArray(10)
        val destination = temporaryFolder.newFile("update.apk")

        source.downloadApk(updateInfo(apkSizeBytes = 0), destination) { _, _ -> }

        assertEquals(10, destination.length())
    }

    private fun updateInfo(
        apkSizeBytes: Long,
        apkSha256: String = HASH,
    ) = UpdateInfo(
        version = SemanticVersion(0, 4, 0),
        tagName = "v0.4.0",
        releaseName = "Energy Accounting v0.4.0",
        releaseNotes = "## What's new",
        releaseUrl = RELEASE_URL,
        apkUrl = APK_URL,
        apkAssetName = APK_NAME,
        apkSizeBytes = apkSizeBytes,
        apkSha256 = apkSha256,
        checksumsUrl = null,
    )

    /**
     * A transport that answers [getText] from a queue and writes fixed bytes on [download],
     * recording every request it receives. Queued entries may be [String]s or [Throwable]s.
     */
    private class FakeTransport : HttpTransport {

        data class RecordedRequest(val url: String, val headers: Map<String, String>)

        val textResponses = ArrayDeque<Any>()

        val requests = mutableListOf<RecordedRequest>()

        var downloadBytes: ByteArray = ByteArray(0)

        var downloadChunkSize: Int = 64 * 1024

        override fun getText(url: String, headers: Map<String, String>): String {
            requests += RecordedRequest(url, headers)
            val response = textResponses.removeFirstOrNull()
                ?: throw AssertionError("no queued text response for $url")
            if (response is Throwable) throw response
            return response as String
        }

        override fun download(url: String, destination: File, onProgress: (downloadedBytes: Long) -> Unit) {
            requests += RecordedRequest(url, emptyMap())
            var downloaded = 0L
            FileOutputStream(destination).use { output ->
                var offset = 0
                while (offset < downloadBytes.size) {
                    val length = minOf(downloadChunkSize, downloadBytes.size - offset)
                    output.write(downloadBytes, offset, length)
                    offset += length
                    downloaded += length
                    onProgress(downloaded)
                }
            }
        }
    }

    private companion object {
        const val REPO = "iseppe/energy-accounting"
        const val API_URL = "https://api.github.com/repos/iseppe/energy-accounting/releases/latest"
        const val RELEASE_URL = "https://github.com/iseppe/energy-accounting/releases/tag/v0.4.0"
        const val APK_NAME = "empathie-energy-accounting-v0.4.0.apk"
        const val APK_URL =
            "https://github.com/iseppe/energy-accounting/releases/download/v0.4.0/empathie-energy-accounting-v0.4.0.apk"
        const val SHA256SUMS_URL =
            "https://github.com/iseppe/energy-accounting/releases/download/v0.4.0/SHA256SUMS"
        const val APK_SIZE = 12345678L

        const val HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val HASH_UPPER = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
        const val OTHER_HASH = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

        fun validJson(
            digest: String? = "sha256:$HASH_UPPER",
            includeChecksums: Boolean = true,
        ): String {
            val assets = buildList {
                add(
                    """
                    {
                      "name": "$APK_NAME",
                      "browser_download_url": "$APK_URL",
                      "size": $APK_SIZE,
                      "digest": ${digest?.let { "\"$it\"" } ?: "null"},
                      "content_type": "application/vnd.android.package-archive"
                    }
                    """.trimIndent(),
                )
                if (includeChecksums) {
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
            }

            return """
                {
                  "tag_name": "v0.4.0",
                  "name": "Energy Accounting v0.4.0",
                  "body": "## What's new",
                  "html_url": "$RELEASE_URL",
                  "draft": false,
                  "prerelease": false,
                  "assets": [
                    ${assets.joinToString(",\n        ")}
                  ]
                }
            """.trimIndent()
        }
    }
}
