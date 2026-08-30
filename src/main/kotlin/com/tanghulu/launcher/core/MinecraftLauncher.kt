package com.tanghulu.launcher.core

import com.tanghulu.launcher.core.model.Library
import com.tanghulu.launcher.core.model.Rule
import com.tanghulu.launcher.core.model.VersionInfo
import com.tanghulu.launcher.util.FileUtil
import com.tanghulu.launcher.util.HttpUtil
import com.tanghulu.launcher.util.JavaRuntime
import com.tanghulu.launcher.util.Json
import com.tanghulu.launcher.util.OperatingSystem
import com.tanghulu.launcher.util.SkinManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Minecraft 启动核心：解析版本、下载依赖、构建参数并启动游戏进程。
 */
class MinecraftLauncher {

    fun interface LogSink {
        fun log(line: String)
    }

    fun interface ProgressSink {
        fun progress(percent: Int, stage: String)
    }

    fun interface DownloadSink {
        fun onDownload(done: Int, total: Int, fileName: String?, fileDone: Long, fileTotal: Long)
    }

    class LaunchOptions {
        @JvmField var gameDir: Path? = null
        @JvmField var versionId: String? = null
        @JvmField var username: String? = null
        @JvmField var javaPath: Path? = null
        @JvmField var minMemoryMB: Int = 512
        @JvmField var maxMemoryMB: Int = 2048
        @JvmField var source: DownloadSource = DownloadSource.OFFICIAL
        @JvmField var width: Int = 0
        @JvmField var height: Int = 0
        @JvmField var serverIp: String? = null
        @JvmField var cleanNatives: Boolean = true
        @JvmField var downloadAssets: Boolean = true
        @JvmField var extraJvmArgs: String = ""
        @JvmField var extraGameArgs: String = ""
    }

    class PreparedVersion(
        @JvmField val info: VersionInfo,
        @JvmField val versionJar: Path,
        @JvmField val nativesDir: Path,
        @JvmField val allowedLibs: List<Library>
    )

    companion object {
        const val LAUNCHER_NAME = "TanghuluLauncher"
        const val LAUNCHER_VERSION = "1.0.0"

        private val TOKEN_PATTERN = Pattern.compile("\\$\\{(.*?)}")

        private fun isUnusedQuickPlay(args: List<String>): Boolean {
            var hasQuickPlay = false
            var hasValue = false
            for (s in args) {
                if (s.startsWith("--quickPlay")) hasQuickPlay = true
                else if (s.isNotEmpty()) hasValue = true
            }
            return hasQuickPlay && !hasValue
        }

        private fun splitArgs(args: String): List<String> {
            val out = ArrayList<String>()
            for (part in args.split("\\s+".toRegex())) {
                if (part.isNotEmpty()) out.add(part)
            }
            return out
        }
    }

    private val downloader = DownloadManager(8)
    private val versionManager = VersionManager()
    private val assetManager = AssetManager()

    private var log4jConfigPath: Path? = null

    @Throws(Exception::class)
    fun prepareVersion(o: LaunchOptions, log: LogSink, progress: ProgressSink, download: DownloadSink? = null): PreparedVersion {
        val gameDir = o.gameDir ?: throw IllegalArgumentException("游戏目录未设置")
        val versionId = o.versionId ?: throw IllegalArgumentException("版本未选择")
        val source = o.source ?: throw IllegalArgumentException("下载源未设置")

        Files.createDirectories(gameDir)
        log.log("=== Tanghulu Launcher " + LAUNCHER_VERSION + " ===")
        log.log("游戏目录: " + gameDir.toAbsolutePath())
        log.log("版本: " + versionId + " | 下载源: " + source.getName())
        progress.progress(2, "解析版本信息...")

        // 1. 版本 JSON（合并 inheritsFrom 继承链）
        val resolved = versionManager.resolve(gameDir, versionId, source)
        var info = VersionInfo.parse(resolved.json)
        if (info.id == null) {
            info = VersionInfo(versionId, info.mainClass, info.type, info.assets,
                info.assetIndex, info.clientDownload, info.libraries, info.gameArguments,
                info.jvmArguments, info.minecraftArguments, info.javaMajorVersion,
                info.minimumLauncherVersion)
        }
        log.log("主类: " + info.mainClass + " | 要求 Java " + info.javaMajorVersion + "+")
        val javaPath = o.javaPath
        if (javaPath != null) {
            o.javaPath = matchJava(javaPath, info.javaMajorVersion, log)
        }
        progress.progress(5, "版本: " + versionId)

        // 2. 版本 jar
        val versionJar = versionManager.localVersionJar(gameDir, resolved.jarId)
        val clientSha1 = info.clientDownload?.sha1
        val jarOk = Files.isRegularFile(versionJar) &&
            (clientSha1 == null || clientSha1.equals(FileUtil.sha1(versionJar), true))
        if (!jarOk) {
            val clientUrl = info.clientDownload?.url
            if (clientUrl == null) {
                log.log("警告: 无版本 jar 下载地址，跳过（可能为 Forge/自定义版本）")
            } else {
                log.log("下载版本 jar: " + resolved.jarId)
                val jarCandidates = ArrayList<String>()
                jarCandidates.addAll(source.versionJarCandidates(resolved.jarId, clientSha1))
                if (!jarCandidates.contains(clientUrl)) {
                    if (source == DownloadSource.OFFICIAL) jarCandidates.add(0, clientUrl)
                    else jarCandidates.add(clientUrl)
                }
                HttpUtil.downloadIfNeeded(jarCandidates, versionJar, clientSha1) { d, t ->
                    val frac = if (t > 0) (d.toDouble() / t).coerceIn(0.0, 1.0) else 0.0
                    progress.progress(2 + (frac * 3).toInt(), "下载版本 jar")
                    download?.onDownload(0, 1, "版本 jar", d, t)
                }
            }
        }

        // 3. 库文件与 natives
        val nativesKey = "natives-" + OperatingSystem.CURRENT_OS.mojangName()
        val customRes = o.width > 0 || o.height > 0
        val allowedLibs = ArrayList<Library>()
        val libTasks = ArrayList<DownloadTask>()
        val nativeJars = ArrayList<Path>()
        val nativeTasks = ArrayList<DownloadTask>()
        for (lib in info.libraries) {
            if (!lib.isAllowed(OperatingSystem.CURRENT_OS.mojangName(), customRes, false)) continue
            allowedLibs.add(lib)
            val p = lib.artifactPath()
            if (p == null) continue
            val target = gameDir.resolve(p)
            if (Files.isRegularFile(target)) {
                val sha1 = lib.artifactSha1()
                if (sha1 != null && sha1.isNotEmpty() && !sha1.equals(FileUtil.sha1(target), true)) {
                    libTasks.add(DownloadTask(source.libraryCandidates(lib.artifactUrl()), target, sha1, 0, p))
                }
            } else if (lib.artifactUrl() != null) {
                libTasks.add(DownloadTask(source.libraryCandidates(lib.artifactUrl()), target,
                    lib.artifactSha1(), 0, p))
            } else if (!lib.classifiers.containsKey(nativesKey)) {
                log.log("警告: 缺少库文件且无下载地址: " + p)
            }
            val natives = lib.classifiers[nativesKey]
            if (natives != null && natives.url != null && natives.path != null) {
                val nTarget = gameDir.resolve(natives.path)
                if (Files.isRegularFile(nTarget) &&
                    (natives.sha1 == null || natives.sha1.isEmpty() ||
                        natives.sha1.equals(FileUtil.sha1(nTarget), true))
                ) {
                    nativeJars.add(nTarget)
                } else {
                    nativeTasks.add(DownloadTask(source.libraryCandidates(natives.url), nTarget,
                        natives.sha1, natives.size, "native: " + natives.path))
                }
            }
        }
        if (libTasks.isNotEmpty()) {
            log.log("下载库文件 (" + libTasks.size + " 个)...")
            downloader.downloadAll(libTasks) { done, total, current, fileDone, fileTotal ->
                val frac = if (fileTotal > 0) (fileDone.toDouble() / fileTotal).coerceIn(0.0, 1.0) else 0.0
                progress.progress(10 + ((done + frac) / total * 25).toInt(), "库文件 $done/$total")
                download?.onDownload(done, total, current, fileDone, fileTotal)
            }
        }
        if (nativeTasks.isNotEmpty()) {
            log.log("下载 natives (" + nativeTasks.size + " 个)...")
            downloader.downloadAll(nativeTasks) { done, total, current, fileDone, fileTotal ->
                val frac = if (fileTotal > 0) (fileDone.toDouble() / fileTotal).coerceIn(0.0, 1.0) else 0.0
                progress.progress(38 + ((done + frac) / total * 6).toInt(), "natives $done/$total")
                download?.onDownload(done, total, current, fileDone, fileTotal)
            }
        }

        // 4. 解压 natives
        val nativesDir = gameDir.resolve("versions").resolve(versionId).resolve("natives")
        if (o.cleanNatives) NativeExtractor.clean(nativesDir)
        Files.createDirectories(nativesDir)
        for (nj in nativeJars) {
            if (Files.isRegularFile(nj)) NativeExtractor.extract(nj, nativesDir)
        }
        progress.progress(45, "natives 就绪")

        // 5. assets
        if (o.downloadAssets) {
            log.log("检查资源文件...")
            val assetIndex = assetManager.loadOrDownloadIndex(
                gameDir, info.assets ?: versionId,
                source.assetIndex(info.assetIndex?.url, info.assets))
            val assetTasks = assetManager.buildObjectTasks(gameDir, assetIndex, source)
            if (assetTasks.isNotEmpty()) {
                log.log("下载资源文件 (" + assetTasks.size + " 个)...")
                downloader.downloadAll(assetTasks) { done, total, current, fileDone, fileTotal ->
                    val frac = if (fileTotal > 0) (fileDone.toDouble() / fileTotal).coerceIn(0.0, 1.0) else 0.0
                    progress.progress(45 + ((done + frac) / total * 35).toInt(), "资源 $done/$total")
                    download?.onDownload(done, total, current, fileDone, fileTotal)
                }
            } else {
                log.log("资源文件已是最新")
            }
            resolveLog4jConfig(gameDir, info, assetIndex)
        }
        progress.progress(100, "版本文件已就绪")
        return PreparedVersion(info, versionJar, nativesDir, allowedLibs)
    }

    @Throws(Exception::class)
    fun launch(o: LaunchOptions, log: LogSink, progress: ProgressSink, download: DownloadSink? = null): Process {
        val gameDir = o.gameDir ?: throw IllegalArgumentException("游戏目录未设置")
        val versionId = o.versionId ?: throw IllegalArgumentException("版本未选择")
        val username = o.username ?: throw IllegalArgumentException("用户名未设置")
        val javaPath = o.javaPath
        if (javaPath == null || !Files.isRegularFile(javaPath)) {
            throw IllegalArgumentException("Java 路径无效: " + o.javaPath)
        }

        val prepared = prepareVersion(o, log, progress, download)
        val info = prepared.info
        val versionJar = prepared.versionJar
        val nativesDir = prepared.nativesDir
        val allowedLibs = prepared.allowedLibs
        log.log("用户名: " + username)
        log.log("构建启动参数...")

        val customRes = o.width > 0 || o.height > 0

        // 6. 构建参数
        val tokens = buildTokens(o, info, versionJar, nativesDir, allowedLibs)
        val jvmArgs = ArrayList<String>()
        val gameArgs = ArrayList<String>()

        if (info.jvmArguments.isNotEmpty()) {
            jvmArgs.addAll(resolveArguments(info.jvmArguments, tokens, customRes, false))
        } else {
            jvmArgs.add("-Djava.library.path=" + nativesDir.toAbsolutePath())
            jvmArgs.add("-cp")
            jvmArgs.add(resolveTokens("\${classpath}", tokens))
        }
        if (info.gameArguments.isNotEmpty()) {
            gameArgs.addAll(resolveArguments(info.gameArguments, tokens, customRes, false))
        } else if (info.minecraftArguments != null) {
            for (part in info.minecraftArguments.split("\\s+".toRegex())) {
                if (part.isNotEmpty()) gameArgs.add(resolveTokens(part, tokens))
            }
        }

        if (!jvmArgs.contains("-Dlog4j2.formatMsgNoLookups=true")) {
            jvmArgs.add("-Dlog4j2.formatMsgNoLookups=true")
        }
        if (info.minimumLauncherVersion >= 21) {
            jvmArgs.add("-Dminecraft.clientId=" + UUID.randomUUID())
        }
        jvmArgs.add("-Dminecraft.launcher.brand=" + LAUNCHER_NAME)
        jvmArgs.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION)

        // 7. 组装启动命令
        val cmd = ArrayList<String>()
        cmd.add(javaPath.toAbsolutePath().toString())
        var hasAuthlib = false
        for (lib in allowedLibs) {
            if (lib.name != null && lib.name.startsWith("com.mojang:authlib")) {
                hasAuthlib = true
                break
            }
        }
        injectSkinSupport(o, cmd, log, hasAuthlib)
        cmd.add("-Xms" + o.minMemoryMB + "M")
        cmd.add("-Xmx" + o.maxMemoryMB + "M")
        cmd.add("-XX:MetaspaceSize=512M")
        if (o.extraJvmArgs.trim().isNotEmpty()) {
            cmd.addAll(splitArgs(o.extraJvmArgs.trim()))
        }
        val extra = o.extraJvmArgs.lowercase()
        if (!extra.contains("-xx:+use") && !extra.contains("-xx:use")) {
            cmd.add("-XX:+UnlockExperimentalVMOptions")
            cmd.add("-XX:+UseG1GC")
            cmd.add("-XX:G1MixedGCCountTarget=5")
            cmd.add("-XX:G1NewSizePercent=20")
            cmd.add("-XX:G1ReservePercent=20")
            cmd.add("-XX:MaxGCPauseMillis=50")
            cmd.add("-XX:G1HeapRegionSize=32M")
        }
        cmd.addAll(jvmArgs)
        cmd.add(info.mainClass ?: "net.minecraft.client.main.Main")
        cmd.addAll(gameArgs)
        if (o.extraGameArgs.trim().isNotEmpty()) {
            cmd.addAll(splitArgs(o.extraGameArgs.trim()))
        }

        log.log("启动命令: " + cmd.joinToString(" "))

        val pb = ProcessBuilder(cmd)
        pb.directory(gameDir.toFile())
        pb.redirectErrorStream(true)
        val process = pb.start()

        Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { br ->
                    var line = br.readLine()
                    while (line != null) {
                        log.log(line)
                        line = br.readLine()
                    }
                }
            } catch (ignored: IOException) {
            }
        }, "game-log").apply { isDaemon = true }.start()

        progress.progress(100, "游戏已启动")
        log.log("游戏进程已启动 (PID " + process.pid() + ")")
        return process
    }

    private fun injectSkinSupport(o: LaunchOptions, cmd: MutableList<String>, log: LogSink, hasAuthlib: Boolean) {
        if (!hasAuthlib) {
            LocalSkinServer.stop()
            return
        }
        if (!SkinManager.hasSkin(o.username)) {
            LocalSkinServer.stop()
            return
        }
        val apiRoot = LocalSkinServer.startFor(o.username)
        if (apiRoot == null) {
            log.log("[皮肤] 本地皮肤服务器启动失败，游戏内将显示默认皮肤")
            return
        }
        var injector = authlibInjectorJar()
        if (injector == null) {
            log.log("[皮肤] 未找到 authlib-injector，尝试下载...")
            injector = downloadAuthlibInjector(log)
        }
        if (injector == null) {
            log.log("[皮肤] authlib-injector 下载失败，游戏内将显示默认皮肤")
            return
        }
        cmd.add("-javaagent:" + injector.toAbsolutePath() + "=" + apiRoot)
        log.log("[皮肤] 已注入 authlib-injector，游戏内将显示自定义皮肤")
    }

    private fun authlibInjectorJar(): Path? {
        val f = OperatingSystem.appDataDir().resolve("authlib-injector.jar")
        return if (Files.isRegularFile(f)) f else null
    }

    private fun downloadAuthlibInjector(log: LogSink): Path? {
        val target = OperatingSystem.appDataDir().resolve("authlib-injector.jar")
        try {
            Files.createDirectories(target.parent)
            var url: String? = null
            val latest = HttpUtil.getTextOnce("https://authlib-injector.yushi.moe/artifact/latest.json")
            if (!latest.isNullOrBlank()) {
                val obj = Json.asObject(Json.parse(latest))
                if (obj != null) url = Json.optString(obj, "download_url")
            }
            if (url.isNullOrBlank()) {
                url = "https://github.com/yushijinhun/authlib-injector/releases/latest/download/authlib-injector-1.2.5.jar"
            }
            log.log("[皮肤] 正在下载 authlib-injector ...")
            val data = HttpUtil.get(url!!)
            if (data.isEmpty()) return null
            Files.write(target, data)
            log.log("[皮肤] authlib-injector 下载完成")
            return target
        } catch (e: Exception) {
            log.log("[皮肤] authlib-injector 下载失败: " + e.message)
            return null
        }
    }

    private fun resolveLog4jConfig(gameDir: Path, info: VersionInfo, assetIndex: Map<String, Any?>) {
        val objects = Json.asObject(assetIndex["objects"])
        if (objects == null) return
        for ((key, value) in objects) {
            if (!key.startsWith("log_configs/")) continue
            val entry = Json.asObject(value)
            val hash = Json.optString(entry, "hash")
            if (hash == null || hash.length < 2) continue
            val src = gameDir.resolve("assets/objects").resolve(hash.substring(0, 2)).resolve(hash)
            if (Files.isRegularFile(src)) {
                val dst = gameDir.resolve("assets/log_configs")
                    .resolve(key.substring("log_configs/".length))
                try {
                    Files.createDirectories(dst.parent)
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                    log4jConfigPath = dst
                    return
                } catch (ignored: IOException) {
                }
            }
        }
    }

    private fun buildTokens(o: LaunchOptions, info: VersionInfo, versionJar: Path,
                            nativesDir: Path, allowedLibs: List<Library>): Map<String, String> {
        val uuid = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + o.username).toByteArray(StandardCharsets.UTF_8))
        val uuidStr = uuid.toString().replace("-", "")
        val accessToken = UUID.randomUUID().toString().replace("-", "")
        val assetsRoot = o.gameDir!!.resolve("assets").toAbsolutePath().toString()
        val gameDirStr = o.gameDir!!.toAbsolutePath().toString()

        val cp = StringBuilder()
        for (lib in allowedLibs) {
            val p = lib.artifactPath()
            if (p == null) continue
            val f = o.gameDir!!.resolve(p)
            if (Files.isRegularFile(f)) {
                cp.append(f.toAbsolutePath())
                    .append(if (OperatingSystem.CURRENT_OS.isWindows()) ";" else ":")
            }
        }
        cp.append(versionJar.toAbsolutePath())

        val t = HashMap<String, String>()
        t["auth_player_name"] = o.username ?: ""
        t["auth_uuid"] = uuidStr
        t["auth_access_token"] = accessToken
        t["auth_session"] = accessToken + ":" + uuidStr
        t["user_type"] = "legacy"
        t["user_properties"] = "{}"
        t["clientid"] = UUID.randomUUID().toString()
        t["version_name"] = o.versionId ?: ""
        t["version_type"] = info.type ?: "release"
        t["game_directory"] = gameDirStr
        t["game_assets"] = gameDirStr + "/assets/virtual/legacy"
        t["assets_root"] = assetsRoot
        t["assets_index_name"] = info.assets ?: o.versionId ?: ""
        t["natives_directory"] = nativesDir.toAbsolutePath().toString()
        t["library_directory"] = o.gameDir!!.resolve("libraries").toAbsolutePath().toString()
        t["classpath"] = cp.toString()
        t["launcher_name"] = LAUNCHER_NAME
        t["launcher_version"] = LAUNCHER_VERSION
        t["resolution_width"] = (if (o.width > 0) o.width else 854).toString()
        t["resolution_height"] = (if (o.height > 0) o.height else 480).toString()
        t["quickPlayPath"] = ""
        t["quickPlaySingleplayer"] = ""
        t["quickPlayMultiplayer"] = ""
        t["quickPlayRealms"] = ""
        t["path"] = log4jConfigPath?.toAbsolutePath()?.toString() ?: ""
        if (!o.serverIp.isNullOrEmpty()) {
            val parts = o.serverIp!!.split(":")
            t["server_address"] = parts[0]
            t["server_port"] = if (o.serverIp!!.contains(":")) parts[1] else "25565"
        } else {
            t["server_address"] = ""
            t["server_port"] = ""
        }
        return t
    }

    private fun resolveArguments(raw: List<Any?>, tokens: Map<String, String>,
                                 customRes: Boolean, demo: Boolean): List<String> {
        val out = ArrayList<String>()
        for (item in raw) {
            if (item is String) {
                out.add(resolveTokens(item, tokens))
            } else if (item is Map<*, *>) {
                val obj = Json.asObject(item) ?: continue
                if (!matchesRules(parseRules(Json.asArray(obj["rules"])), customRes, demo)) continue
                val value = obj["value"]
                if (value is String) {
                    val resolved = resolveTokens(value, tokens)
                    if (!resolved.startsWith("--quickPlay")) out.add(resolved)
                } else if (value is List<*>) {
                    val expanded = ArrayList<String>()
                    for (v in value) {
                        if (v is String) expanded.add(resolveTokens(v, tokens))
                    }
                    if (!isUnusedQuickPlay(expanded)) out.addAll(expanded)
                }
            }
        }
        return out
    }

    private fun parseRules(raw: List<Any?>?): List<Rule> {
        val rules = ArrayList<Rule>()
        if (raw != null) {
            for (o in raw) {
                val rm = Json.asObject(o)
                if (rm != null) rules.add(Rule.fromJson(rm))
            }
        }
        return rules
    }

    private fun matchesRules(rules: List<Rule>?, customRes: Boolean, demo: Boolean): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (r in rules) {
            if (r.matches(OperatingSystem.CURRENT_OS.mojangName(), customRes, demo)) {
                allowed = r.allows()
            }
        }
        return allowed
    }

    private fun resolveTokens(arg: String, tokens: Map<String, String>): String {
        val m = TOKEN_PATTERN.matcher(arg)
        val sb = StringBuilder()
        while (m.find()) {
            val key = m.group(1)
            val value = tokens[key]
            m.appendReplacement(sb, Matcher.quoteReplacement(if (value == null) "" else value))
        }
        m.appendTail(sb)
        return sb.toString()
    }

    private fun matchJava(configured: Path, requiredMajor0: Int, log: LogSink): Path {
        var requiredMajor = requiredMajor0
        if (requiredMajor <= 0) requiredMajor = 8
        val current = JavaRuntime.detectMajor(configured)

        if (requiredMajor <= 8) {
            if (current == 8) return configured
            val java8 = JavaRuntime.ensureJava8 { line -> log.log(line) }
            if (java8 != null && !samePath(configured, java8)) {
                log.log("检测到老版本（要求 Java 8），已自动切换 Java 8: " + java8.toAbsolutePath())
                return java8
            }
            if (current > 8) {
                log.log("警告: 当前为老版本（要求 Java 8），本机未检测到 Java 8。" +
                    "Java 9+ 可能导致启动失败（如 launchwrapper 不兼容）。" +
                    "建议安装 Java 8 后在「设置」中指定其路径。")
            }
            return configured
        }

        if (current > 0 && current >= requiredMajor) return configured
        if (current > 0) {
            log.log("当前 Java " + current + " 低于版本要求（Java " + requiredMajor + "+），正在自动匹配...")
        } else {
            log.log("未检测到有效 Java 版本，正在自动匹配 Java " + requiredMajor + "+ ...")
        }
        val better = JavaRuntime.findJava(requiredMajor)
        if (better != null && !samePath(configured, better)) {
            log.log("已自动切换 Java: " + better.toAbsolutePath())
            return better
        }
        if (current > 0) {
            throw IllegalArgumentException(
                "该版本需要 Java " + requiredMajor + "+，当前 Java 为 " + current +
                    "，且未找到更高版本。请在「设置」中手动指定 Java " + requiredMajor + "+ 的路径。")
        }
        log.log("警告: 未找到匹配的 Java，继续使用原路径尝试启动。")
        return configured
    }

    private fun samePath(a: Path?, b: Path?): Boolean =
        a != null && b != null &&
            a.toAbsolutePath().normalize() == b.toAbsolutePath().normalize()
}
