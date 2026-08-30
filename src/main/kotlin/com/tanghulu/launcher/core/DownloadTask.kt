package com.tanghulu.launcher.core

import java.nio.file.Path
import java.util.Collections

/**
 * A single download task.
 * Supports multiple candidate URLs: tries the next source in order when a download fails.
 */
class DownloadTask(
    urls: List<String>?,
    @JvmField val target: Path,
    @JvmField val sha1: String?,
    @JvmField val size: Long,
    @JvmField val label: String?
) {
    /** Candidate download URLs, ordered by priority. */
    @JvmField
    val urls: List<String> = if (urls.isNullOrEmpty()) emptyList()
        else Collections.unmodifiableList(ArrayList(urls))

    constructor(url: String, target: Path, sha1: String?, size: Long, label: String?) :
        this(listOf(url), target, sha1, size, label)

    /** The preferred URL. */
    fun primaryUrl(): String? = if (urls.isEmpty()) null else urls[0]

    override fun toString(): String = label ?: target.fileName.toString()
}
