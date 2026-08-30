package com.tanghulu.launcher.core

import com.tanghulu.launcher.util.HttpUtil
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * 并发下载管理器：多线程并行下载任务，实时汇报进度。
 */
class DownloadManager(threads: Int) {
    fun interface ProgressListener {
        /**
         * @param done 已完成文件数
         * @param total 总文件数
         * @param current 当前文件名（完成/失败时为 null 或标签）
         * @param fileDone 当前文件已下载字节
         * @param fileTotal 当前文件总字节（-1 表示未知）
         */
        fun onProgress(done: Int, total: Int, current: String?, fileDone: Long, fileTotal: Long)
    }

    private val threads: Int = max(1, threads)

    constructor() : this(8)

    /**
     * 并行下载所有任务。
     *
     * @return 实际下载的文件数（跳过已存在且校验通过的不计）
     */
    @Throws(IOException::class)
    fun downloadAll(tasks: List<DownloadTask>, listener: ProgressListener?): Int {
        if (tasks.isEmpty()) {
            listener?.onProgress(0, 0, "", 0, 0)
            return 0
        }
        val done = AtomicInteger()
        val pool: ExecutorService = Executors.newFixedThreadPool(threads) { r ->
            Thread(r, "tanghulu-download").apply { isDaemon = true }
        }
        val futures = ArrayList<Future<Int>>(tasks.size)
        for (task in tasks) {
            futures.add(pool.submit<Int> {
                try {
                    val downloaded = HttpUtil.downloadIfNeeded(task.urls, task.target, task.sha1) { fd, ft ->
                        listener?.onProgress(done.get(), tasks.size, task.label, fd, ft)
                    }
                    listener?.onProgress(done.incrementAndGet(), tasks.size, task.label, 0, 0)
                    if (downloaded) 1 else 0
                } catch (e: IOException) {
                    listener?.onProgress(done.incrementAndGet(), tasks.size, task.label, 0, 0)
                    throw e
                }
            })
        }
        pool.shutdown()
        var downloaded = 0
        var failure: IOException? = null
        for (f in futures) {
            try {
                downloaded += f.get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                pool.shutdownNow()
                throw IOException("Download interrupted", e)
            } catch (e: ExecutionException) {
                if (failure == null) {
                    val cause = e.cause
                    failure = if (cause is IOException) cause else IOException("Download failed", cause)
                }
            }
        }
        if (failure != null) throw failure
        return downloaded
    }
}
