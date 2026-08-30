package com.tanghulu.launcher.core

import com.tanghulu.launcher.core.model.Library
import com.tanghulu.launcher.util.HttpUtil
import com.tanghulu.launcher.util.Json
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.LinkedHashMap
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * Mod 加载器安装器（借鉴 HMCL 的 FabricInstallTask / NeoForgeInstallTask）。
 * Fabric / Quilt 走官方 meta API 生成 inheritsFrom 版本 JSON；
 * NeoForge / Forge 下载官方 installer 并运行 --installClient。
 */
class ModLoaderInstaller {

    fun interface Progress {
        fun update(percent: Int, stage: String)
    }

    private val downloader = DownloadManager(8)

    /** 列出某游戏版本可用的加载器版本（降序）。 */
    @Throws(IOException::class)
    fun listLoaderVersions(loader: ModLoader, gameVersion: String): List<String> {
        if (!loader.supported) {
            throw UnsupportedOperationException(loader.displayName + " 安装流程尚未实现")
        }
        if (loader.isMeta()) {
            val text = HttpUtil.getText(loader.metaBase!! + "/" + gameVersion)
            val versions = ArrayList<String>()
            for (o in Json.asArray(Json.parse(text)) ?: emptyList()) {
                val m = Json.asObject(o)
                if (m != null) {
                    val v = Json.optString(m, "version")
                    if (v != null) versions.add(v)
                }
            }
            versions.sortByDescending { versionKey(it) }
            return versions
        }
        if (loader == ModLoader.OPTIFINE) return listOptiFineVersions(gameVersion)
        if (loader == ModLoader.FABRIC_API) return listFabricApiVersions(gameVersion)
        if (loader == ModLoader.QSL_QFAPI) return listQslVersions(gameVersion)
        val rel = if (loader == ModLoader.FORGE)
            "net/minecraftforge/forge/" else "net/neoforged/neoforge/"
        val xml = HttpUtil.getText(loader.mavenBase!! + rel + "maven-metadata.xml")
        return extractMavenVersions(xml, gameVersion)
    }

    /** 安装加载器，完成后生成一个可启动的新版本。 */
    @Throws(Exception::class)
    fun install(
        loader: ModLoader, gameDir: Path, gameVersion: String, loaderVersion: String,
        javaPath: Path, log: Consumer<String>, progress: Progress
    ) {
        if (!loader.supported) {
            throw UnsupportedOperationException(loader.displayName + " 安装流程尚未实现")
        }
        when (loader) {
            ModLoader.OPTIFINE -> installOptiFine(gameDir, gameVersion, loaderVersion, log, progress)
            ModLoader.FABRIC_API -> installFabricApi(gameDir, gameVersion, loaderVersion, log, progress)
            ModLoader.QSL_QFAPI -> installQslQfapi(gameDir, gameVersion, loaderVersion, log, progress)
            else -> if (loader.isMeta())
                installMeta(loader, gameDir, gameVersion, loaderVersion, log, progress)
            else installForgeLike(loader, gameDir, loaderVersion, javaPath, log, progress)
        }
    }

    @Throws(IOException::class)
    private fun installMeta(
        loader: ModLoader, gameDir: Path, gameVersion: String, loaderVersion: String,
        log: Consumer<String>, progress: Progress
    ) {
        log.accept("获取 " + loader.displayName + " " + loaderVersion + " 元数据...")
        progress.update(5, "获取加载器元数据")
        val url = loader.metaBase!! + "/" + gameVersion + "/" + loaderVersion
        val meta = Json.asObject(Json.parse(HttpUtil.getText(url)))

        val launcherMeta = Json.asObject(Json.opt(meta, "launcherMeta"))
        val mainClass = extractMainClass(Json.opt(launcherMeta, "mainClass"))

        val libs = Json.asObject(Json.opt(launcherMeta, "libraries"))
        val byName = LinkedHashMap<String, Map<String, Any?>>()
        addMetaLibs(byName, Json.asArray(Json.opt(libs, "common")), loader)
        addMetaLibs(byName, Json.asArray(Json.opt(libs, "client")), loader)
        addMetaLibs(byName, Json.asArray(Json.opt(libs, "server")), loader)

        val mapKey = if (loader == ModLoader.FABRIC) "intermediary" else "hashed"
        addMaven(byName, Json.asObject(Json.opt(meta, mapKey)), loader.mavenBase)
        addMaven(byName, Json.asObject(Json.opt(meta, "loader")), loader.mavenBase)

        val versionId = loader.name.lowercase() + "-loader-" + loaderVersion + "-" + gameVersion
        val patch = LinkedHashMap<String, Any?>()
        patch["id"] = versionId
        patch["inheritsFrom"] = gameVersion
        patch["type"] = "release"
        patch["mainClass"] = mainClass
        patch["libraries"] = ArrayList(byName.values)
        val arguments = Json.opt(launcherMeta, "arguments")
        if (arguments != null) patch["arguments"] = arguments

        val versionDir = gameDir.resolve("versions").resolve(versionId)
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("$versionId.json"), Json.stringify(patch), StandardCharsets.UTF_8)
        log.accept("已生成版本: $versionId")
        progress.update(20, "版本 JSON 已生成")

        val tasks = ArrayList<DownloadTask>()
        for (lm in byName.values) {
            val lib = Library.fromJson(lm) ?: continue
            val url = lib.artifactUrl() ?: continue
            val p = lib.artifactPath() ?: continue
            val target = gameDir.resolve(p)
            if (!Files.isRegularFile(target)) {
                tasks.add(DownloadTask(url, target, lib.artifactSha1(), 0, p))
            }
        }
        if (tasks.isNotEmpty()) {
            log.accept("下载加载器库文件 (" + tasks.size + " 个)...")
            downloader.downloadAll(tasks) { done, total, _, _, _ ->
                progress.update(30 + (done * 70.0 / total).toInt(), "库文件 $done/$total")
            }
        }
        progress.update(100, "安装完成")
        log.accept(loader.displayName + " " + loaderVersion + " 安装完成 → " + versionId)
    }

    @Throws(Exception::class)
    private fun installForgeLike(
        loader: ModLoader, gameDir: Path, loaderVersion: String,
        javaPath: Path, log: Consumer<String>, progress: Progress
    ) {
        val artifact = if (loader == ModLoader.FORGE) "forge" else "neoforge"
        val rel = if (loader == ModLoader.FORGE)
            "net/minecraftforge/forge/" else "net/neoforged/neoforge/"
        val installerUrl = loader.mavenBase!! + rel + loaderVersion + "/" +
            artifact + "-" + loaderVersion + "-installer.jar"
        val installerJar = gameDir.resolve("libraries").resolve(rel)
            .resolve(loaderVersion).resolve("$artifact-$loaderVersion-installer.jar")

        log.accept("下载 " + loader.displayName + " " + loaderVersion + " installer...")
        progress.update(5, "下载 installer")
        HttpUtil.downloadIfNeeded(Collections.singletonList(installerUrl), installerJar, null)

        // 新版 installer 会校验官方启动器的 launcher_profiles.json，缺失会直接失败，这里先补一个空配置
        ensureLauncherProfiles(gameDir)

        // 旧版 Forge（MC 1.7.10 及更早）的 SimpleInstaller 不支持 --installClient，改用反射调用客户端安装
        if (isLegacyForge(loaderVersion)) {
            installLegacyForge(installerJar, gameDir, loader, loaderVersion, log, progress)
        } else {
            installModernForge(installerJar, javaPath, gameDir, loader, loaderVersion, log, progress)
        }
    }

    /** 判断是否为旧版 Forge（对应 Minecraft 1.7.10 及更早，installer 为 SimpleInstaller）。 */
    private fun isLegacyForge(loaderVersion: String): Boolean {
        val mc = loaderVersion.substringBefore('-')
        val parts = mc.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major < 1 || (major == 1 && minor < 8)
    }

    /** 预创建官方启动器配置文件，避免 Forge/OptiFine installer 因缺少该文件而失败。 */
    private fun ensureLauncherProfiles(gameDir: Path) {
        val f = gameDir.resolve("launcher_profiles.json")
        if (!Files.isRegularFile(f)) {
            try {
                Files.writeString(f, "{\"profiles\":{}}", StandardCharsets.UTF_8)
            } catch (e: IOException) {
                // 创建失败不阻断安装，installer 会给出具体错误
            }
        }
    }

    /**
     * 旧版 Forge 的 SimpleInstaller 没有 --installClient 参数，
     * 通过反射调用 InstallerAction.CLIENT.run 完成客户端安装（与 HMCL 一致）。
     */
    @Throws(Exception::class)
    private fun installLegacyForge(
        installerJar: Path, gameDir: Path, loader: ModLoader, loaderVersion: String,
        log: Consumer<String>, progress: Progress
    ) {
        log.accept("运行 " + loader.displayName + " " + loaderVersion + " installer（旧版，反射安装客户端）...")
        progress.update(30, "运行 installer")
        URLClassLoader(
            arrayOf(installerJar.toUri().toURL()),
            ClassLoader.getPlatformClassLoader()
        ).use { cl ->
            val actionClass = cl.loadClass("net.minecraftforge.installer.InstallerAction")
            val client = (actionClass.enumConstants
                ?: throw IOException("InstallerAction 无枚举值"))
                .first { (it as Enum<*>).name == "CLIENT" }
            val predicateClass = cl.loadClass("com.google.common.base.Predicate")
            val runMethod = actionClass.getMethod("run", File::class.java, predicateClass)
            // 使用 installer 自带的 Guava Predicates.alwaysTrue() 作为下载过滤条件（全部下载）
            val predicatesClass = cl.loadClass("com.google.common.base.Predicates")
            val alwaysTrue = predicatesClass.getMethod("alwaysTrue").invoke(null)
            try {
                runMethod.invoke(client, gameDir.toFile(), alwaysTrue)
            } catch (e: InvocationTargetException) {
                val cause = e.cause
                if (cause is Exception) throw cause
                throw IOException(cause?.toString(), cause)
            }
        }
        progress.update(100, "安装完成")
        log.accept(loader.displayName + " " + loaderVersion + " 安装完成")
    }

    /** 新版 Forge/NeoForge 通过 --installClient 参数无头安装。 */
    @Throws(IOException::class)
    private fun installModernForge(
        installerJar: Path, javaPath: Path, gameDir: Path, loader: ModLoader, loaderVersion: String,
        log: Consumer<String>, progress: Progress
    ) {
        log.accept("运行 " + loader.displayName + " " + loaderVersion + " installer（需要联网，可能持续几分钟）...")
        progress.update(30, "运行 installer")
        val cmd = listOf(
            javaPath.toAbsolutePath().toString(),
            "-jar",
            installerJar.toAbsolutePath().toString(),
            "--installClient",
            gameDir.toAbsolutePath().toString()
        )

        val pb = ProcessBuilder(cmd)
        pb.directory(gameDir.toFile())
        pb.redirectErrorStream(true)
        val process = pb.start()
        BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { br ->
            var line = br.readLine()
            while (line != null) {
                log.accept(line)
                line = br.readLine()
            }
        }
        val exit = process.waitFor()
        if (exit != 0) {
            throw IOException(loader.displayName + " installer 执行失败 (exit $exit)")
        }
        progress.update(100, "安装完成")
        log.accept(loader.displayName + " " + loaderVersion + " 安装完成")
    }

    /** 列出某游戏版本可用的 OptiFine 版本（type_patch 形式，如 HD_U_I6）。 */
    @Throws(IOException::class)
    private fun listOptiFineVersions(gameVersion: String): List<String> {
        val text = HttpUtil.getText(OPTIFINE_BASE + "versionList")
        val result = ArrayList<String>()
        for (o in Json.asArray(Json.parse(text)) ?: emptyList()) {
            val m = Json.asObject(o) ?: continue
            if (gameVersion != Json.optString(m, "mcversion")) continue
            val type = Json.optString(m, "type")
            val patch = Json.optString(m, "patch")
            if (type == null || patch == null) continue
            result.add(type + "_" + patch)
        }
        return result
    }

    /** 安装 OptiFine：下载 installer 后反射调用官方 optifine.Installer.doInstall 无 GUI 安装。 */
    @Throws(Exception::class)
    private fun installOptiFine(
        gameDir: Path, gameVersion: String, loaderVersion: String,
        log: Consumer<String>, progress: Progress
    ) {
        val idx = loaderVersion.lastIndexOf('_')
        if (idx <= 0 || idx >= loaderVersion.length - 1) {
            throw IOException("无效的 OptiFine 版本: $loaderVersion")
        }
        val type = loaderVersion.substring(0, idx)
        val patch = loaderVersion.substring(idx + 1)
        val fileName = "OptiFine_" + gameVersion + "_" + type + "_" + patch + ".jar"
        val url = OPTIFINE_BASE + gameVersion + "/" + type + "/" + patch

        val installerJar = gameDir.resolve("libraries/optifine/installer").resolve(fileName)
        log.accept("下载 OptiFine $loaderVersion ...")
        progress.update(5, "下载 OptiFine installer")
        HttpUtil.downloadIfNeeded(url, installerJar, null)

        val clientJar = gameDir.resolve("versions").resolve(gameVersion).resolve("$gameVersion.jar")
        if (!Files.isRegularFile(clientJar)) {
            throw IOException("原版 $gameVersion 尚未下载，请先下载原版")
        }
        // 预创建 launcher_profiles.json，避免 OptiFine installer 弹出 GUI 文件选择器
        ensureLauncherProfiles(gameDir)

        log.accept("安装 OptiFine $loaderVersion（运行官方 installer）...")
        progress.update(20, "运行 OptiFine installer")
        URLClassLoader(
            arrayOf(installerJar.toUri().toURL()),
            ClassLoader.getPlatformClassLoader()
        ).use { cl ->
            val installer = cl.loadClass("optifine.Installer")
            val doInstall: Method = installer.getMethod("doInstall", File::class.java)
            try {
                doInstall.invoke(null, gameDir.toFile())
            } catch (e: InvocationTargetException) {
                val cause = e.cause
                if (cause != null && cause.message == "QUIET") {
                    log.accept("OptiFine 安装完成（跳过官方启动器配置更新）")
                } else if (cause is Exception) {
                    throw cause
                } else {
                    throw IOException(cause?.toString(), cause)
                }
            }
        }
        progress.update(100, "安装完成")
        log.accept("OptiFine $loaderVersion 安装完成")
    }

    /** 列出某游戏版本适配 Fabric 的 Fabric API 版本号。 */
    @Throws(IOException::class)
    private fun listFabricApiVersions(gameVersion: String): List<String> {
        val result = ArrayList<String>()
        for (v in ModDownloader().listVersions(FABRIC_API_SLUG)) {
            if (v.gameVersions.contains(gameVersion) && v.loaders.contains("fabric")) {
                v.versionNumber?.let { result.add(it) }
            }
        }
        return result
    }

    /** 安装 Fabric API：从 Modrinth 下载对应 jar 到游戏目录的 mods 文件夹。 */
    @Throws(IOException::class)
    private fun installFabricApi(
        gameDir: Path, gameVersion: String, loaderVersion: String,
        log: Consumer<String>, progress: Progress
    ) {
        var target: ModDownloader.ModVersion? = null
        for (v in ModDownloader().listVersions(FABRIC_API_SLUG)) {
            if (loaderVersion == v.versionNumber && v.gameVersions.contains(gameVersion)
                && v.loaders.contains("fabric")
            ) {
                target = v
                break
            }
        }
        if (target == null) {
            throw IOException("未找到 Fabric API $loaderVersion（游戏 $gameVersion / fabric）")
        }
        val modsDir = gameDir.resolve("mods")
        Files.createDirectories(modsDir)
        val name = if (!target.fileName.isNullOrEmpty()) target.fileName
        else "$FABRIC_API_SLUG-$loaderVersion.jar"
        val dest = modsDir.resolve(name)
        log.accept("下载 Fabric API $loaderVersion -> mods/$name")
        progress.update(10, "下载 $name")
        HttpUtil.downloadIfNeeded(target.url, dest, target.sha1) { downloaded, total ->
            val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
            progress.update(max(10, min(99, pct)), "下载 $name")
        }
        progress.update(100, "安装完成")
        log.accept("Fabric API $loaderVersion 安装完成")
    }

    /** 列出某游戏版本适配 Quilt 的 QSL 版本号。 */
    @Throws(IOException::class)
    private fun listQslVersions(gameVersion: String): List<String> {
        val result = ArrayList<String>()
        for (v in ModDownloader().listVersions(QSL_SLUG)) {
            if (v.gameVersions.contains(gameVersion) && v.loaders.contains("quilt")) {
                v.versionNumber?.let { result.add(it) }
            }
        }
        return result
    }

    /** 安装 QSL + QFAPI：从 Modrinth 下载两个 jar 到游戏目录的 mods 文件夹。 */
    @Throws(IOException::class)
    private fun installQslQfapi(
        gameDir: Path, gameVersion: String, loaderVersion: String,
        log: Consumer<String>, progress: Progress
    ) {
        val modsDir = gameDir.resolve("mods")
        Files.createDirectories(modsDir)
        log.accept("安装 QSL + QFAPI（适配游戏 $gameVersion / quilt）...")
        downloadModJar(QSL_SLUG, "QSL", gameVersion, "quilt", loaderVersion, modsDir, log, progress, 10, 50)
        downloadModJar(QFAPI_SLUG, "QFAPI", gameVersion, "quilt", null, modsDir, log, progress, 55, 90)
        progress.update(100, "安装完成")
        log.accept("QSL/QFAPI 安装完成（QSL $loaderVersion）")
    }

    /** 通用：从 Modrinth 下载指定 mod 项目的一个 jar 到 mods 目录。 */
    @Throws(IOException::class)
    private fun downloadModJar(
        slug: String, displayName: String, gameVersion: String, loader: String?,
        versionNumber: String?, modsDir: Path, log: Consumer<String>, progress: Progress,
        startPct: Int, endPct: Int
    ) {
        var target: ModDownloader.ModVersion? = null
        for (v in ModDownloader().listVersions(slug)) {
            if ((versionNumber == null || versionNumber == v.versionNumber)
                && v.gameVersions.contains(gameVersion)
                && (loader == null || v.loaders.contains(loader))
            ) {
                target = v
                break
            }
        }
        if (target == null) {
            throw IOException(
                "未找到 " + displayName + (versionNumber?.let { " $it" } ?: "") +
                    "（游戏 " + gameVersion + (loader?.let { " / $it" } ?: "") + "）"
            )
        }
        val name = if (!target.fileName.isNullOrEmpty()) target.fileName
        else "$slug-${target.versionNumber}.jar"
        val dest = modsDir.resolve(name)
        log.accept("下载 $displayName ${target.versionNumber} -> mods/$name")
        progress.update(startPct, "下载 $name")
        HttpUtil.downloadIfNeeded(target.url, dest, target.sha1) { downloaded, total ->
            val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
            val scaled = startPct + (endPct - startPct) * pct / 100
            progress.update(max(startPct, min(endPct, scaled)), "下载 $name")
        }
        log.accept("$displayName ${target.versionNumber} 下载完成")
    }

    companion object {
        /** OptiFine 版本列表与下载（BMCLAPI 镜像，与 HMCL 一致）。 */
        private const val OPTIFINE_BASE = "https://bmclapi2.bangbang93.com/optifine/"
        private const val FABRIC_API_SLUG = "fabric-api"
        private const val QSL_SLUG = "qsl"
        private const val QFAPI_SLUG = "qfapi"

        private fun extractMainClass(mc: Any?): String? {
            if (mc is String) return mc
            val m = Json.asObject(mc)
            if (m != null) {
                val client = Json.optString(m, "client")
                if (client != null) return client
            }
            return null
        }

        private fun addMetaLibs(
            byName: LinkedHashMap<String, Map<String, Any?>>, libs: List<Any?>?, loader: ModLoader
        ) {
            if (libs == null) return
            for (o in libs) {
                val lm = Json.asObject(o) ?: continue
                val name = Json.optString(lm, "name") ?: continue
                val base = Json.optString(lm, "url")
                byName.putIfAbsent(name, mavenLibrary(name, base))
            }
        }

        private fun addMaven(
            byName: LinkedHashMap<String, Map<String, Any?>>, obj: Map<String, Any?>?, defaultBase: String?
        ) {
            if (obj == null) return
            val maven = Json.optString(obj, "maven") ?: return
            byName.putIfAbsent(maven, mavenLibrary(maven, defaultBase))
        }

        private fun mavenLibrary(maven: String, baseUrl: String?): Map<String, Any?> {
            val lib = LinkedHashMap<String, Any?>()
            lib["name"] = maven
            if (baseUrl != null) {
                val artifact = LinkedHashMap<String, Any?>()
                artifact["url"] = baseUrl + mavenToPath(maven)
                val downloads = LinkedHashMap<String, Any?>()
                downloads["artifact"] = artifact
                lib["downloads"] = downloads
            }
            return lib
        }

        private fun mavenToPath(maven: String): String {
            val parts = maven.split(":")
            if (parts.size < 3) return maven
            val group = parts[0]
            val artifact = parts[1]
            val version = parts[2]
            return group.replace('.', '/') + "/" + artifact + "/" + version +
                "/" + artifact + "-" + version + ".jar"
        }

        private fun extractMavenVersions(xml: String, gameVersion: String): List<String> {
            val prefixes = ArrayList<String>()
            prefixes.add("$gameVersion-")
            val parts = gameVersion.split(".")
            if (parts.size >= 2) {
                prefixes.add(parts[1] + (if (parts.size >= 3) "." + parts[2] else ".0") + ".")
            }
            val result = ArrayList<String>()
            val m = Pattern.compile("<version>([^<]+)</version>").matcher(xml)
            while (m.find()) {
                val v = m.group(1)
                for (p in prefixes) {
                    if (v.startsWith(p)) {
                        result.add(v)
                        break
                    }
                }
            }
            result.sortByDescending { versionKey(it) }
            return result
        }

        /** 版本号排序键：按数字段拆分补零，保证 0.15.11 > 0.15.9。 */
        private fun versionKey(v: String): String {
            val sb = StringBuilder()
            for (part in v.split(Regex("[^0-9]+"))) {
                if (part.isEmpty()) continue
                val n = try {
                    part.toLong()
                } catch (e: NumberFormatException) {
                    0L
                }
                sb.append(String.format("%010d", n)).append('.')
            }
            return sb.toString()
        }
    }
}
