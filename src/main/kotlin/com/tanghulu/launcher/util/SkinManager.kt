package com.tanghulu.launcher.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

/**
 * 离线皮肤管理：负责本地皮肤文件的存储与自定义皮肤上传。
 */
object SkinManager {

    /** 皮肤存储根目录：appDataDir/skins */
    @JvmStatic
    fun skinsDir(): Path = OperatingSystem.appDataDir().resolve("skins")

    /** 某玩家对应的本地皮肤文件路径（不存在则返回 null）。 */
    @JvmStatic
    fun skinFile(playerName: String?): Path? {
        if (playerName.isNullOrBlank()) return null
        val f = skinsDir().resolve(sanitize(playerName) + ".png")
        return if (Files.isRegularFile(f)) f else null
    }

    /** 是否存在本地皮肤。 */
    @JvmStatic
    fun hasSkin(playerName: String?): Boolean = skinFile(playerName) != null

    /** 上传本地 PNG 作为指定玩家的皮肤，返回保存后的文件路径。 */
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

    /** 删除指定玩家的本地皮肤。 */
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

    /** 非法字符替换，防止路径穿越。 */
    private fun sanitize(name: String): String = SANITIZE_PATTERN.matcher(name).replaceAll("_")
}
