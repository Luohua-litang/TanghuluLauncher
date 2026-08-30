package com.tanghulu.launcher.core

import java.nio.file.Path
import java.util.Collections

/**
 * 单个下载任务。
 * 支持多个候选地址：下载失败时按顺序尝试下一个源。
 */
class DownloadTask(
    urls: List<String>?,
    @JvmField val target: Path,
    @JvmField val sha1: String?,
    @JvmField val size: Long,
    @JvmField val label: String?
) {
    /** 候选下载地址，按优先顺序排列。 */
    @JvmField
    val urls: List<String> = if (urls.isNullOrEmpty()) emptyList()
        else Collections.unmodifiableList(ArrayList(urls))

    constructor(url: String, target: Path, sha1: String?, size: Long, label: String?) :
        this(listOf(url), target, sha1, size, label)

    /** 首选地址。 */
    fun primaryUrl(): String? = if (urls.isEmpty()) null else urls[0]

    override fun toString(): String = label ?: target.fileName.toString()
}
