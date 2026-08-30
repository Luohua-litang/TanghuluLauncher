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
 * Concurrent download manager: downloads tasks in parallel with multiple threads and reports progress in real time.
 */
class DownloadManager(threads: Int) {
    fun interface ProgressListener {
        /**
         * @param done number of completed files
         * @param total total number of files
         * @param current current file name (null or the label when done/failed)
         * @param fileDone bytes downloaded for the current file
         * @param fileTotal total bytes of the current file (-1 means unknown)
         */
        fun onProgress(done: Int, total: Int, current: String?, fileDone: Long, fileTotal: Long)
    }

    private val threads: Int = max(1, threads)

    constructor() : this(8)

    /**
     * Download all tasks in parallel.
     *
     * @return number of files actually downloaded (skipping files that already exist and pass verification)
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
