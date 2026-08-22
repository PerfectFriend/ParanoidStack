/**
 * Менеджер передачи файлов через XFTP-протокол.
 * Позволяет загружать файлы на XFTP-сервер и получать информацию
 * для отправки получателю через SMP-канал.
 */
package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Управление загрузкой файлов на XFTP-сервер.
 * Файл разбивается на чанки, каждый чанк шифруется,
 * и информация о нём отправляется получателю через SMP-сообщение.
 *
 * @param context контекст приложения
 * @param xftpServer XFTP-сервер для загрузки
 */
class FileTransferManager(
    private val context: Context,
    private val xftpServer: XFTPServer
) {
    private val tag = "FileTransferManager"

    /** Прогресс передачи файла */
    data class FileTransferProgress(
        val fileName: String,           // имя файла
        val totalBytes: Long,           // общий размер
        val transferredBytes: Long = 0, // передано байт
        val isComplete: Boolean = false, // завершена ли передача
        val error: String? = null       // сообщение об ошибке
    )

    /** Информация о файле для отправки */
    data class FileToSend(
        val uri: Uri,           // URI файла
        val fileName: String,   // имя файла
        val mimeType: String,   // MIME-тип
        val size: Long          // размер в байтах
    )

    /**
     * Подготовить файл к загрузке: получить метаданные через ContentResolver.
     * @param uri URI файла
     * @return FileToSend или null при ошибке
     */
    suspend fun prepareFileForUpload(uri: Uri): FileToSend? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else "file"
                    val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else -1L
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    FileToSend(uri, name, mime, size)
                } else null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to query file", e)
            null
        }
    }

    /**
     * Загрузить файл на XFTP-сервер и получить JSON-описание для SMP.
     *
     * @param fileToSend информация о файле
     * @param onProgress callback прогресса
     * @return AppResult с JSON-строкой описания чанка, или ошибка
     */
    suspend fun uploadFile(
        fileToSend: FileToSend,
        onProgress: (FileTransferProgress) -> Unit
    ): AppResult<String> {
        return try {
            onProgress(FileTransferProgress(fileToSend.fileName, fileToSend.size, 0))

            val inputStream: InputStream = context.contentResolver.openInputStream(fileToSend.uri)
                ?: return AppException.StorageException("Cannot open file").asError()

            val fileData = inputStream.readBytes()
            inputStream.close()

            // Генерируем ключи для шифрования чанка
            val sndKey = NaClCrypto.generateKeyPair().public.encoded
            val rcvKey = NaClCrypto.generateKeyPair().public.encoded
            val digest = MessageDigest.getInstance("SHA-256").digest(fileData)

            val client = XFTPClient(xftpServer)
            if (!client.connect()) {
                return AppException.NetworkException("Cannot connect to XFTP server").asError()
            }

            // Регистрируем чанк на сервере
            val result = client.registerChunk(sndKey, listOf(rcvKey), fileData.size, digest)
            if (result == null) {
                client.disconnect()
                return AppException.ProtocolException("Chunk registration failed").asError()
            }

            // Загружаем данные чанка
            val uploadOk = client.uploadChunk(result.senderId, fileData)
            client.disconnect()

            if (!uploadOk) {
                return AppException.ProtocolException("Upload failed").asError()
            }

            onProgress(FileTransferProgress(fileToSend.fileName, fileToSend.size, fileData.size.toLong(), true))

            // Формируем JSON с информацией о чанке для отправки через SMP
            val fileInfo = org.json.JSONObject().apply {
                put("type", "xftp.file")
                put("fileName", fileToSend.fileName)
                put("fileSize", fileData.size)
                put("mimeType", fileToSend.mimeType)
                put("digest", android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP))
                put("chunkId", android.util.Base64.encodeToString(result.senderId, android.util.Base64.NO_WRAP))
                put("rcvKey", android.util.Base64.encodeToString(rcvKey, android.util.Base64.NO_WRAP))
                put("serverHost", xftpServer.host)
                put("serverIdentity", xftpServer.serverIdentity)
            }.toString()

            fileInfo.asSuccess()
        } catch (e: Exception) {
            Log.e(tag, "Upload error", e)
            AppException.NetworkException("Upload error: ${e.message}").asError()
        }
    }
}
