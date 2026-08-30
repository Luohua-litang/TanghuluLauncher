package com.tanghulu.launcher.core.model

import com.tanghulu.launcher.util.Json

/**
 * 版本 JSON 中的库文件（library）条目。
 */
class Library(
    @JvmField val name: String?,
    /** 规则列表，null 表示无条件允许 */
    @JvmField val rules: List<Rule>?,
    /** 主文件（jar）下载信息 */
    @JvmField val artifact: DownloadInfo?,
    /** 分类下载：natives-windows / natives-osx / natives-linux 等 */
    @JvmField val classifiers: Map<String, DownloadInfo>,
    /** 由 name 推导出的默认 artifact 路径（当 downloads.artifact 缺失时使用） */
    @JvmField val inferredPath: String?
) {
    /** 判断此库在当前环境下是否被允许。 */
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

    /** 获取主文件相对于游戏目录的路径。 */
    fun artifactPath(): String? {
        if (artifact != null && artifact.path != null) return artifact.path
        return inferredPath
    }

    /** 获取主文件下载 URL（可能为 null）。 */
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
            // 旧格式：natives 字段 + downloads.classifiers
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
         * 由 "group:artifact:version[:classifier]" 形式的 name 推导出
         * "libraries/org/group/artifact/version/artifact-version.jar" 相对路径。
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
