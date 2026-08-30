package com.tanghulu.launcher.core

import com.tanghulu.launcher.util.HttpUtil
import com.tanghulu.launcher.util.Json
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

/**
 * 版本清单管理：拉取 Mojang 版本清单、扫描本地已安装版本。
 */
class VersionManager {

    /** 拉取版本清单（Map 结构），失败抛出 IOException。 */
    @Throws(IOException::class)
    fun fetchManifest(source: DownloadSource): Map<String, Any?> {
        val text = HttpUtil.getText(source.manifestUrl())
        return Json.asObject(Json.parse(text)) ?: throw IOException("Invalid version manifest")
    }

    /** 从清单中找出某个版本条目的 URL。 */
    fun findVersionUrl(manifest: Map<String, Any?>, id: String): String? {
        val versions = Json.asArray(manifest["versions"])
        if (versions != null) {
            for (v in versions) {
                val entry = Json.asObject(v)
                if (entry != null && id == Json.optString(entry, "id")) {
                    return Json.optString(entry, "url")
                }
            }
        }
        return null
    }

    /** 扫描游戏目录下已安装的版本 id。 */
    fun getLocalVersions(gameDir: Path): List<String> {
        val versionsDir = gameDir.resolve("versions")
        if (!Files.isDirectory(versionsDir)) return emptyList()
        val result = ArrayList<String>()
        try {
            Files.list(versionsDir).use { dirs ->
                dirs.filter { Files.isDirectory(it) }.forEach { d ->
                    val id = d.fileName.toString()
                    if (Files.isRegularFile(d.resolve("$id.json"))) result.add(id)
                }
            }
        } catch (e: IOException) {
            // 忽略读取失败
        }
        result.sort()
        return result
    }

    /**
     * 删除本地已安装的某个版本（移除 {@code versions/<id>} 整个目录）。
     * 做了路径校验，避免目录穿越；目录不存在时返回 false。
     */
    fun deleteVersion(gameDir: Path, id: String): Boolean {
        if (id.isBlank() || id.contains("..") || id.contains('/') || id.contains('\\')) return false
        val base = gameDir.resolve("versions").normalize()
        val dir = base.resolve(id).normalize()
        if (!dir.startsWith(base) || !Files.isDirectory(dir)) return false
        return try {
            Files.walk(dir).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    /** 版本 JSON 的本地文件。 */
    fun localVersionJson(gameDir: Path, id: String): Path =
        gameDir.resolve("versions").resolve(id).resolve("$id.json")

    /** 版本 jar 的本地文件。 */
    fun localVersionJar(gameDir: Path, id: String): Path =
        gameDir.resolve("versions").resolve(id).resolve("$id.jar")

    /**
     * 解析版本 JSON：优先读取本地文件，否则从网络拉取并保存到本地。
     * 返回解析后的根对象（Map）。
     */
    @Throws(IOException::class)
    fun loadOrDownloadVersionJson(
        gameDir: Path, id: String, source: DownloadSource, manifestVersionUrl: String?
    ): Map<String, Any?> {
        val local = localVersionJson(gameDir, id)
        if (Files.isRegularFile(local)) {
            val root = Json.asObject(Json.parse(Files.readString(local, StandardCharsets.UTF_8)))
            if (root != null) return root
        }
        // 下载：未提供 URL 时先从版本清单查找
        var url = manifestVersionUrl
        if (url.isNullOrEmpty()) {
            url = findVersionUrl(fetchManifest(source), id)
        }
        url = source.versionJson(url)
        if (url.isNullOrEmpty()) throw IOException("No download URL for version $id")
        val text = HttpUtil.getText(url)
        val root = Json.asObject(Json.parse(text))
            ?: throw IOException("Invalid version JSON for $id")
        Files.createDirectories(local.parent)
        Files.writeString(local, text, StandardCharsets.UTF_8)
        return root
    }

    /** 合并继承链后的版本信息。 */
    class ResolvedVersion(
        /** 合并后的完整版本 JSON。 */
        @JvmField val json: Map<String, Any?>,
        /** 实际提供 client jar 的版本 id（可能是继承链上的父版本）。 */
        @JvmField val jarId: String
    )

    /**
     * 解析版本 JSON 并合并 {@code inheritsFrom} 继承链（参考 HMCL 的 GameInstancePatch）。
     * Mod 加载器（Fabric/Forge 等）生成的版本 JSON 通过 {@code inheritsFrom} 挂在原版上，
     * 这里把父版本的 libraries / arguments / 下载信息递归合并进来。
     *
     * @return 合并后的完整 JSON + 实际提供 client jar 的版本 id
     */
    @Throws(IOException::class)
    fun resolve(gameDir: Path, id: String, source: DownloadSource): ResolvedVersion {
        val json = loadOrDownloadVersionJson(gameDir, id, source, null)
        return resolveFrom(gameDir, id, json, source)
    }

    @Throws(IOException::class)
    private fun resolveFrom(
        gameDir: Path, id: String, json: Map<String, Any?>, source: DownloadSource
    ): ResolvedVersion {
        val inheritsFrom = Json.optString(json, "inheritsFrom")
        if (inheritsFrom.isNullOrEmpty()) return ResolvedVersion(json, id)
        // 子版本是否自带 client jar（决定 client jar 取自己还是父版本）
        val downloads = Json.asObject(Json.opt(json, "downloads"))
        val hasClient = downloads != null && Json.asObject(downloads["client"]) != null
        val parent = resolve(gameDir, inheritsFrom, source)
        return ResolvedVersion(mergeVersion(json, parent.json), if (hasClient) id else parent.jarId)
    }

    /** 合并子/父版本 JSON：子字段优先，libraries 按 name 去重（子覆盖父）。 */
    private fun mergeVersion(child: Map<String, Any?>, parent: Map<String, Any?>): Map<String, Any?> {
        val merged = LinkedHashMap<String, Any?>(parent)
        for ((k, v) in child) {
            if (k == "libraries") {
                merged["libraries"] = mergeLibraries(child, parent)
            } else {
                merged[k] = v
            }
        }
        return merged
    }

    private fun mergeLibraries(child: Map<String, Any?>, parent: Map<String, Any?>): List<Any?> {
        val byName = LinkedHashMap<String, Any?>()
        appendLibraries(byName, Json.asArray(parent["libraries"]))
        appendLibraries(byName, Json.asArray(child["libraries"]))
        return ArrayList(byName.values)
    }

    private fun appendLibraries(byName: LinkedHashMap<String, Any?>, libraries: List<Any?>?) {
        if (libraries == null) return
        for (o in libraries) {
            val lib = Json.asObject(o)
            if (lib != null) {
                val name = Json.optString(lib, "name")
                if (name != null) byName[name] = lib // 后写入覆盖，即子版本优先
            }
        }
    }

    /** 获取版本清单的展示数据：id + 类型（release/snapshot/old_*）。 */
    fun listManifestEntries(manifest: Map<String, Any?>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val versions = Json.asArray(manifest["versions"])
        if (versions != null) {
            for (v in versions) {
                val entry = Json.asObject(v)
                if (entry != null) {
                    val id = Json.optString(entry, "id")
                    val type = Json.optString(entry, "type", "release") ?: "release"
                    if (id != null) result.putIfAbsent(id, type)
                }
            }
        }
        return result
    }

    /** 版本清单的一个条目，用于列表展示。 */
    class ManifestEntry(
        @JvmField val id: String,
        @JvmField val type: String,
        /** 原始发布时间（ISO-8601，可能为 null）。 */
        @JvmField val releaseTime: String?
    )

    /** 获取版本清单的展示数据（id + 类型 + 发布时间），保持清单中的原始顺序。 */
    fun listManifestVersions(manifest: Map<String, Any?>): List<ManifestEntry> {
        val result = ArrayList<ManifestEntry>()
        val versions = Json.asArray(manifest["versions"])
        if (versions != null) {
            for (v in versions) {
                val entry = Json.asObject(v) ?: continue
                val id = Json.optString(entry, "id")
                if (id != null) {
                    result.add(
                        ManifestEntry(
                            id,
                            Json.optString(entry, "type", "release") ?: "release",
                            Json.optString(entry, "releaseTime")
                        )
                    )
                }
            }
        }
        return result
    }
}
