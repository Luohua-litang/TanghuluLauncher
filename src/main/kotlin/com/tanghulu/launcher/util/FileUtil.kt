package com.tanghulu.launcher.util

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object FileUtil {

    /** 计算文件的 SHA1，失败返回 null。 */
    @JvmStatic
    fun sha1(file: Path): String? = digest(file, "SHA-1", 40)

    /** 计算文件的 SHA256，失败返回 null。 */
    @JvmStatic
    fun sha256(file: Path): String? = digest(file, "SHA-256", 64)

    private fun digest(file: Path, algorithm: String, hexLen: Int): String? {
        return try {
            Files.newInputStream(file).use { input ->
                val md = MessageDigest.getInstance(algorithm)
                val buf = ByteArray(8192)
                var n = input.read(buf)
                while (n > 0) {
                    md.update(buf, 0, n)
                    n = input.read(buf)
                }
                val sb = StringBuilder(hexLen)
                for (b in md.digest()) {
                    sb.append(String.format("%02x", b))
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

    /** 删除目录及其内容。 */
    @JvmStatic
    @Throws(IOException::class)
    fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            stream.sorted(java.util.Comparator.reverseOrder()).forEach { p ->
                try {
                    Files.deleteIfExists(p)
                } catch (ignored: IOException) {
                    // 忽略单个文件删除失败
                }
            }
        }
    }
}
