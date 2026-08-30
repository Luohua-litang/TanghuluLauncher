package com.tanghulu.launcher.core.model

import com.tanghulu.launcher.util.Json

/**
 * A library entry in the version JSON.
 */
class Library(
    @JvmField val name: String?,
    /** Rule list; null means allowed unconditionally */
    @JvmField val rules: List<Rule>?,
    /** Primary artifact (jar) download info */
    @JvmField val artifact: DownloadInfo?,
    /** Classifier downloads: natives-windows / natives-osx / natives-linux etc. */
    @JvmField val classifiers: Map<String, DownloadInfo>,
    /** Default artifact path derived from name (used when downloads.artifact is missing) */
    @JvmField val inferredPath: String?
) {
    /** Whether this library is allowed in the current environment. */
    fun isAllowed(osName: String, hasCustomResolution: Boolean, isDemoUser: Boolean): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (r in rules) {
            if (r.matches(osName, hasCustomResolution, isDemoUser)) {
                allowed = r.allows()
            }
        }
        return allowed
    }

    /** Path of the primary artifact relative to the game directory. */
    fun artifactPath(): String? {
        if (artifact != null && artifact.path != null) return artifact.path
        return inferredPath
    }

    /** Download URL of the primary artifact (may be null). */
    fun artifactUrl(): String? = artifact?.url

    fun artifactSha1(): String? = artifact?.sha1

    companion object {
        @JvmStatic
        fun fromJson(json: Map<String, Any?>?): Library? {
            val name = Json.optString(json, "name") ?: return null

            var rules: MutableList<Rule>? = null
            val ruleList = Json.asArray(Json.opt(json, "rules"))
            if (ruleList != null && ruleList.isNotEmpty()) {
                rules = mutableListOf()
                for (r in ruleList) {
                    val rm = Json.asObject(r)
                    if (rm != null) rules.add(Rule.fromJson(rm))
                }
            }

            val downloads = Json.asObject(Json.opt(json, "downloads"))
            var artifact: DownloadInfo? = null
            val classifiers = HashMap<String, DownloadInfo>()
            if (downloads != null) {
                artifact = DownloadInfo.fromJson(Json.asObject(Json.opt(downloads, "artifact")))
                val cls = Json.asObject(Json.opt(downloads, "classifiers"))
                if (cls != null) {
                    for ((k, v) in cls) {
                        val di = DownloadInfo.fromJson(Json.asObject(v))
                        if (di != null) classifiers[k] = di
                    }
                }
            }
            // Legacy format: natives field + downloads.classifiers
            if (classifiers.isEmpty()) {
                val natives = Json.asObject(Json.opt(json, "natives"))
                if (natives != null && downloads != null) {
                    val cls = Json.asObject(Json.opt(downloads, "classifiers"))
                    if (cls != null) {
                        for ((k, v) in cls) {
                            val di = DownloadInfo.fromJson(Json.asObject(v))
                            if (di != null) classifiers[k] = di
                        }
                    }
                }
            }

            val inferred = inferPath(name)
            var art = artifact
            if (art != null && art.path == null) {
                art = DownloadInfo(inferred, art.url, art.sha1, art.size)
            }
            return Library(name, rules, art, classifiers, inferred)
        }

        /**
         * Derive the relative path "libraries/org/group/artifact/version/artifact-version.jar"
         * from a name of the form "group:artifact:version[:classifier]".
         */
        @JvmStatic
        fun inferPath(name: String): String? {
            val parts = name.split(":")
            if (parts.size < 3) return null
            val group = parts[0]
            val artifact = parts[1]
            val version = parts[2]
            val classifier = if (parts.size > 3) parts[3] else null

            var fileName = "$artifact-$version"
            if (classifier != null) fileName += "-$classifier"
            fileName += ".jar"

            val path = group.replace('.', '/') + "/" + artifact + "/" + version + "/" + fileName
            return "libraries/$path"
        }
    }
}
