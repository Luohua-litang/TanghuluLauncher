package com.tanghulu.launcher.core.model

import com.tanghulu.launcher.util.Json

/** 单个文件的下载信息。 */
class DownloadInfo(
    @JvmField val path: String?,
    @JvmField val url: String?,
    @JvmField val sha1: String?,
    @JvmField val size: Long
) {
    companion object {
        @JvmStatic
        fun fromJson(json: Map<String, Any?>?): DownloadInfo? {
            if (json == null) return null
            return DownloadInfo(
                Json.optString(json, "path"),
                Json.optString(json, "url"),
                Json.optString(json, "sha1"),
                Json.optLong(json, "size", 0)
            )
        }
    }
}
