package com.empathie.energyaccounting.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The minimal HTTP surface the updater needs: fetch a small text document (release JSON, a
 * checksums file) and stream a large binary to disk with progress.
 *
 * Implementations throw [HttpStatusException] for non-2xx responses and [IOException] for
 * transport-level failures.
 */
interface HttpTransport {
    fun getText(url: String, headers: Map<String, String>): String

    fun download(url: String, destination: File, onProgress: (downloadedBytes: Long) -> Unit)
}

/** Thrown when a request completes with a non-2xx status. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * [HttpTransport] backed by [HttpURLConnection]. Text reads are capped at [MAX_TEXT_BYTES] and
 * downloads are streamed in [CHUNK_SIZE] chunks, reporting progress at most every
 * [PROGRESS_INTERVAL_MILLIS] or [PROGRESS_INTERVAL_BYTES].
 */
class UrlConnectionTransport : HttpTransport {

    override fun getText(url: String, headers: Map<String, String>): String {
        val connection = openConnection(url, headers, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            return connection.inputStream.use { it.readCapped(MAX_TEXT_BYTES).toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    override fun download(url: String, destination: File, onProgress: (downloadedBytes: Long) -> Unit) {
        val connection = openConnection(url, emptyMap(), DOWNLOAD_CONNECT_TIMEOUT_MILLIS, DOWNLOAD_READ_TIMEOUT_MILLIS)
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            var downloaded = 0L
            var lastReportedBytes = 0L
            var lastReportedAt = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val chunk = ByteArray(CHUNK_SIZE)
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        output.write(chunk, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (downloaded - lastReportedBytes >= PROGRESS_INTERVAL_BYTES ||
                            now - lastReportedAt >= PROGRESS_INTERVAL_MILLIS
                        ) {
                            lastReportedBytes = downloaded
                            lastReportedAt = now
                            onProgress(downloaded)
                        }
                    }
                }
            }
            if (downloaded != lastReportedBytes) onProgress(downloaded)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        headers: Map<String, String>,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return connection
    }

    /** Reads at most [limit] bytes; more content fails instead of being silently truncated. */
    private fun InputStream.readCapped(limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0
        while (true) {
            val read = read(chunk, 0, minOf(chunk.size, limit + 1 - total))
            if (read < 0) break
            buffer.write(chunk, 0, read)
            total += read
            if (total > limit) throw IOException("response body exceeds $limit bytes")
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 20_000
        const val DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 15_000
        const val DOWNLOAD_READ_TIMEOUT_MILLIS = 30_000

        const val MAX_TEXT_BYTES = 1024 * 1024
        const val CHUNK_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_BYTES = 256 * 1024L
        const val PROGRESS_INTERVAL_MILLIS = 250L
    }
}
