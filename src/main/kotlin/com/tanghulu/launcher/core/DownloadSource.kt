package com.tanghulu.launcher.core

import java.util.LinkedHashSet

/**
 * Download source configuration. Official source + BMCLAPI mirror.
 */
class DownloadSource private constructor(
    private val name: String,
    private val manifestUrl: String,
    /** Version jar template; {sha1} is the object hash, {id} is the version id */
    private val versionJarTemplate: String,
    /** Asset object template; {h2} is the first two hash chars, {hash} is the full hash */
    private val assetObjectTemplate: String,
    private val rewriteLibrary: Boolean
) {
    fun getName(): String = name

    fun manifestUrl(): String = manifestUrl

    /** Version JSON download URL (from the manifest entry url); mirrors may rewrite it. */
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

    /** Library URL rewrite (mirror sources swap the official host for the mirror). */
    fun library(originalUrl: String?): String? {
        if (!rewriteLibrary || originalUrl == null) return originalUrl
        if (originalUrl.startsWith("https://libraries.minecraft.net/")) {
            return "https://bmclapi2.bangbang93.com/libraries/" +
                originalUrl.substring("https://libraries.minecraft.net/".length)
        }
        return originalUrl
    }

    // ---------- Candidate URLs (multi-source auto retry) ----------

    /** Library candidate URLs: prefer the current source, fall back to the other one on failure. */
    fun libraryCandidates(originalUrl: String?): List<String> =
        candidates({ library(originalUrl) }, { other().library(originalUrl) })

    /** Asset object candidate URLs. */
    fun assetObjectCandidates(hash: String?): List<String> =
        candidates({ assetObject(hash) }, { other().assetObject(hash) })

    /** Version jar candidate URLs. */
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
            // a single source URL generation failure must not affect other candidates
        }
    }

    /** The other source (different from this one), used for fallback. */
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
