package com.tanghulu.launcher.core.model

import com.tanghulu.launcher.util.Json

/**
 * 解析后的版本信息（来自 versions/&lt;id&gt;/&lt;id&gt;.json）。
 */
class VersionInfo(
    @JvmField val id: String?,
    @JvmField val mainClass: String?,
    @JvmField val type: String?,
    @JvmField val assets: String?,
    @JvmField val assetIndex: DownloadInfo?,
    @JvmField val clientDownload: DownloadInfo?,
    @JvmField val libraries: List<Library>,
    @JvmField val gameArguments: List<Any?>,
    @JvmField val jvmArguments: List<Any?>,
    @JvmField val minecraftArguments: String?,
    @JvmField val javaMajorVersion: Int,
    @JvmField val minimumLauncherVersion: Int
) {
    companion object {
        @JvmStatic
        fun parse(json: Any?): VersionInfo {
            val root = Json.asObject(json) ?: throw IllegalArgumentException("Invalid version JSON")

            val libs = mutableListOf<Library>()
            val libList = Json.asArray(Json.opt(root, "libraries"))
            if (libList != null) {
                for (o in libList) {
                    val lm = Json.asObject(o)
                    if (lm != null) {
                        val lib = Library.fromJson(lm)
                        if (lib != null) libs.add(lib)
                    }
                }
            }

            val gameArgs = mutableListOf<Any?>()
            val jvmArgs = mutableListOf<Any?>()
            var minecraftArguments: String? = null
            val arguments = Json.asObject(Json.opt(root, "arguments"))
            if (arguments != null) {
                val g = Json.asArray(arguments["game"])
                val j = Json.asArray(arguments["jvm"])
                if (g != null) gameArgs.addAll(g)
                if (j != null) jvmArgs.addAll(j)
            } else {
                minecraftArguments = Json.optString(root, "minecraftArguments")
            }

            var javaMajor = 8
            val javaVersion = Json.asObject(Json.opt(root, "javaVersion"))
            if (javaVersion != null) {
                javaMajor = Json.optInt(javaVersion, "majorVersion", 8)
            }

            val downloads = Json.asObject(Json.opt(root, "downloads"))
            val client = if (downloads == null) null
            else DownloadInfo.fromJson(Json.asObject(Json.opt(downloads, "client")))

            return VersionInfo(
                Json.optString(root, "id"),
                Json.optString(root, "mainClass", "net.minecraft.client.main.Main"),
                Json.optString(root, "type", "release"),
                Json.optString(root, "assets", Json.optString(root, "id")),
                DownloadInfo.fromJson(Json.asObject(Json.opt(root, "assetIndex"))),
                client,
                libs,
                gameArgs,
                jvmArgs,
                minecraftArguments,
                javaMajor,
                Json.optInt(root, "minimumLauncherVersion", 0)
            )
        }
    }
}
