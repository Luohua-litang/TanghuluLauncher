package com.tanghulu.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.tanghulu.launcher.core.DownloadSource
import com.tanghulu.launcher.core.MinecraftLauncher
import com.tanghulu.launcher.core.ModDownloader
import com.tanghulu.launcher.core.ModLoader
import com.tanghulu.launcher.core.ModLoaderInstaller
import com.tanghulu.launcher.core.VersionManager
import com.tanghulu.launcher.ui.theme.AccentOptions
import com.tanghulu.launcher.ui.theme.DefaultAccent
import com.tanghulu.launcher.util.JavaInfo
import com.tanghulu.launcher.util.JavaRuntime
import com.tanghulu.launcher.util.Log
import com.tanghulu.launcher.util.MinecraftNewsService
import com.tanghulu.launcher.util.OperatingSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong

enum class AppPage(val title: String) {
    Home("主页"), Versions("版本"), Mods("Mod"), Account("账户"), News("新闻"), Settings("设置"),
}

data class VersionOption(val id: String, val type: String, val local: Boolean, val releaseTime: String?)

/** 单个文件下载进度。 */
data class FileProgress(val name: String, val done: Long, val total: Long) {
    /** 当前文件完成比例 0..1（total 未知时为 0）。 */
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    /** 总大小是否已知。 */
    val known: Boolean get() = total > 0
}

/**
 * 全局应用状态：配置持久化 + 运行时数据。业务动作见 AppStateActions.kt（扩展函数）。
 */
class AppState {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal val launcher = MinecraftLauncher()
    internal val versionManager = VersionManager()
    internal val modDownloader = ModDownloader()
    internal val loaderInstaller = ModLoaderInstaller()

    var page by mutableStateOf(AppPage.Home)

    // 配置
    var username by mutableStateOf("Steve")
    var javaPath by mutableStateOf("")
    var detectedJavas by mutableStateOf<List<JavaInfo>>(emptyList())
    var javaScanning by mutableStateOf(false)
    var memory by mutableStateOf("2 GB")
    var source by mutableStateOf(DownloadSource.BMCLAPI)
    var jvmArgs by mutableStateOf("")
    var downloadAssets by mutableStateOf(true)
    var gameDir by mutableStateOf("")
    var darkMode by mutableStateOf(true)
    var accentName by mutableStateOf("Green")
    var customAccent by mutableStateOf<Color?>(null)

    // 版本
    var versions by mutableStateOf<List<VersionOption>>(emptyList())
    var versionsLoading by mutableStateOf(false)
    var versionStatus by mutableStateOf("")
    var versionQuery by mutableStateOf("")
    var selectedVersionId by mutableStateOf<String?>(null)
    var installedLoaders by mutableStateOf<Map<ModLoader, String>>(emptyMap())

    // 启动 / 进度
    var launchStatus by mutableStateOf("就绪")
    var launchProgress by mutableStateOf<Int?>(null)
    var fileProgress by mutableStateOf<FileProgress?>(null)
    var launching by mutableStateOf(false)
    var runningProcess: Process? = null

    // Mod
    var modQuery by mutableStateOf("")
    var modLoader by mutableStateOf(ModLoader.FABRIC)
    var modResults by mutableStateOf<List<ModDownloader.Mod>>(emptyList())
    var modStatus by mutableStateOf("输入关键词搜索 Modrinth")
    var modLoading by mutableStateOf(false)
    var modProgress by mutableStateOf<Float?>(null)

    // 新闻
    var news by mutableStateOf<List<MinecraftNewsService.NewsItem>>(emptyList())
    var newsLoading by mutableStateOf(false)
    var newsError by mutableStateOf(false)

    // 皮肤
    var skinVersion by mutableStateOf(0)
    var skinStatus by mutableStateOf("")

    val logs = mutableStateListOf<String>()

    internal val configFile: Path = OperatingSystem.appDataDir().resolve("config.properties")
    private var saveJob: Job? = null
    internal var lastNewsFetch = 0L
    private val progressThrottle = AtomicLong(0L)
    private val fileProgressThrottle = AtomicLong(0L)

    init {
        Log.setUiSink { line -> scope.launch { appendLog(line) } }
        loadConfig()
    }

    // ---------- 配置 ----------

    private fun loadConfig() {
        val p = Properties()
        if (Files.isRegularFile(configFile)) {
            try { Files.newInputStream(configFile).use { p.load(it) } } catch (_: Exception) {}
        }
        username = p.getProperty("username", "Steve")
        accentName = p.getProperty("theme", "Green")
        customAccent = p.getProperty("customThemeColor", "").takeIf { it.isNotBlank() }?.let { parseColor(it) }
        javaPath = p.getProperty("javaPath", "")
        memory = p.getProperty("memory", "2 GB")
        source = DownloadSource.all().firstOrNull { it.getName() == p.getProperty("source", "") } ?: DownloadSource.BMCLAPI
        jvmArgs = p.getProperty("jvmArgs", "")
        downloadAssets = p.getProperty("downloadAssets", "true").toBoolean()
        gameDir = p.getProperty("gameDir", "")
        darkMode = p.getProperty("darkMode", "true").toBoolean()
        if (javaPath.isBlank()) detectJava()
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch { delay(400); saveNow() }
    }

    fun saveNow() {
        val p = Properties()
        p.setProperty("username", username.trim())
        p.setProperty("theme", accentName)
        p.setProperty("customThemeColor", customAccent?.let { colorToHex(it) } ?: "")
        p.setProperty("javaPath", javaPath.trim())
        p.setProperty("memory", memory)
        p.setProperty("source", source.getName())
        p.setProperty("jvmArgs", jvmArgs.trim())
        p.setProperty("downloadAssets", downloadAssets.toString())
        p.setProperty("gameDir", gameDir.trim())
        p.setProperty("darkMode", darkMode.toString())
        scope.launch(Dispatchers.IO) {
            try {
                configFile.parent?.let { Files.createDirectories(it) }
                Files.newOutputStream(configFile).use { p.store(it, "Tanghulu Launcher") }
            } catch (e: Exception) { Log.warn("保存配置失败: ${e.message}") }
        }
    }

    private fun parseColor(hex: String): Color? = try {
        Color(hex.removePrefix("#").toLong(16) or 0xFF000000L)
    } catch (_: Exception) { null }

    private fun colorToHex(c: Color): String =
        String.format("#%02x%02x%02x", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    fun accent(): Color = customAccent
        ?: AccentOptions.firstOrNull { it.name == accentName }?.color
        ?: DefaultAccent

    fun setAccent(name: String) { accentName = name; customAccent = null; scheduleSave() }
    fun setCustomAccent(color: Color) { customAccent = color; accentName = "Custom"; scheduleSave() }

    fun effectiveGameDir(): Path {
        val s = gameDir.trim()
        return if (s.isNotEmpty()) Path.of(s) else OperatingSystem.minecraftDir()
    }

    fun parseMemory(s: String): Int = try {
        val v = s.trim().uppercase()
        val num = v.replace("GB", "").replace("MB", "").trim().toDouble()
        if (v.contains("MB")) num.toInt() else (num * 1024).toInt()
    } catch (_: Exception) { 2048 }

    // ---------- 日志 / 进度 ----------

    internal fun onLog(line: String) { scope.launch { appendLog(line) } }

    internal fun onProgress(percent: Int, stage: String) {
        val now = System.currentTimeMillis()
        if (now - progressThrottle.get() < 100) return
        progressThrottle.set(now)
        scope.launch {
            launchProgress = percent
            launchStatus = "$stage · $percent%"
        }
    }

    internal fun onFileProgress(done: Int, total: Int, fileName: String?, fileDone: Long, fileTotal: Long) {
        val now = System.currentTimeMillis()
        if (now - fileProgressThrottle.get() < 80) return
        fileProgressThrottle.set(now)
        val fp = if (fileName.isNullOrEmpty()) null else FileProgress(fileName, fileDone, fileTotal)
        scope.launch { fileProgress = fp }
    }

    private fun appendLog(line: String) {
        logs.add(line)
        while (logs.size > 5000) logs.removeAt(0)
    }

    internal fun detectInstalledLoaders(): Map<ModLoader, String> {
        val result = LinkedHashMap<ModLoader, String>()
        val gv = selectedVersionId
        val hasGv = !gv.isNullOrEmpty()
        for (id in versionManager.getLocalVersions(effectiveGameDir())) {
            when {
                id.startsWith("fabric-loader-") && (!hasGv || id.endsWith("-$gv")) -> result.putIfAbsent(ModLoader.FABRIC, id)
                id.startsWith("quilt-loader-") && (!hasGv || id.endsWith("-$gv")) -> result.putIfAbsent(ModLoader.QUILT, id)
                id.startsWith("neoforge-") || (hasGv && id.startsWith("$gv-neoforge-")) -> result.putIfAbsent(ModLoader.NEOFORGE, id)
                hasGv && id.startsWith("$gv-forge-") -> result.putIfAbsent(ModLoader.FORGE, id)
            }
        }
        return result
    }

    internal fun formatTime(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return if (iso.length >= 10) iso.substring(0, 10) else iso
    }

    internal fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.'); val pb = b.split('.')
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = leadingInt(pa.getOrNull(i)); val y = leadingInt(pb.getOrNull(i))
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun leadingInt(s: String?): Int {
        if (s.isNullOrEmpty()) return -1
        var i = 0
        while (i < s.length && s[i].isDigit()) i++
        return if (i == 0) -1 else (s.substring(0, i).toIntOrNull() ?: -1)
    }

    fun detectJava() {
        scope.launch(Dispatchers.IO) {
            val found = JavaRuntime.findJava(17) ?: JavaRuntime.findJava(8)
            if (found != null) withContext(Dispatchers.Main) { javaPath = found.toString() }
        }
    }

    /** 扫描本机所有 Java 运行时并填充 [detectedJavas]；[force] 为 true 时强制重新扫描。 */
    fun scanJavas(force: Boolean = false) {
        if (javaScanning || (detectedJavas.isNotEmpty() && !force)) return
        javaScanning = true
        scope.launch(Dispatchers.IO) {
            val list = JavaRuntime.listJavas()
            withContext(Dispatchers.Main) {
                detectedJavas = list
                javaScanning = false
            }
        }
    }
}
