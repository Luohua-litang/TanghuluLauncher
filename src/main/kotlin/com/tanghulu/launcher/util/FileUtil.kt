package com.tanghulu.launcher.util

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object FileUtil {

    /** Compute the file's SHA1, or null on failure. */
    @JvmStatic
    fun sha1(file: Path): String? = digest(file, "SHA-1", 40)

    /** Compute the file's SHA256, or null on failure. */
    @JvmStatic
    fun sha256(file: Path): String? = digest(file, "SHA-256", 64)

    private val HEX = "0123456789abcdef".toCharArray()

    private fun digest(file: Path, algorithm: String, hexLen: Int): String? {
        return try {
            Files.newInputStream(file).use { input ->
                val md = MessageDigest.getInstance(algorithm)
                val buf = ByteArray(64 * 1024)
                var n = input.read(buf)
                while (n > 0) {
                    md.update(buf, 0, n)
                    n = input.read(buf)
                }
                val sb = StringBuilder(hexLen)
                for (b in md.digest()) {
                    val v = b.toInt() and 0xFF
                    sb.append(HEX[v ushr 4])
                    sb.append(HEX[v and 0x0F])
                }
                sb.toString()
            }
        } catch (e: IOException) {
            null
        } catch (e: NoSuchAlgorithmException) {
            null
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readText(file: Path): String = Files.readString(file, StandardCharsets.UTF_8)

    @JvmStatic
    @Throws(IOException::class)
    fun writeText(file: Path, text: String) {
        Files.createDirectories(file.parent)
        Files.writeString(file, text, StandardCharsets.UTF_8)
    }

    /** Delete a directory and its contents. */
    @JvmStatic
    @Throws(IOException::class)
    fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            stream.sorted(java.util.Comparator.reverseOrder()).forEach { p ->
                try {
                    Files.deleteIfExists(p)
                } catch (ignored: IOException) {
                    // ignore individual deletion failures
                }
            }
        }
    }
}
