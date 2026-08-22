/**
 * Утилита для экспорта и управления логами приложения.
 *
 * Позволяет выгружать системный logcat, сохранять внутренние
 * логи приложения, отправлять их через системный Intent и
 * очищать накопленные файлы логов.
 */
package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Утилита экспорта и управления логами.
 *
 * Предоставляет методы для выгрузки системного logcat в файл,
 * копирования внутренних логов приложения, отправки их через
 * системный Intent (share) и очистки накопленных лог-файлов.
 *
 * @param context Контекст приложения.
 */
class LogExporter(private val context: Context) {
    private val tag = "LogExporter"

    /**
     * Экспортирует системный logcat (все потоки) в текстовый файл.
     *
     * Выполняет команду `logcat -d -v threadtime`, сохраняет
     * результат в кэш-директорию и возвращает URI файла.
     *
     * @return URI экспортированного файла или null при ошибке.
     */
    /** Dumps system logcat to a file in cache dir and returns its file URI */
    fun exportLogcatToFile(): Uri? {
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(context.cacheDir, "debug_log_$dateStr.txt")

            // Выполняем команду logcat для дампа логов
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
            val inputStream = process.inputStream
            val output = file.outputStream()
            inputStream.copyTo(output)
            output.close()
            inputStream.close()
            process.waitFor()

            Log.i(tag, "Log exported to ${file.absolutePath} (${file.length()} bytes)")
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(tag, "Failed to export logcat", e)
            null
        }
    }

    /**
     * Экспортирует внутренние логи приложения в текстовый файл.
     *
     * Собирает файлы логов из AppLogger (app_logs/) и legacy logs/,
     * объединяет их содержимое и сохраняет в единый файл.
     *
     * @return Абсолютный путь к файлу экспорта или null при ошибке.
     */
    /** Combines AppLogger files and legacy logs into a single export file */
    fun exportAppLogs(): String? {
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(context.filesDir, "app_log_$dateStr.txt")

            val combined = StringBuilder()
            val appLogDir = File(context.filesDir, "app_logs")
            val legacyLogDir = File(context.filesDir, "logs")

            fun appendDir(dir: File, label: String) {
                if (dir.exists()) {
                    combined.appendLine("=== $label ===")
                    dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                        combined.appendLine("--- ${f.name} (${f.length()} bytes) ---")
                        combined.append(f.readText().take(15000))
                        combined.appendLine()
                    }
                }
            }
            appendDir(appLogDir, "AppLogger logs")
            appendDir(legacyLogDir, "Legacy logs")
            if (combined.isEmpty()) combined.appendLine("No log files found.")
            file.writeText(combined.toString())

            Log.i(tag, "App logs exported to ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(tag, "Failed to export app logs", e)
            null
        }
    }

    /**
     * Открывает системный диалог отправки файла лога.
     *
     * Использует Intent.ACTION_SEND с указанным URI файла.
     *
     * @param uri URI файла для отправки.
     */
    fun shareLog(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Отправить лог"))
        } catch (e: Exception) {
            Log.e(tag, "Failed to share log", e)
        }
    }

    /**
     * Очищает все накопленные лог-файлы.
     *
     * Удаляет файлы из внутренней директории logs/ и из кэш-директории,
     * имена которых начинаются с "debug_log_" или "app_log_".
     *
     * @return true, если очистка выполнена успешно, иначе false.
     */
    /** Removes legacy logs from filesDir/logs/ and exported files from cache */
    fun clearLogs(): Boolean {
        return try {
            // Удаляем файлы из внутренней директории logs/
            val logDir = File(context.filesDir, "logs")
            if (logDir.exists()) {
                logDir.listFiles()?.forEach { it.delete() }
            }
            // Удаляем экспортированные файлы из кэша
            val cacheFiles = context.cacheDir.listFiles { f -> f.name.startsWith("debug_log_") || f.name.startsWith("app_log_") }
            cacheFiles?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to clear logs", e)
            false
        }
    }
}
