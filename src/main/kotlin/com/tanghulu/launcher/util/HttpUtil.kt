package com.tanghulu.launcher.util

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * HTTP request and download utility built on the JDK's java.net.http.
 */
object HttpUtil {
    const val USER_AGENT = "TanghuluLauncher/1.0.0"

    private val CLIENT: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** Fetch URL content, retrying up to 3 times on failure. */
    @JvmStatic
    @Throws(IOException::class)
    fun get(url: String): ByteArray = get(url, emptyMap())

    /** Fetch URL content with custom headers, retrying up to 3 times on failure. */
    @JvmStatic
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): ByteArray {
        var last: IOException? = null
        for (attempt in 0 until 3) {
            try {
                val builder = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                for ((k, v) in headers) builder.header(k, v)
                val resp = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                if (resp.statusCode() == 200) return resp.body()
                throw IOException("HTTP ${resp.statusCode()} for $url")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while fetching $url", e)
            } catch (e: IOException) {
                last = e
                try {
                    Thread.sleep(500L * (attempt + 1))
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        throw IOException("Failed to fetch $url", last)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getText(url: String): String = String(get(url), StandardCharsets.UTF_8)

    /** Fetch text content with custom headers. */
    @JvmStatic
    @Throws(IOException::class)
    fun getText(url: String, headers: Map<String, String>): String = String(get(url, headers), StandardCharsets.UTF_8)

    /**
     * Fetch text in a single quick attempt (no retries), for latency-sensitive requests such as version list refreshes.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getTextOnce(url: String): String {
        try {
            val req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build()
            val resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() == 200) {
                return String(resp.body(), StandardCharsets.UTF_8)
            }
            throw IOException("HTTP ${resp.statusCode()} for $url")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while fetching $url", e)
        }
    }

    /**
     * Measure the round-trip latency of a URL with a single lightweight GET request
     * (response body discarded). Returns the elapsed time in milliseconds, or throws on failure.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun ping(url: String): Long {
        val start = System.nanoTime()
        val req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val resp = try {
            CLIENT.send(req, HttpResponse.BodyHandlers.discarding())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while pinging $url", e)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        if (resp.statusCode() in 200..399) return elapsedMs
        throw IOException("HTTP ${resp.statusCode()} for $url")
    }

    /** Download progress callback. total == -1 means the total size is unknown. */
    fun interface ProgressListener {
        fun onProgress(downloaded: Long, total: Long)
    }

    /**
     * Download a URL to the target file (write to a temp file first, then move atomically).
     * If the target already exists and its SHA1 matches, skip and return false; otherwise download and return true.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadIfNeeded(url: String, target: Path, expectedSha1: String?): Boolean =
        downloadIfNeeded(url, target, expectedSha1, null)

    /** Stream-download to the target file (write to a temp file first, then move atomically) with progress callbacks. */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadIfNeeded(
        url: String, target: Path, expectedSha1: String?, listener: ProgressListener?
    ): Boolean {
        if (Files.isRegularFile(target)) {
            if (!expectedSha1.isNullOrEmpty() && expectedSha1.equals(FileUtil.sha1(target), true)) {
                listener?.onProgress(1, 1)
                return false // already exists and verified
            }
            // no sha1 or verification failed, delete and re-download
            Files.delete(target)
        }
        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")

        val req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build()
        val resp: HttpResponse<InputStream> = try {
            CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while downloading $url", e)
        }
        if (resp.statusCode() != 200) {
            throw IOException("HTTP ${resp.statusCode()} for $url")
        }
        val total = resp.headers().firstValueAsLong("Content-Length").orElse(-1L)
        var downloaded = 0L
        resp.body().use { input ->
            Files.newOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                var n = input.read(buf)
                while (n != -1) {
                    out.write(buf, 0, n)
                    downloaded += n
                    listener?.onProgress(downloaded, total)
                    n = input.read(buf)
                }
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        if (!expectedSha1.isNullOrEmpty()) {
            val actual = FileUtil.sha1(target)
            if (actual == null || !actual.equals(expectedSha1, true)) {
                throw IOException(
                    "SHA1 mismatch for $target (expected $expectedSha1, got $actual)"
                )
            }
        }
        return true
    }

    /**
     * Try downloading from multiple candidate URLs in order. On failure, fall through to the next URL; throw only if all fail.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadIfNeeded(urls: List<String>, target: Path, expectedSha1: String?): Boolean =
        downloadIfNeeded(urls, target, expectedSha1, null)

    @JvmStatic
    @Throws(IOException::class)
    fun downloadIfNeeded(
        urls: List<String>, target: Path, expectedSha1: String?, listener: ProgressListener?
    ): Boolean {
        if (urls.isEmpty()) throw IOException("No download URL for $target")
        if (urls.size == 1) return downloadIfNeeded(urls[0], target, expectedSha1, listener)
        // skip if the target already exists and is verified
        if (Files.isRegularFile(target) && !expectedSha1.isNullOrEmpty()
            && expectedSha1.equals(FileUtil.sha1(target), true)
        ) {
            listener?.onProgress(1, 1)
            return false
        }
        var last: IOException? = null
        for (url in urls) {
            if (url.isNullOrEmpty()) continue
            try {
                return downloadIfNeeded(url, target, expectedSha1, listener)
            } catch (e: IOException) {
                last = e
            }
        }
        throw IOException(
            "All download sources failed for $target" + (if (last != null) ": " + last.message else "")
        )
    }
}
