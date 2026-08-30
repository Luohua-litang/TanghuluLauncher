package com.tanghulu.launcher.util

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

/**
 * 操作系统枚举，集中处理平台差异。
 * 进程启动时解析一次 [CURRENT_OS]，避免散落的 os.name 字符串判断。
 */
enum class OperatingSystem(private val mojangNameValue: String) {
    WINDOWS("windows"),
    MACOS("osx"),
    LINUX("linux"),
    UNKNOWN("universal");

    /** Mojang 在 natives 分类与规则匹配中使用的操作系统名。 */
    fun mojangName(): String = mojangNameValue

    fun isWindows(): Boolean = this == WINDOWS
    fun isMac(): Boolean = this == MACOS

    /** Java 可执行文件名（Windows 带 .exe）。 */
    fun javaExecutable(): String = if (isWindows()) "java.exe" else "java"

    companion object {
        /** 当前运行平台（进程启动时解析一次）。 */
        @JvmField
        val CURRENT_OS: OperatingSystem = parse(System.getProperty("os.name", ""))

        @JvmStatic
        fun parse(osName: String?): OperatingSystem {
            val name = osName?.lowercase(Locale.ROOT) ?: ""
            return when {
                name.contains("win") -> WINDOWS
                name.contains("mac") || name.contains("darwin") -> MACOS
                name.contains("linux") || name.contains("freebsd") || name.contains("unix") -> LINUX
                else -> UNKNOWN
            }
        }

        /** 各平台默认的 Minecraft 游戏目录（.minecraft）。 */
        @JvmStatic
        fun minecraftDir(): Path = when {
            CURRENT_OS.isWindows() -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) Paths.get(appData, ".minecraft")
                else Paths.get(System.getProperty("user.home"), ".minecraft")
            }
            CURRENT_OS.isMac() -> Paths.get(
                System.getProperty("user.home"), "Library", "Application Support", "minecraft"
            )
            else -> Paths.get(System.getProperty("user.home"), ".minecraft")
        }

        /** 应用数据目录（配置与日志存放处）。 */
        @JvmStatic
        fun appDataDir(): Path = when {
            CURRENT_OS.isWindows() -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) Paths.get(appData, "TanghuluLauncher")
                else Paths.get(System.getProperty("user.home"), ".tanghulu_launcher")
            }
            CURRENT_OS.isMac() -> Paths.get(
                System.getProperty("user.home"), "Library", "Application Support", "TanghuluLauncher"
            )
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")
                if (!xdg.isNullOrBlank()) Paths.get(xdg, "TanghuluLauncher")
                else Paths.get(System.getProperty("user.home"), ".tanghulu_launcher")
            }
        }
    }
}
