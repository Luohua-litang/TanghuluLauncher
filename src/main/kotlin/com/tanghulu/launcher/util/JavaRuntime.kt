package com.tanghulu.launcher.util

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.LinkedHashSet
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Java 运行时检测与匹配：解析 java 可执行文件的版本号，
 * 并在本机常见位置查找满足指定大版本要求的 Java。
 */
object JavaRuntime {
    private val VERSION = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?")

    /** 检测某个 java 可执行文件的主版本号（如 8 / 17 / 21 / 25），失败返回 -1。 */
    @JvmStatic
    fun detectMajor(javaExe: Path?): Int {
        if (javaExe == null || !Files.isRegularFile(javaExe)) return -1
        return try {
            val pb = ProcessBuilder(javaExe.toString(), "-version")
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = String(p.inputStream.readAllBytes(), StandardCharsets.UTF_8)
            p.waitFor()
            val m = VERSION.matcher(out)
            if (!m.find()) return -1
            var major = m.group(1).toInt()
            // JDK 8 及更早输出 "1.8.0_xxx" 这类格式
            if (major == 1 && m.group(2) != null) {
                major = m.group(2).toInt()
            }
            major
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * 查找满足 [requiredMajor] 要求的 java 可执行文件。
     * 优先返回版本完全一致的；否则返回大于要求且最接近的；找不到返回 null。
     */
    @JvmStatic
    fun findJava(requiredMajor: Int): Path? {
        var nearestHigher: Path? = null
        var nearestMajor = Int.MAX_VALUE
        for (p in candidates()) {
            val m = detectMajor(p)
            if (m < requiredMajor) continue
            if (m == requiredMajor) return p
            if (m < nearestMajor) {
                nearestMajor = m
                nearestHigher = p
            }
        }
        return nearestHigher
    }

    /** 只查找主版本号精确等于 [major] 的 java 可执行文件，找不到返回 null。 */
    @JvmStatic
    fun findJavaExact(major: Int): Path? {
        for (p in candidates()) {
            if (detectMajor(p) == major) return p
        }
        return null
    }

    /**
     * 列出本机所有候选 Java 运行时（含主版本号，按版本从高到低排序）。
     * 会逐个执行 `java -version`，建议在 IO 线程调用。
     */
    @JvmStatic
    fun listJavas(): List<JavaInfo> {
        val result = ArrayList<JavaInfo>()
        for (p in candidates()) {
            val m = detectMajor(p)
            if (m > 0) result.add(JavaInfo(p, m))
        }
        return result.sortedWith(
            compareByDescending<JavaInfo> { it.major }.thenBy { it.path.toString() }
        )
    }

    /**
     * 确保本机存在 Java 8 运行时（老版本 Minecraft 需要）。
     * 查找顺序：本机已安装 Java 8 -> 缓存目录 -> 自动下载并解压。
     * 返回 java 可执行文件路径，失败返回 null（不抛异常）。
     */
    @JvmStatic
    fun ensureJava8(log: Consumer<String>?): Path? {
        val exact = findJavaExact(8)
        if (exact != null) return exact
        val cached = OperatingSystem.appDataDir().resolve("runtimes/jre8")
        val cachedJava = cached.resolve("bin").resolve(OperatingSystem.CURRENT_OS.javaExecutable())
        if (detectMajor(cachedJava) == 8) return cachedJava
        log?.accept("未检测到 Java 8，正在自动下载 Java 8 运行环境（首次约 40MB，请稍候）...")
        return try {
            downloadJava8(cached, log)
        } catch (e: Exception) {
            log?.accept("自动下载 Java 8 失败: " + e.message)
            null
        }
    }

    /** 通过 Adoptium 下载 JRE 8 并解压到 [dest]，返回其中的 java 可执行文件。 */
    @Throws(Exception::class)
    private fun downloadJava8(dest: Path, log: Consumer<String>?): Path? {
        var url: String? = null
        var checksum: String? = null
        try {
            val api = "https://api.adoptium.net/v3/assets/latest/8/hotspot" +
                "?os=" + OperatingSystem.CURRENT_OS.mojangName() +
                "&architecture=x64&image_type=jre"
            val arr = Json.asArray(Json.parse(HttpUtil.getTextOnce(api)))
            if (arr != null) {
                for (item in arr) {
                    val m = Json.asObject(item)
                    val binary = Json.asObject(if (m == null) null else m["binary"])
                    val pkg = Json.asObject(if (binary == null) null else binary["package"])
                    if (pkg != null) {
                        url = Json.optString(pkg, "link")
                        checksum = Json.optString(pkg, "checksum")
                        break
                    }
                }
            }
        } catch (ignored: Exception) {
            // API 不可用时走下方固定镜像链接兜底
        }
        if (url.isNullOrBlank()) {
            url = "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/8/jre/x64/windows/" +
                "OpenJDK8U-jre_x64_windows_hotspot_8u432b06.zip"
        }
        val urls = mutableListOf<String>()
        urls.add(url!!)
        val filename = url!!.substring(url!!.lastIndexOf('/') + 1)
        if (!url!!.contains("tuna.tsinghua")) {
            urls.add("https://mirrors.tuna.tsinghua.edu.cn/Adoptium/8/jre/x64/windows/" + filename)
        }
        val zip = dest.resolveSibling(dest.fileName.toString() + ".zip")
        Files.createDirectories(dest.parent)
        HttpUtil.downloadIfNeeded(urls, zip, null)
        if (!checksum.isNullOrBlank()) {
            val actual = FileUtil.sha256(zip)
            if (actual == null || !actual.equals(checksum, true)) {
                Files.deleteIfExists(zip)
                throw IOException("JRE 8 下载校验失败（checksum 不匹配）")
            }
        }
        log?.accept("正在解压 Java 8 ...")
        Files.createDirectories(dest)
        extractZip(zip, dest)
        Files.deleteIfExists(zip)
        val javaExe = dest.resolve("bin").resolve(OperatingSystem.CURRENT_OS.javaExecutable())
        if (detectMajor(javaExe) != 8) {
            throw IOException("解压后未找到有效的 Java 8 运行时")
        }
        return javaExe
    }

    /** 解压 zip 到目标目录，自动跳过第一层根目录（如 jdk8u...-jre/）。 */
    @Throws(IOException::class)
    private fun extractZip(zip: Path, dest: Path) {
        ZipFile(zip.toFile()).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()
                if (entry.isDirectory) continue
                var name = entry.name.replace('\\', '/')
                val idx = name.indexOf('/')
                if (idx >= 0) name = name.substring(idx + 1)
                if (name.isEmpty()) continue
                val target = dest.resolve(name).normalize()
                if (!target.startsWith(dest)) continue // 防路径穿越
                Files.createDirectories(target.parent)
                zf.getInputStream(entry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    /** 收集本机候选 java 可执行文件（去重、绝对路径）。 */
    private fun candidates(): List<Path> {
        val set = LinkedHashSet<Path>()
        val exe = OperatingSystem.CURRENT_OS.javaExecutable()
        // 1. 启动器自身 JVM（打包后即内置 runtime 里的 java）
        addFromHome(set, System.getProperty("java.home"), exe)
        // 2. JAVA_HOME
        addFromHome(set, System.getenv("JAVA_HOME"), exe)
        // 3. PATH 中所有目录
        val pathEnv = System.getenv("PATH")
        if (pathEnv != null) {
            for (dir in pathEnv.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                val j = Paths.get(dir, exe)
                if (Files.isRegularFile(j)) set.add(j.toAbsolutePath().normalize())
            }
        }
        // 4. Windows 常见安装目录
        if (OperatingSystem.CURRENT_OS.isWindows()) {
            val bases = arrayOf(
                "C:/Program Files/Eclipse Adoptium",
                "C:/Program Files/Java",
                "C:/Program Files/Microsoft",
                "C:/Program Files/Zulu",
                "C:/Program Files/Amazon Corretto",
                "C:/Program Files/BellSoft"
            )
            for (base in bases) {
                val d = Paths.get(base)
                if (!Files.isDirectory(d)) continue
                try {
                    Files.list(d).use { s ->
                        val dirs = s.filter { Files.isDirectory(it) }.sorted().toList()
                        for (dd in dirs) addFromHome(set, dd.toString(), exe)
                    }
                } catch (ignored: IOException) {
                    // 忽略无法读取的目录
                }
            }
        }
        return set.toList()
    }

    private fun addFromHome(set: LinkedHashSet<Path>, home: String?, exe: String) {
        if (home.isNullOrBlank()) return
        val j = Paths.get(home, "bin", exe)
        if (Files.isRegularFile(j)) set.add(j.toAbsolutePath().normalize())
    }
}

/** 本机检测到的单个 Java 运行时信息。 */
data class JavaInfo(val path: Path, val major: Int) {
    /** JDK/JRE 根目录名，如 "jdk-17.0.20+7"。 */
    val homeName: String
        get() = path.parent?.parent?.fileName?.toString().orEmpty()
}
