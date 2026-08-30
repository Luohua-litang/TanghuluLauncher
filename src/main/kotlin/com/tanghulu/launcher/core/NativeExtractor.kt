package com.tanghulu.launcher.core

import com.tanghulu.launcher.util.FileUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 解压 natives 库到 natives 目录。
 */
object NativeExtractor {

    /** 将 natives jar 解压到 natives 目录（跳过 META-INF）。 */
    @JvmStatic
    @Throws(IOException::class)
    fun extract(jarFile: Path, nativesDir: Path) {
        if (!Files.isRegularFile(jarFile)) {
            throw IOException("Native jar not found: $jarFile")
        }
        Files.createDirectories(nativesDir)
        ZipFile(jarFile.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.startsWith("META-INF/") || name.startsWith("META-INF\\")) continue
                // 防止路径穿越
                val target = nativesDir.resolve(name).normalize()
                if (!target.startsWith(nativesDir)) continue
                Files.createDirectories(target.parent)
                zip.getInputStream(entry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    /** 清空并重建 natives 目录。 */
    @JvmStatic
    @Throws(IOException::class)
    fun clean(nativesDir: Path) {
        FileUtil.deleteRecursively(nativesDir)
        Files.createDirectories(nativesDir)
    }
}
