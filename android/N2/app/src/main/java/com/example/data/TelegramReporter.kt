/**
 * Репортёр статуса приложения в Telegram.
 * Позволяет отправлять сообщения о состоянии приложения,
 * автоматически собирать логи и отправлять их через Telegram Bot API.
 * Использует очередь сообщений с фоновой отправкой.
 */
package com.example.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Репортёр метрик и логов в Telegram */
class TelegramReporter(
    private var botToken: String = System.getenv("TELEGRAM_BOT_TOKEN") ?: "",
    private var chatId: String = System.getenv("TELEGRAM_CHAT_ID") ?: "",
    private val scope: CoroutineScope,
    private val maxQueueSize: Int = 50
) {
    private val messageQueue = mutableListOf<String>()
    private var flushJob: Job? = null
    private var enabled = botToken.isNotBlank() && chatId.isNotBlank()
    private val startTime = System.currentTimeMillis()

    /** Включён ли репортёр */
    fun isEnabled(): Boolean = enabled

    /** Обновить конфигурацию (токен и чат) */
    fun updateConfig(token: String, id: String) {
        botToken = token
        chatId = id
        enabled = token.isNotBlank() && id.isNotBlank()
    }

    /**
     * Добавить сообщение в очередь на отправку.
     * Максимальный размер очереди — [maxQueueSize] сообщений.
     */
    fun report(message: String) {
        if (!enabled) return
        synchronized(messageQueue) {
            if (messageQueue.size >= maxQueueSize) {
                messageQueue.removeAt(0) // удаляем самое старое
            }
            messageQueue.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $message")
        }
        scheduleFlush()
    }

    /** Отправить сообщение немедленно */
    fun reportNow(message: String) {
        if (!enabled) return
        scope.launch(Dispatchers.IO) {
            sendTelegramMessage(message)
        }
    }

    /** Отправить форматированный отчёт о состоянии приложения */
    fun reportStatus(bridgeStatus: String, torStatus: String, simplexStatus: String, vpnPing: Int) {
        val msg = buildString {
            appendLine("\uD83D\uDCC8 *Not Gammon Status Report*")
            appendLine()
            appendLine("\u2022 Bridge: $bridgeStatus")
            appendLine("\u2022 Tor: $torStatus")
            appendLine("\u2022 SimpleX: $simplexStatus")
            appendLine("\u2022 VPN ping: ${vpnPing}ms")
            appendLine("\u2022 Uptime: ${(System.currentTimeMillis() - startTime) / 1000}s")
        }
        reportNow(msg)
    }

    /** Запланировать отправку очереди через 5 секунд */
    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5000)
            flush()
        }
    }

    /** Отправить все накопленные сообщения */
    private fun flush() {
        val batch = synchronized(messageQueue) {
            val copy = messageQueue.toList()
            messageQueue.clear()
            copy
        }
        if (batch.isEmpty()) return
        val text = batch.joinToString("\n")
        // Telegram имеет ограничение 4096 символов на сообщение
        if (text.length > 4000) {
            text.chunked(4000).forEach { sendTelegramMessage(it) }
        } else {
            sendTelegramMessage(text)
        }
    }

    /** Отправить сообщение через Telegram Bot API */
    private fun sendTelegramMessage(text: String) {
        try {
            val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
            val conn = (url.openConnection() as? HttpURLConnection) ?: return
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val data = "chat_id=$chatId&text=${URLEncoder.encode(text, "UTF-8")}&parse_mode=Markdown"
            OutputStreamWriter(conn.outputStream).use { it.write(data) }
            val code = conn.responseCode
            if (code != 200) {
                Log.w("TelegramReporter", "Telegram API returned $code")
            }
        } catch (e: Exception) {
            Log.w("TelegramReporter", "Failed to send: ${e.message}")
        }
    }
}
