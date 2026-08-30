package com.tanghulu.launcher.core

import com.tanghulu.launcher.util.FileUtil
import com.tanghulu.launcher.util.HttpUtil
import com.tanghulu.launcher.util.Json
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

/**
 * Mod downloader: search and download mods via the Modrinth API.
 */
class ModDownloader {
    companion object {
        const val API_BASE = "https://api.modrinth.com/v2"
    }

    fun interface Progress {
        fun update(percent: Int, stage: String)
    }

    class ModVersion(
        @JvmField val id: String?,
        @JvmField val versionNumber: String?,
        @JvmField val gameVersions: List<String>,
        @JvmField val loaders: List<String>,
        @JvmField val fileName: String?,
        @JvmField val url: String,
        @JvmField val sha1: String?
    )

    @Throws(IOException::class)
    fun search(query: String, limit: Int): List<ModItem> {
        val url = API_BASE + "/search?query=" + enc(query) +
            "&facets=" + enc("[[\"project_type:mod\"]]") +
            "&limit=" + max(1, min(limit, 100))
        return parseHits(HttpUtil.getText(url))
    }

    /** Trending list: mods sorted by download count. */
    @Throws(IOException::class)
    fun trending(limit: Int): List<ModItem> {
        val url = API_BASE + "/search?index=downloads" +
            "&facets=" + enc("[[\"project_type:mod\"]]") +
            "&limit=" + max(1, min(limit, 100))
        return parseHits(HttpUtil.getText(url))
    }

    private fun parseHits(text: String): List<ModItem> {
        val root = Json.asObject(Json.parse(text))
        val result = ArrayList<ModItem>()
        if (root == null) return result
        for (o in Json.asArray(root["hits"]) ?: emptyList()) {
            val h = Json.asObject(o) ?: continue
            val slug = Json.optString(h, "slug")
            if (slug.isNullOrEmpty()) continue
            result.add(
                ModItem(
                    slug,
                    Json.optString(h, "title", slug) ?: slug,
                    Json.optString(h, "description"),
                    Json.optString(h, "icon_url"),
                    Json.optLong(h, "downloads", 0)
                )
            )
        }
        return result
    }

    @Throws(IOException::class)
    fun listVersions(slug: String): List<ModVersion> {
        val url = API_BASE + "/project/" + enc(slug) + "/version"
        val result = ArrayList<ModVersion>()
        for (o in Json.asArray(Json.parse(HttpUtil.getText(url))) ?: emptyList()) {
            val v = Json.asObject(o) ?: continue
            val file = pickFile(Json.asArray(v["files"])) ?: continue
            val furl = Json.optString(file, "url")
            if (furl.isNullOrEmpty()) continue
            val hashes = Json.asObject(file["hashes"])
            result.add(
                ModVersion(
                    Json.optString(v, "id"),
                    Json.optString(v, "version_number"),
                    stringList(v["game_versions"]),
                    stringList(v["loaders"]),
                    Json.optString(file, "filename"),
                    furl,
                    if (hashes == null) null else Json.optString(hashes, "sha1")
                )
            )
        }
        return result
    }

    @Throws(IOException::class)
    fun download(
        mod: ModItem, gameVersion: String, loader: String?, modsDir: Path,
        log: Consumer<String>, progress: Progress?
    ): Path {
        val ver = pickVersion(mod.id, gameVersion, loader)
            ?: throw IOException(
                "未找到匹配版本: " + mod.title + "（游戏 " + gameVersion +
                    (if (loader.isNullOrEmpty()) "" else " / " + loader) + "）"
            )
        val name = if (!ver.fileName.isNullOrEmpty()) ver.fileName
        else mod.id + "-" + ver.versionNumber + ".jar"
        val target = modsDir.resolve(name)
        if (Files.isRegularFile(target) && !ver.sha1.isNullOrEmpty()
            && ver.sha1.equals(FileUtil.sha1(target), true)
        ) {
            log.accept("Mod 已存在，跳过: $name")
            progress?.update(100, "已存在")
            return target
        }
        log.accept("下载 " + mod.title + " " + ver.versionNumber + " -> " + name)
        progress?.update(10, "下载 $name")
        HttpUtil.downloadIfNeeded(ver.url, target, ver.sha1) { downloaded, total ->
            val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
            progress?.update(max(10, min(99, pct)), "下载 $name")
        }
        progress?.update(100, "完成")
        log.accept("Mod 下载完成: $name")
        return target
    }

    @Throws(IOException::class)
    fun pickVersion(slug: String, gameVersion: String, loader: String?): ModVersion? {
        val versions = listVersions(slug)
        if (versions.isEmpty()) return null
        val filterLoader = !loader.isNullOrEmpty()
        for (v in versions) {
            if (v.gameVersions.contains(gameVersion) && (!filterLoader || v.loaders.contains(loader))) {
                return v
            }
        }
        for (v in versions) {
            if (v.gameVersions.contains(gameVersion)) return v
        }
        if (filterLoader) {
            for (v in versions) {
                if (v.loaders.contains(loader)) return v
            }
        }
        return versions[0]
    }

    private fun pickFile(files: List<Any?>?): Map<String, Any?>? {
        if (files == null) return null
        var first: Map<String, Any?>? = null
        for (o in files) {
            val f = Json.asObject(o) ?: continue
            if (first == null) first = f
            if (f["primary"] == true) return f
        }
        return first
    }

    private fun stringList(v: Any?): List<String> {
        val out = ArrayList<String>()
        for (o in Json.asArray(v) ?: emptyList()) {
            val s = Json.asString(o)
            if (s != null) out.add(s)
        }
        return out
    }

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)
}
