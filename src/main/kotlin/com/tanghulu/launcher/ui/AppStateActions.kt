package com.tanghulu.launcher.ui

import com.tanghulu.launcher.core.DownloadSource
import com.tanghulu.launcher.core.MinecraftLauncher
import com.tanghulu.launcher.core.ModDownloader
import com.tanghulu.launcher.core.ModLoader
import com.tanghulu.launcher.util.JavaRuntime
import com.tanghulu.launcher.util.Log
import com.tanghulu.launcher.util.MinecraftNewsService
import com.tanghulu.launcher.util.SkinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

// ================= 版本 =================

fun AppState.loadVersions() {
    if (versionsLoading) return
    versionsLoading = true
    versionStatus = "正在加载版本列表…"
    scope.launch(Dispatchers.IO) {
        val localIds = versionManager.getLocalVersions(effectiveGameDir()).toHashSet()
        val list = ArrayList<VersionOption>()
        var ok = false
        for (s in DownloadSource.all()) {
            try {
                val manifest = versionManager.fetchManifest(s)
                for (e in versionManager.listManifestVersions(manifest)) {
                    list.add(VersionOption(e.id, e.type, localIds.contains(e.id), formatTime(e.releaseTime)))
                }
                ok = true; break
            } catch (ex: Exception) { Log.warn("源 ${s.getName()} 拉取失败: ${ex.message}") }
        }
        if (!ok) for (id in localIds) list.add(VersionOption(id, "本地", true, null))
        list.sortWith { a, b ->
            val la = if (a.local) 0 else 1
            val lb = if (b.local) 0 else 1
            if (la != lb) la - lb else compareVersions(b.id, a.id)
        }
        withContext(Dispatchers.Main) {
            versions = list
            versionsLoading = false
            versionStatus = "共 ${list.size} 个版本"
            if (list.isNotEmpty() && selectedVersionId == null) selectVersion(list[0].id)
            else if (list.isNotEmpty()) refreshInstalledLoaders()
        }
    }
}

fun AppState.selectVersion(id: String) {
    selectedVersionId = id
    refreshInstalledLoaders()
}

fun AppState.refreshInstalledLoaders() {
    installedLoaders = detectInstalledLoaders()
}

// ================= 启动 / 下载 / 安装 =================

fun AppState.launchGame(): Boolean {
    val name = username.trim()
    if (name.isEmpty()) { launchStatus = "请先输入玩家名"; return false }
    val vid = selectedVersionId
    if (vid.isNullOrEmpty()) { launchStatus = "请先选择游戏版本"; return false }
    val java = javaPath.trim()
    if (java.isEmpty()) { launchStatus = "请先在设置中配置 Java 路径"; return false }
    val javaP = try { Path.of(java) } catch (_: Exception) { launchStatus = "Java 路径无效"; return false }

    launching = true
    launchStatus = "准备启动…"
    launchProgress = 0
    logs.clear()

    val opt = MinecraftLauncher.LaunchOptions().apply {
        gameDir = this@launchGame.effectiveGameDir()
        versionId = vid
        username = name
        javaPath = javaP
        minMemoryMB = 512
        maxMemoryMB = parseMemory(memory)
        source = this@launchGame.source
        downloadAssets = this@launchGame.downloadAssets
        extraJvmArgs = this@launchGame.jvmArgs.trim()
    }
    scope.launch(Dispatchers.IO) {
        try {
            val p = launcher.launch(opt, { onLog(it) }, { pct, stg -> onProgress(pct, stg) },
                { done, total, name, fd, ft -> onFileProgress(done, total, name, fd, ft) })
            withContext(Dispatchers.Main) {
                runningProcess = p; launching = false; launchProgress = null; fileProgress = null
                launchStatus = "游戏运行中 · PID ${p.pid()}"
            }
        } catch (e: Exception) {
            Log.error("启动失败", e)
            withContext(Dispatchers.Main) {
                launching = false; launchProgress = null; fileProgress = null; launchStatus = "启动失败: ${e.message}"
            }
        }
    }
    return true
}

fun AppState.downloadVersion(versionId: String) {
    if (versionId.isBlank()) return
    launchStatus = "正在下载 $versionId …"; launchProgress = 0
    val opt = MinecraftLauncher.LaunchOptions().apply {
        gameDir = effectiveGameDir(); this.versionId = versionId
        source = this@downloadVersion.source; downloadAssets = true
    }
    scope.launch(Dispatchers.IO) {
        try {
            launcher.prepareVersion(opt, { onLog(it) }, { pct, stg -> onProgress(pct, stg) },
                { done, total, name, fd, ft -> onFileProgress(done, total, name, fd, ft) })
            withContext(Dispatchers.Main) {
                launchProgress = null; fileProgress = null; launchStatus = "$versionId 已就绪"; loadVersions()
            }
        } catch (e: Exception) {
            Log.error("下载版本失败", e)
            withContext(Dispatchers.Main) { launchProgress = null; fileProgress = null; launchStatus = "下载失败: ${e.message}" }
        }
    }
}

fun AppState.deleteVersion(versionId: String) {
    if (versionId.isBlank()) return
    scope.launch(Dispatchers.IO) {
        val ok = versionManager.deleteVersion(effectiveGameDir(), versionId)
        withContext(Dispatchers.Main) {
            if (ok) {
                if (selectedVersionId == versionId) selectedVersionId = null
                versionStatus = "已删除 $versionId"
                loadVersions()
            } else {
                versionStatus = "删除失败: $versionId"
            }
        }
    }
}

fun AppState.installGame(versionId: String, instanceName: String, loaders: Map<ModLoader, String>) {
    if (versionId.isBlank()) return
    val label = instanceName.ifBlank { versionId }
    launchStatus = "正在安装 $label …"; launchProgress = 0
    scope.launch(Dispatchers.IO) {
        try {
            val opt = MinecraftLauncher.LaunchOptions().apply {
                gameDir = effectiveGameDir(); this.versionId = versionId
                source = this@installGame.source; downloadAssets = true
            }
            launcher.prepareVersion(opt, { onLog(it) }, { pct, stg -> onProgress(pct, stg) },
                { done, total, name, fd, ft -> onFileProgress(done, total, name, fd, ft) })
            for ((loader, lv) in loaders) {
                withContext(Dispatchers.Main) { launchStatus = "正在安装 ${loader.displayName} $lv …" }
                val java: Path = if (loader.requiresJava()) {
                    val j = javaPath.trim().ifBlank { null } ?: JavaRuntime.findJava(17)?.toString()
                    if (j.isNullOrBlank()) throw IllegalStateException("${loader.displayName} 需要 Java，请先配置")
                    Path.of(j)
                } else Path.of("")
                loaderInstaller.install(loader, effectiveGameDir(), versionId, lv, java,
                    { onLog(it) }, { pct, stg -> onProgress(pct, stg) })
            }
            withContext(Dispatchers.Main) {
                launchProgress = null; fileProgress = null; launchStatus = "$label 安装完成"; loadVersions()
            }
        } catch (e: Exception) {
            Log.error("安装游戏失败", e)
            withContext(Dispatchers.Main) { launchProgress = null; fileProgress = null; launchStatus = "安装失败: ${e.message}" }
        }
    }
}

// ================= Mod =================

/** 首次进入 Mod 页时加载 Modrinth 热门榜（前 50）。 */
fun AppState.loadTrendingMods() {
    if (modLoading || modResults.isNotEmpty()) return
    modLoading = true; modProgress = null; modStatus = "正在加载 Modrinth 热门榜…"
    scope.launch(Dispatchers.IO) {
        try {
            val r = modDownloader.trending(50)
            withContext(Dispatchers.Main) {
                modResults = r; modLoading = false; modStatus = "Modrinth 热门榜 Top ${r.size}"
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { modLoading = false; modStatus = "热门榜加载失败: ${e.message}" }
        }
    }
}

fun AppState.searchMods() {
    val q = modQuery.trim()
    if (q.isEmpty()) { modStatus = "请输入搜索关键词"; return }
    modLoading = true; modProgress = null; modStatus = "正在搜索…"
    scope.launch(Dispatchers.IO) {
        try {
            val r = modDownloader.search(q, 30)
            withContext(Dispatchers.Main) {
                modResults = r; modLoading = false; modStatus = "找到 ${r.size} 个结果"
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { modLoading = false; modStatus = "搜索失败: ${e.message}" }
        }
    }
}

fun AppState.downloadMod(mod: ModDownloader.Mod) {
    modProgress = 0f; modStatus = "正在下载 ${mod.title}…"
    val gv = selectedVersionId ?: ""
    val modsDir = effectiveGameDir().resolve("mods")
    scope.launch(Dispatchers.IO) {
        try {
            val p = modDownloader.download(mod, gv, modLoader.modrinthId(), modsDir,
                { onLog(it) }, { pct, stg -> onProgress(pct, stg) })
            withContext(Dispatchers.Main) {
                modProgress = null; modStatus = "已下载: ${p.fileName}"
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { modProgress = null; modStatus = "下载失败: ${e.message}" }
        }
    }
}

fun AppState.fetchLoaderVersions(loader: ModLoader, gameVersion: String, onDone: (List<String>) -> Unit) {
    scope.launch(Dispatchers.IO) {
        val list = try { loaderInstaller.listLoaderVersions(loader, gameVersion) } catch (e: Exception) {
            Log.warn("获取 ${loader.displayName} 版本失败: ${e.message}"); emptyList()
        }
        withContext(Dispatchers.Main) { onDone(list) }
    }
}

// ================= 新闻 =================

fun AppState.loadNews(force: Boolean = false) {
    val now = System.currentTimeMillis()
    if (!force && news.isNotEmpty() && now - lastNewsFetch < 10 * 60 * 1000L) return
    newsLoading = true; newsError = false
    MinecraftNewsService.fetchNews(10,
        { items ->
            scope.launch { news = items; newsLoading = false; newsError = false; lastNewsFetch = now }
        },
        { err ->
            scope.launch { newsLoading = false; newsError = news.isEmpty(); Log.warn("新闻获取失败: ${err.message}") }
        })
}

// ================= 皮肤 =================

fun AppState.uploadSkin(file: Path) {
    skinStatus = "正在上传皮肤…"
    scope.launch(Dispatchers.IO) {
        try {
            SkinManager.uploadSkin(username.trim(), file)
            withContext(Dispatchers.Main) { skinStatus = "皮肤已更新"; skinVersion++ }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { skinStatus = "上传失败: ${e.message}" }
        }
    }
}

fun AppState.removeSkin() {
    val ok = SkinManager.removeSkin(username.trim())
    skinStatus = if (ok) "已移除皮肤" else "没有可移除的皮肤"
    if (ok) skinVersion++
}

fun AppState.skinFilePath(): Path? = SkinManager.skinFile(username.trim())

/** ModLoader 对应的 Modrinth loader 字符串；不可用于 Mod 检索的加载器返回 null。 */
fun ModLoader.modrinthId(): String? = when (this) {
    ModLoader.FABRIC -> "fabric"
    ModLoader.QUILT -> "quilt"
    ModLoader.NEOFORGE -> "neoforge"
    ModLoader.FORGE -> "forge"
    else -> null
}
