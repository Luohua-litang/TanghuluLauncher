package com.tanghulu.launcher.core

import com.tanghulu.launcher.util.FileUtil
import com.tanghulu.launcher.util.HttpUtil
import com.tanghulu.launcher.util.Json
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 资源（assets）管理：下载资源索引与资源对象文件。
 */
class AssetManager {

    /** 资源索引本地文件。 */
    fun assetIndexFile(gameDir: Path, id: String): Path =
        gameDir.resolve("assets").resolve("indexes").resolve("$id.json")

    /** 确保资源索引存在并返回解析后的对象。 */
    @Throws(IOException::class)
    fun loadOrDownloadIndex(gameDir: Path, id: String, indexUrl: String?): Map<String, Any?> {
        val indexFile = assetIndexFile(gameDir, id)
        if (Files.isRegularFile(indexFile)) {
            val json = Json.parse(Files.readString(indexFile, StandardCharsets.UTF_8))
            val root = Json.asObject(json)
            if (root != null) return root
        }
        if (indexUrl == null) throw IOException("No asset index URL for $id")
        val text = HttpUtil.getText(indexUrl)
        Files.createDirectories(indexFile.parent)
        Files.writeString(indexFile, text, StandardCharsets.UTF_8)
        return Json.asObject(Json.parse(text)) ?: throw IOException("Invalid asset index for $id")
    }

    /** 列出资源索引中的所有对象哈希。 */
    fun listObjectHashes(index: Map<String, Any?>): List<String> {
        val objects = Json.asObject(index["objects"]) ?: return emptyList()
        val hashes = ArrayList<String>(objects.size)
        for (o in objects.values) {
            val entry = Json.asObject(o)
            if (entry != null) {
                val hash = Json.optString(entry, "hash")
                if (hash != null) hashes.add(hash)
            }
        }
        return hashes
    }

    /** 构建缺失资源对象的下载任务。 */
    fun buildObjectTasks(gameDir: Path, index: Map<String, Any?>, source: DownloadSource): List<DownloadTask> {
        val objects = Json.asObject(index["objects"]) ?: return emptyList()
        val tasks = ArrayList<DownloadTask>()
        for ((key, value) in objects) {
            val entry = Json.asObject(value) ?: continue
            val hash = Json.optString(entry, "hash") ?: continue
            val size = Json.optLong(entry, "size", 0)
            val target = gameDir.resolve("assets").resolve("objects")
                .resolve(hash.substring(0, 2)).resolve(hash)
            // 只下载缺失或损坏的文件
            if (Files.isRegularFile(target) && hash.equals(FileUtil.sha1(target), true)) continue
            tasks.add(DownloadTask(source.assetObjectCandidates(hash), target, hash, size, key))
        }
        return tasks
    }
}
