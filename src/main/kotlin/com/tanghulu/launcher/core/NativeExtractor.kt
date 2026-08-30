package com.tanghulu.launcher.core

import com.tanghulu.launcher.util.FileUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Extract natives libraries into the natives directory.
 */
object NativeExtractor {

    /** Extract a natives jar into the natives directory (skipping META-INF). */
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
                // Prevent path traversal
                val target = nativesDir.resolve(name).normalize()
                if (!target.startsWith(nativesDir)) continue
                Files.createDirectories(target.parent)
                zip.getInputStream(entry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    /** Clear and recreate the natives directory. */
    @JvmStatic
    @Throws(IOException::class)
    fun clean(nativesDir: Path) {
        FileUtil.deleteRecursively(nativesDir)
        Files.createDirectories(nativesDir)
    }
}
