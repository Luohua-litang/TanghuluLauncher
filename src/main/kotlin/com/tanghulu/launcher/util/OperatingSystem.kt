package com.tanghulu.launcher.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

/**
 * Operating system enum that centralizes platform differences.
 * [CURRENT_OS] is resolved once at process startup to avoid scattered os.name checks.
 */
enum class OperatingSystem(private val mojangNameValue: String) {
    WINDOWS("windows"),
    MACOS("osx"),
    LINUX("linux"),
    UNKNOWN("universal");

    /** OS name used by Mojang for natives classification and rule matching. */
    fun mojangName(): String = mojangNameValue

    fun isWindows(): Boolean = this == WINDOWS
    fun isMac(): Boolean = this == MACOS

    /** Java executable file name (.exe on Windows). */
    fun javaExecutable(): String = if (isWindows()) "java.exe" else "java"

    companion object {
        /** The currently running platform (resolved once at startup). */
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

        /** Default Minecraft game directory per platform (.minecraft). */
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

        /**
         * Directory that contains the launcher executable (the APP_HOME root).
         * When packaged by Compose Desktop the classes live in a jar under APP_HOME/lib
         * (or APP_HOME/app for jpackage), so we walk up one level from that folder.
         * Falls back to the working directory.
         */
        @JvmStatic
        fun launcherDir(): Path {
            val codeSource = OperatingSystem::class.java.protectionDomain?.codeSource
            if (codeSource != null) {
                try {
                    val location = Paths.get(codeSource.location.toURI())
                    if (Files.isRegularFile(location)) {
                        val parent = location.parent
                        val name = parent?.fileName?.toString()
                        if (parent != null && (name.equals("lib", true) || name.equals("app", true))) {
                            // Compose Desktop "lib/" or jpackage "app/" layout -> APP_HOME root
                            return parent.parent ?: parent
                        }
                        return parent ?: Paths.get(System.getProperty("user.dir"))
                    }
                    return location
                } catch (_: Exception) {
                    // fall through to the working directory
                }
            }
            return Paths.get(System.getProperty("user.dir"))
        }

        /**
         * Launcher-local data directory (config, logs, skins, runtimes).
         * Kept in a "config" folder next to the launcher for portability.
         */
        @JvmStatic
        fun appDataDir(): Path = launcherDir().resolve("config")
    }
}
