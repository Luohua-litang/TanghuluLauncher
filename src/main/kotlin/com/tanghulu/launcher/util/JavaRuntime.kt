package com.tanghulu.launcher.util

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Java runtime detection and matching: parses the version of a java executable,
 * then finds a Java satisfying the required major version at common local locations.
 */
object JavaRuntime {
    private val VERSION = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?")

    /** Cache of java executable -> major version to avoid running `java -version` repeatedly. */
    private val majorCache = ConcurrentHashMap<Path, Int>()

    /** Detect the major version of a java executable (e.g. 8 / 17 / 21 / 25); returns -1 on failure. */
    @JvmStatic
    fun detectMajor(javaExe: Path?): Int {
        if (javaExe == null || !Files.isRegularFile(javaExe)) return -1
        val key = javaExe.toAbsolutePath().normalize()
        majorCache[key]?.let { return it }
        val major = try {
            val pb = ProcessBuilder(javaExe.toString(), "-version")
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = String(p.inputStream.readAllBytes(), StandardCharsets.UTF_8)
            p.waitFor()
            val m = VERSION.matcher(out)
            if (!m.find()) {
                -1
            } else {
                var v = m.group(1).toInt()
                // JDK 8 and earlier print "1.8.0_xxx"-style versions
                if (v == 1 && m.group(2) != null) {
                    v = m.group(2).toInt()
                }
                v
            }
        } catch (e: Exception) {
            -1
        }
        majorCache[key] = major
        return major
    }

    /**
     * Find a java executable satisfying [requiredMajor].
     * Prefer an exact match; otherwise the nearest higher version; null if none found.
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

    /** Find a java executable whose major version exactly equals [major]; null if none found. */
    @JvmStatic
    fun findJavaExact(major: Int): Path? {
        for (p in candidates()) {
            if (detectMajor(p) == major) return p
        }
        return null
    }

    /**
     * List all candidate local Java runtimes (with major version, newest first).
     * Runs `java -version` for each; call on an IO thread.
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
     * Ensure a Java 8 runtime exists locally (required by old Minecraft versions).
     * Search order: installed Java 8 -> cache directory -> auto-download and extract.
     * Returns the java executable path, or null on failure (does not throw).
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

    /** Download JRE 8 from Adoptium and extract to [dest], returning its java executable. */
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
            // fall back to the fixed mirror URL below when the API is unavailable
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

    /** Extract a zip into the target directory, skipping the first root folder (e.g. jdk8u...-jre/). */
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
                if (!target.startsWith(dest)) continue // prevent path traversal
                Files.createDirectories(target.parent)
                zf.getInputStream(entry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    /** Collect candidate java executables locally (deduped, absolute paths). */
    private fun candidates(): List<Path> {
        val set = LinkedHashSet<Path>()
        val exe = OperatingSystem.CURRENT_OS.javaExecutable()
        // 1. The launcher's own JVM (the bundled runtime when packaged)
        addFromHome(set, System.getProperty("java.home"), exe)
        // 2. JAVA_HOME
        addFromHome(set, System.getenv("JAVA_HOME"), exe)
        // 3. All directories on PATH
        val pathEnv = System.getenv("PATH")
        if (pathEnv != null) {
            for (dir in pathEnv.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                val j = Paths.get(dir, exe)
                if (Files.isRegularFile(j)) set.add(j.toAbsolutePath().normalize())
            }
        }
        // 4. Common Windows install locations
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
                    // ignore unreadable directories
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

/** Info about a single Java runtime detected locally. */
data class JavaInfo(val path: Path, val major: Int) {
    /** JDK/JRE root folder name, e.g. "jdk-17.0.20+7". */
    val homeName: String
        get() = path.parent?.parent?.fileName?.toString().orEmpty()
}
