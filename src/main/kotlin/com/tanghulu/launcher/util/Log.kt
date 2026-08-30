package com.tanghulu.launcher.util

import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Consumer

/**
 * Lightweight logger: unified `[HH:mm:ss] [level] message` format, written to a file asynchronously on a background thread,
 * while also forwarding each line to the UI ([setUiSink]).
 */
object Log {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private const val RETENTION_MILLIS = 7L * 24 * 3600 * 1000 // keep logs for 7 days

    private val QUEUE: BlockingQueue<String> = LinkedBlockingQueue()

    @Volatile private var writer: PrintWriter? = null
    @Volatile private var uiSink: Consumer<String>? = null
    @Volatile private var logFile: Path? = null
    @Volatile private var shutdown = false

    @JvmStatic
    fun init(logDir: Path) {
        try {
            Files.createDirectories(logDir)
            cleanupOldLogs(logDir)
            logFile = logDir.resolve("tanghulu-" + LocalDateTime.now().format(FILE) + ".log")
            writer = PrintWriter(
                Files.newBufferedWriter(
                    logFile!!, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
                ),
                true
            )
        } catch (e: IOException) {
            // fall back to UI-only forwarding if writing to file fails
            writer = null
        }

        val worker = Thread({
            while (!shutdown) {
                try {
                    val line = QUEUE.take()
                    val w = writer
                    if (w != null) w.println(line)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "log-writer")
        worker.isDaemon = true
        worker.start()

        Runtime.getRuntime().addShutdownHook(Thread({
            shutdown = true
            writer?.close()
        }, "log-shutdown"))
    }

    /** Register a UI output callback; every log line is forwarded to it (remember to switch to the JavaFX thread). */
    @JvmStatic
    fun setUiSink(sink: Consumer<String>) {
        uiSink = sink
    }

    @JvmStatic
    fun info(msg: String) = emit("INFO", msg)

    @JvmStatic
    fun warn(msg: String) = emit("WARN", msg)

    @JvmStatic
    fun error(msg: String) = emit("ERROR", msg)

    @JvmStatic
    fun error(msg: String, t: Throwable?) {
        emit("ERROR", msg)
        if (t != null) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            emit("ERROR", sw.toString())
        }
    }

    @JvmStatic
    fun logFile(): Path? = logFile

    private fun emit(level: String, msg: String) {
        val line = "[" + TIME.format(LocalDateTime.now()) + "] [" + level + "] " + msg
        QUEUE.offer(line)
        uiSink?.accept(line)
    }

    private fun cleanupOldLogs(logDir: Path) {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        try {
            Files.list(logDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().startsWith("tanghulu-") }
                    .filter { p ->
                        try {
                            Files.getLastModifiedTime(p).toMillis() < cutoff
                        } catch (e: IOException) {
                            false
                        }
                    }
                    .forEach { p ->
                        try {
                            Files.deleteIfExists(p)
                        } catch (ignored: IOException) {
                            // a single failed deletion must not abort the rest
                        }
                    }
            }
        } catch (ignored: IOException) {
            // cleanup failures are ignorable
        }
    }
}
