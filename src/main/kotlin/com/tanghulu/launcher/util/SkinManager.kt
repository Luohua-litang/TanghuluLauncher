package com.tanghulu.launcher.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

/**
 * Offline skin management: stores local skin files and handles custom skin uploads.
 */
object SkinManager {

    /** Skin storage root directory: appDataDir/skins */
    @JvmStatic
    fun skinsDir(): Path = OperatingSystem.appDataDir().resolve("skins")

    /** Path to a player's local skin file (null if missing). */
    @JvmStatic
    fun skinFile(playerName: String?): Path? {
        if (playerName.isNullOrBlank()) return null
        val f = skinsDir().resolve(sanitize(playerName) + ".png")
        return if (Files.isRegularFile(f)) f else null
    }

    /** Whether a local skin exists. */
    @JvmStatic
    fun hasSkin(playerName: String?): Boolean = skinFile(playerName) != null

    /** Upload a local PNG as the given player's skin and return the saved file path. */
    @JvmStatic
    @Throws(IOException::class)
    fun uploadSkin(playerName: String?, sourcePng: Path?): Path {
        if (playerName.isNullOrBlank()) throw IOException("玩家名为空")
        if (sourcePng == null || !Files.isRegularFile(sourcePng)) throw IOException("皮肤文件不存在")
        Files.createDirectories(skinsDir())
        val target = skinsDir().resolve(sanitize(playerName) + ".png")
        Files.copy(sourcePng, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    /** Delete the given player's local skin. */
    @JvmStatic
    fun removeSkin(playerName: String?): Boolean {
        val f = skinFile(playerName)
        if (f == null) return false
        return try {
            Files.deleteIfExists(f)
            true
        } catch (e: IOException) {
            Log.warn("删除皮肤失败: " + e.message)
            false
        }
    }

    private val SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9_]")

    /** Replace illegal characters to prevent path traversal. */
    private fun sanitize(name: String): String = SANITIZE_PATTERN.matcher(name).replaceAll("_")
}
