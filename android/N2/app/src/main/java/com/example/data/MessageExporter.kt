package com.example.data

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream

/**
 * Экспорт чата в текстовый файл.
 * Формат: [дата время] Отправитель: текст сообщения.
 */
/**
 * Exports chat history to plain-text format.
 * Format: [dd.MM.yy HH:mm:ss] Sender: [edited] message text
 */
class MessageExporter {

    /**
     * Write the full chat history to the given output stream.
     * Messages are sorted chronologically. Outgoing messages are labelled "Я", incoming use [contactName].
     * Edited messages are prefixed with "[edited]".
     */
    fun exportChat(
        messages: List<SecureMessageEntity>,
        contactName: String,
        outputStream: OutputStream
    ) {
        val writer = outputStream.bufferedWriter()
        writer.write("=== Экспорт чата: $contactName ===\n")
        writer.write("Дата: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.forLanguageTag("ru")).format(java.util.Date())}\n")
        writer.write("Всего сообщений: ${messages.size}\n")
        writer.write("=" .repeat(50) + "\n\n")

        messages.sortedBy { it.timestamp }.forEach { msg ->
            val time = java.text.SimpleDateFormat("dd.MM.yy HH:mm:ss", java.util.Locale.forLanguageTag("ru"))
                .format(java.util.Date(msg.timestamp))
            val sender = if (msg.isOutgoing) "Я" else contactName
            val text = msg.messageText.decodeToString()
            val prefix = if (msg.isEdited) "[edited] " else ""
            writer.write("[$time] $sender: $prefix$text\n")
        }

        writer.flush()
    }

    /**
     * Export chat to a content URI (e.g. SAF picker result).
     * @return true if the export succeeded, false on error.
     */
    fun exportToUri(context: Context, messages: List<SecureMessageEntity>, contactName: String, uri: Uri): Boolean {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri) ?: return false
            exportChat(messages, contactName, outputStream)
            outputStream.close()
            true
        } catch (_: Exception) { false }
    }
}
