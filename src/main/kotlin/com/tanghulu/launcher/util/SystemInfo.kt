package com.tanghulu.launcher.util

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/** A single label/value entry in the system diagnostics panel. */
data class SysInfoItem(val label: String, val value: String)

/** A titled group of [SysInfoItem] entries. */
data class SysInfoGroup(val title: String, val items: List<SysInfoItem>)

/**
 * Collects system diagnostics (OS, CPU, memory, Java, disk, game directory size)
 * for the developer tools panel. Pure JVM code, no Compose dependencies.
 */
object SystemInfo {
    const val LAUNCHER_VERSION = "1.0.0"

    fun collect(gameDir: Path): List<SysInfoGroup> {
        val props = System.getProperties()
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val runtime = Runtime.getRuntime()

        val os = "${props.getProperty("os.name")} ${props.getProperty("os.version")}"
        val arch = props.getProperty("os.arch") ?: "?"
        val javaVersion = props.getProperty("java.version") ?: "?"
        val javaVendor = props.getProperty("java.vendor") ?: "?"
        val javaHome = props.getProperty("java.home") ?: "?"
        val cores = runtime.availableProcessors()
        val load = osBean.systemLoadAverage
        val jvmUsed = runtime.totalMemory() - runtime.freeMemory()
        val jvmMax = runtime.maxMemory()

        var sysTotal = -1L
        var sysFree = -1L
        if (osBean is com.sun.management.OperatingSystemMXBean) {
            sysTotal = osBean.totalMemorySize
            sysFree = osBean.freeMemorySize
        }

        val diskFile = File(gameDir.toAbsolutePath().toString())
        val diskTotal = diskFile.totalSpace
        val diskFree = diskFile.freeSpace

        return listOf(
            SysInfoGroup("系统", listOf(
                SysInfoItem("操作系统", os),
                SysInfoItem("架构", arch),
                SysInfoItem("CPU 核心数", cores.toString()),
                SysInfoItem("系统负载", if (load >= 0) String.format("%.2f", load) else "不可用"),
                SysInfoItem(
                    "系统内存",
                    if (sysTotal > 0) "${formatBytes(sysTotal - sysFree)} / ${formatBytes(sysTotal)}" else "不可用"
                ),
            )),
            SysInfoGroup("Java 运行时", listOf(
                SysInfoItem("版本", javaVersion),
                SysInfoItem("供应商", javaVendor),
                SysInfoItem("路径", javaHome),
                SysInfoItem("JVM 内存", "${formatBytes(jvmUsed)} / ${formatBytes(jvmMax)}"),
            )),
            SysInfoGroup("启动器", listOf(
                SysInfoItem("版本", LAUNCHER_VERSION),
                SysInfoItem("游戏目录", gameDir.toAbsolutePath().absolutePathString()),
                SysInfoItem("游戏目录大小", formatBytes(dirSize(gameDir))),
                SysInfoItem("磁盘可用", "${formatBytes(diskFree)} / ${formatBytes(diskTotal)}"),
            )),
        )
    }

    /** Recursively sum the size of all files under [dir] (0 if missing / not a directory). */
    private fun dirSize(dir: Path): Long {
        if (!Files.isDirectory(dir)) return 0L
        var total = 0L
        try {
            Files.walk(dir).use { stream ->
                stream.forEach { p ->
                    try {
                        if (Files.isRegularFile(p)) total += Files.size(p)
                    } catch (_: Exception) {
                        // ignore single-file failures
                    }
                }
            }
        } catch (_: Exception) {
            // ignore traversal failures
        }
        return total
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}
