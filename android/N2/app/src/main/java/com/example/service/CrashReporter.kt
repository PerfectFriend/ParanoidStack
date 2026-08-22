/**
 * Репортёр crash-отчётов в Telegram.
 *
 * Собирает подробную информацию об исключении (дата, устройство,
 * стек-трейс) и отправляет её через Telegram-бота. Также сохраняет
 * копию отчёта локально через CrashLogHandler.
 */
package com.example.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.TelegramReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Репортёр crash-отчётов.
 *
 * При возникновении исключения формирует детальный отчёт
 * (дата, устройство, поток, стек-трейс, причина) и отправляет
 * его через Telegram-бота, если бот был инициализирован.
 * Также дублирует сохранение отчёта локально.
 *
 * @param context Контекст приложения.
 */
class CrashReporter(private val context: Context) {
    private val tag = "CrashReporter"
    /** Coroutine-область для фоновой отправки отчётов в Telegram */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        /** Экземпляр Telegram-репортёра (инициализируется извне) */
        @Volatile
        private var reporter: TelegramReporter? = null

        /**
         * Инициализирует Telegram-бота для отправки crash-отчётов.
         *
         * @param token  Токен Telegram-бота.
         * @param chatId ID чата, куда отправлять отчёты.
         */
        fun initTelegram(token: String, chatId: String) {
            reporter = TelegramReporter(
                botToken = token,
                chatId = chatId,
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            )
        }
    }

    /**
     * Формирует и отправляет отчёт об исключении.
     *
     * Сохраняет полный отчёт локально и отправляет сокращённую
     * версию (первые 3000 символов стек-трейса) в Telegram.
     *
     * @param thread    Поток, в котором произошло исключение.
     * @param throwable Исключение.
     */
    /** Builds a full crash report, saves it locally, and sends a summary via Telegram */
    fun reportCrash(thread: Thread, throwable: Throwable) {
        val report = buildReport(thread, throwable)

        // Сохраняем полный отчёт локально в файл
        try {
            val crashDir = CrashLogHandler.getCrashLogFile(context)
            crashDir.parentFile?.mkdirs()
            crashDir.writeText(report)
            Log.i(tag, "Crash saved to ${crashDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to save crash locally", e)
        }

        // Отправляем сокращённый отчёт в Telegram (если бот инициализирован)
        reporter?.let { r ->
            scope.launch {
                try {
                    val summary = buildString {
                        appendLine("\uD83D\uDCA5 *CRASH REPORT*")
                        appendLine("*Device:* ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                        appendLine("*Thread:* ${thread.name}")
                        appendLine("*Error:* ${throwable::class.simpleName}: ${throwable.message?.take(100)}")
                        appendLine()
                        val sw = StringWriter()
                        throwable.printStackTrace(PrintWriter(sw))
                        // Telegram message size limit — truncate stack trace to first 3000 chars
                        val trace = sw.toString().take(3000)
                        appendLine("```")
                        appendLine(trace)
                        appendLine("```")
                    }
                    r.reportNow(summary)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send crash to Telegram", e)
                }
            }
        }
    }

    /**
     * Формирует полный текстовый отчёт об исключении.
     *
     * Включает дату, модель устройства, версию SDK,
     * информацию о потоке, класс исключения, сообщение,
     * полный стек-трейс и стек-трейс причины (если есть).
     *
     * @param thread    Поток.
     * @param throwable Исключение.
     * @return Отформатированный текстовый отчёт.
     */
    /** Assembles a structured crash report with device info, stack trace, and causal chain */
    private fun buildReport(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("=== CRASH REPORT ===")
        appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("Device: ${Build.MODEL} (${Build.MANUFACTURER})")
        appendLine("SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        appendLine("Thread: ${thread.name} (id=${thread.threadId()})")
        appendLine("Exception: ${throwable::class.simpleName}")
        appendLine("Message: ${throwable.message}")
        appendLine()
        appendLine("Stack Trace:")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        appendLine(sw.toString())
        appendLine()
        appendLine("Cause:")
        // Если у исключения была причина, добавляем её стек-трейс
        throwable.cause?.let {
            val csw = StringWriter()
            it.printStackTrace(PrintWriter(csw))
            appendLine(csw.toString())
        }
    }
}
