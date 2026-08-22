/**
 * Обработчик неотловленных исключений (crash-логов).
 *
 * Перехватывает все непойманные исключения в главном потоке,
 * сохраняет детальный отчёт в локальный файл и передаёт
 * информацию в CrashReporter для отправки через Telegram.
 * Является заменой (обёрткой) стандартного обработчика.
 */
package com.example.service

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Обработчик неотловленных исключений приложения.
 *
 * Перехватывает все непойманные исключения, сохраняет
 * подробный отчёт (стек-трейс, информация об устройстве,
 * дата) в локальный файл и дублирует отправку через
 * CrashReporter в Telegram. После сохранения передаёт
 * управление стандартному обработчику.
 *
 * @param context Контекст приложения для доступа к файловой системе.
 */
class CrashLogHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    /** Стандартный обработчик, установленный системой до нас */
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val tag = "CrashLogHandler"

    companion object {
        /** Директория для хранения crash-логов внутри filesDir */
        private const val CRASH_DIR = "crash_logs"
        /** Имя файла последнего crash-лога */
        private const val CRASH_FILE = "last_crash.txt"

        /**
         * Возвращает файл для записи/чтения crash-лога.
         * Директория создаётся при необходимости.
         *
         * @param context Контекст приложения.
         * @return Файл crash-лога.
         */
        /** Returns the crash log file path, creating the parent directory if needed */
        fun getCrashLogFile(context: Context): File {
            val dir = File(context.filesDir, CRASH_DIR)
            dir.mkdirs()
            return File(dir, CRASH_FILE)
        }

        /**
         * Проверяет существование сохранённого crash-лога.
         *
         * @param context Контекст приложения.
         * @return true, если файл crash-лога существует.
         */
        fun hasCrashLog(context: Context): Boolean {
            return getCrashLogFile(context).exists()
        }

        /**
         * Читает содержимое crash-лога, если файл существует.
         *
         * @param context Контекст приложения.
         * @return Текст crash-лога или null, если файл отсутствует.
         */
        fun readCrashLog(context: Context): String? {
            val file = getCrashLogFile(context)
            return if (file.exists()) file.readText() else null
        }

        /**
         * Удаляет сохранённый crash-лог.
         *
         * @param context Контекст приложения.
         */
        fun deleteCrashLog(context: Context) {
            getCrashLogFile(context).delete()
        }
    }

    /**
     * Устанавливает текущий экземпляр как глобальный
     * обработчик неотловленных исключений.
     */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /**
     * Вызывается при возникновении неотловленного исключения.
     *
     * Формирует текстовый отчёт с датой, информацией об устройстве,
     * стек-трейсом и причиной исключения. Сохраняет отчёт в файл
     * и отправляет через CrashReporter. После этого передаёт
     * управление стандартному обработчику (системе).
     *
     * @param thread    Поток, в котором произошло исключение.
     * @param throwable Само исключение.
     */
    /** Handles an uncaught exception by saving a detailed report and forwarding to the default handler */
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val msg = "${throwable::class.simpleName}: ${throwable.message}"
            Log.e(tag, msg, throwable)
            AppLogger.e(tag, "CRASH: $msg", throwable)
            // Формируем текстовый отчёт об исключении
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("=== CRASH LOG ===")
            pw.println("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            pw.println("Thread: ${thread.name} (id=${thread.threadId()})")
            pw.println("Device: ${android.os.Build.MODEL} (${android.os.Build.VERSION.SDK_INT})")
            pw.println()
            pw.println("Stack Trace:")
            throwable.printStackTrace(pw)
            pw.println()
            pw.println("Cause:")
            throwable.cause?.printStackTrace(pw)
            pw.close()

            // Сохраняем отчёт в локальный файл
            val crashFile = getCrashLogFile(context)
            crashFile.parentFile?.mkdirs()
            FileWriter(crashFile).use { it.write(sw.toString()) }

            Log.e(tag, "Crash saved to ${crashFile.absolutePath}")

            // Appends the last 100 AppLogger lines to the crash file for additional context
            try {
                val recent = AppLogger.getRecentLogs(100)
                FileWriter(crashFile, true).use { it.write("\n\n=== RECENT LOGS ===\n$recent") }
            } catch (_: java.lang.Exception) { Log.w("CrashLogHandler", "ignored exception") }

            // Дополнительно отправляем отчёт через CrashReporter (Telegram)
            CrashReporter(context).reportCrash(thread, throwable)
        } catch (e: Exception) {
            Log.e(tag, "Failed to save crash log", e)
        }

        // Passes control to the system's default handler so the OS can terminate the app
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
