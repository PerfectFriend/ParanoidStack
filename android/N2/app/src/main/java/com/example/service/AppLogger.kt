/**
 * File-based rotating logger for the application.
 *
 * Writes timestamped log entries to rotating files in the app_logs
 * directory. Supports DEBUG, INFO, WARN, ERROR levels with automatic
 * rotation at 512 KB per file, keeping up to [MAX_LOG_FILES] historical
 * files. Thread-safe via synchronized write methods.
 */
package com.example.service

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Singleton logger that persists logs to the local filesystem.
 *
 * Mirrors Android Logcat but also writes to rotating files on disk.
 * Each log line includes a timestamp, log level, padded tag, and message.
 * Provides thread-safe writing via @Synchronized.
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_DIR = "app_logs"
    private const val MAX_LOG_FILES = 5
    private const val MAX_FILE_SIZE = 512_000
    /** Pads tags to this width for aligned log output */
    private const val MAX_TAG_WIDTH = 20

    private var logDir: File? = null
    private var currentFile: File? = null
    private var currentSize: Long = 0
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun init(context: Context) {
        logDir = File(context.filesDir, LOG_DIR)
        logDir?.mkdirs()
        rotateLogFile()
        Log.i(TAG, "AppLogger initialized: ${logDir?.absolutePath}")

        i(TAG, "App start | Device: ${Build.MODEL} SDK: ${Build.VERSION.SDK_INT} ${Build.VERSION.RELEASE}")
        i(TAG, "App start | Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
    }

    fun d(tag: String, msg: String) = write('D', tag, msg)
    fun i(tag: String, msg: String) = write('I', tag, msg)
    fun w(tag: String, msg: String) = write('W', tag, msg)
    fun e(tag: String, msg: String) = write('E', tag, msg)
    fun e(tag: String, msg: String, tr: Throwable) {
        write('E', tag, "$msg | ${tr::class.simpleName}: ${tr.message}")
        write('E', tag, Log.getStackTraceString(tr).take(2000))
    }

    fun getLogFiles(): List<File> = logDir?.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()

    fun getLogDir(): String = logDir?.absolutePath ?: ""

    /** Formats a log line with timestamp, level, padded tag, and message */
    private fun write(level: Char, tag: String, msg: String) {
        val line = buildString {
            append(dateFmt.format(Date()))
            append(" $level/${tag.padEnd(MAX_TAG_WIDTH).take(MAX_TAG_WIDTH)}")
            append(" $msg")
            append('\n')
        }
        writeToFile(line)
    }

    /** Appends a single line to the current log file; triggers rotation if size exceeded */
    @Synchronized
    private fun writeToFile(line: String) {
        try {
            val file = currentFile
            if (file == null) {
                rotateLogFile()
                currentFile?.appendText(line)
                currentSize = line.length.toLong()
                return
            }
            file.appendText(line)
            currentSize += line.length
            if (currentSize > MAX_FILE_SIZE) rotateLogFile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    /** Creates a new log file and deletes the oldest files if the count exceeds [MAX_LOG_FILES] */
    @Synchronized
    private fun rotateLogFile() {
        val dir = logDir ?: return
        currentFile = File(dir, "app_${fileDateFmt.format(Date())}.log")
        currentSize = 0
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        val excess = files.size - MAX_LOG_FILES
        if (excess > 0) files.take(excess).forEach { it.delete() }
    }

    /** Reads the most recent [lineCount] log lines across all files (newest files first) */
    fun getRecentLogs(lineCount: Int = 200): String {
        val files = getLogFiles()
        if (files.isEmpty()) return "No logs available"
        val sb = StringBuilder()
        var remaining = lineCount
        for (f in files) {
            if (remaining <= 0) break
            // Read newest lines first by reversing, taking remaining, then reversing back
            val lines = f.readLines().reversed().take(remaining).reversed()
            sb.appendLine("--- ${f.name} (${lines.size}/${f.readLines().size} lines) ---")
            sb.appendLine(lines.joinToString("\n"))
            remaining -= lines.size
        }
        return sb.toString()
    }

    /** Deletes all log files and starts a fresh log file */
    fun clearLogs() {
        getLogFiles().forEach { it.delete() }
        rotateLogFile()
    }
}
