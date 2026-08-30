package com.tanghulu.launcher.core

import java.util.LinkedHashSet

/**
 * 下载源配置。官方源 + BMCLAPI 国内镜像。
 */
class DownloadSource private constructor(
    private val name: String,
    private val manifestUrl: String,
    /** 版本 jar 模板，{sha1} 为对象哈希，{id} 为版本 id */
    private val versionJarTemplate: String,
    /** 资源对象模板，{h2} 为哈希前两位，{hash} 为完整哈希 */
    private val assetObjectTemplate: String,
    private val rewriteLibrary: Boolean
) {
    fun getName(): String = name

    fun manifestUrl(): String = manifestUrl

    /** 版本 JSON 下载地址（来自 manifest 条目 url），镜像可重写。 */
    fun versionJson(originalUrl: String?): String? {
        if (rewriteLibrary && originalUrl != null && originalUrl.startsWith("https://launchermeta.mojang.com/")) {
            return "https://bmclapi2.bangbang93.com/version/" + versionIdFromManifestUrl(originalUrl) + "/json"
        }
        return originalUrl
    }

    fun versionJar(id: String, sha1: String?): String =
        versionJarTemplate.replace("{id}", id).replace("{sha1}", sha1 ?: "")

    fun assetIndex(originalUrl: String?, id: String?): String? {
        if (rewriteLibrary && id != null) {
            return "https://bmclapi2.bangbang93.com/indexes/$id/json"
        }
        return originalUrl
    }

    fun assetObject(hash: String?): String {
        if (hash == null || hash.length < 2) {
            return assetObjectTemplate.replace("{h2}", "??").replace("{hash}", hash ?: "")
        }
        return assetObjectTemplate.replace("{h2}", hash.substring(0, 2)).replace("{hash}", hash)
    }

    /** 库文件 URL 重写（镜像源把官方 host 换成镜像）。 */
    fun library(originalUrl: String?): String? {
        if (!rewriteLibrary || originalUrl == null) return originalUrl
        if (originalUrl.startsWith("https://libraries.minecraft.net/")) {
            return "https://bmclapi2.bangbang93.com/libraries/" +
                originalUrl.substring("https://libraries.minecraft.net/".length)
        }
        return originalUrl
    }

    // ---------- 候选 URL（多源自动重试） ----------

    /** 库文件候选地址：首选当前源，失败时自动尝试另一个源。 */
    fun libraryCandidates(originalUrl: String?): List<String> =
        candidates({ library(originalUrl) }, { other().library(originalUrl) })

    /** 资源对象候选地址。 */
    fun assetObjectCandidates(hash: String?): List<String> =
        candidates({ assetObject(hash) }, { other().assetObject(hash) })

    /** 版本 jar 候选地址。 */
    fun versionJarCandidates(id: String, sha1: String?): List<String> =
        candidates({ versionJar(id, sha1) }, { other().versionJar(id, sha1) })

    private fun candidates(primary: () -> String?, fallback: () -> String?): List<String> {
        val set = LinkedHashSet<String>()
        addUrl(set, primary)
        addUrl(set, fallback)
        return ArrayList(set)
    }

    private fun addUrl(set: LinkedHashSet<String>, supplier: () -> String?) {
        try {
            val url = supplier()
            if (!url.isNullOrEmpty()) set.add(url)
        } catch (ignored: Exception) {
            // 单个源 URL 生成失败不影响其他候选
        }
    }

    /** 与当前源不同的另一个源，用于降级。 */
    private fun other(): DownloadSource {
        for (s in all()) {
            if (s !== this) return s
        }
        return this
    }

    companion object {
        @JvmField
        val OFFICIAL = DownloadSource(
            "官方源",
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json",
            "https://launcher.mojang.com/v1/objects/{sha1}/{id}.jar",
            "https://resources.download.minecraft.net/{h2}/{hash}",
            false
        )

        @JvmField
        val BMCLAPI = DownloadSource(
            "BMCLAPI 镜像",
            "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json",
            "https://bmclapi2.bangbang93.com/version/{id}/jar",
            "https://bmclapi2.bangbang93.com/assets/{hash}",
            true
        )

        private fun versionIdFromManifestUrl(url: String): String {
            // launchermeta.mojang.com/v1/packages/<hash>/<id>.json
            val seg = url.split("/")
            val last = seg[seg.size - 1]
            return if (last.endsWith(".json")) last.substring(0, last.length - 5) else last
        }

        @JvmStatic
        fun all(): Array<DownloadSource> = arrayOf(OFFICIAL, BMCLAPI)
    }
}
