/**
 * WebDAV-based backup and restore for profiles and settings.
 * Uploads/downloads encrypted backup blobs to/from a remote WebDAV server
 * using Basic HTTP authentication. Data is expected to be pre-encrypted
 * (e.g. via [ProfileBackupManager]) before upload.
 */
package com.example.data

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Manages backup upload/download to/from a WebDAV server.
 *
 * @param serverUrl Base URL of the WebDAV directory.
 * @param username WebDAV account username.
 * @param password WebDAV account password.
 */
class WebDavBackup(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    data class WebDavConfig(
        val serverUrl: String = "",
        val username: String = "",
        val password: String = "",
        val enabled: Boolean = false
    )

    /**
     * Upload encrypted backup data to the WebDAV server via HTTP PUT.
     * @param data The encrypted blob (e.g. from [ProfileBackupManager.exportProfiles]).
     * @param fileName Remote file name on the server.
     * @return true if the server returned a 2xx status code.
     */
    fun uploadBackup(data: ByteArray, fileName: String = "n2_backup.enc"): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$serverUrl/$fileName")
            conn = url.openConnection() as? HttpURLConnection ?: return false
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            conn.setRequestProperty("Authorization", "Basic $auth")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.outputStream.write(data)
            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            Log.e("WebDavBackup", "Upload failed: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Download encrypted backup data from the WebDAV server via HTTP GET.
     * @param fileName Remote file name on the server.
     * @return The raw bytes of the file, or null on failure.
     */
    fun downloadBackup(fileName: String = "n2_backup.enc"): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$serverUrl/$fileName")
            conn = url.openConnection() as? HttpURLConnection ?: return null
            conn.requestMethod = "GET"
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            conn.setRequestProperty("Authorization", "Basic $auth")
            val stream = ByteArrayOutputStream()
            conn.inputStream.copyTo(stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Log.e("WebDavBackup", "Download failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        /**
         * Verify WebDAV server connectivity using PROPFIND.
         * @return true if the server responds with a 2xx status code.
         */
        fun testConnection(url: String, user: String, pass: String): Boolean {
            var conn: HttpURLConnection? = null
            return try {
                conn = URL(url).openConnection() as? HttpURLConnection ?: return false
                conn.requestMethod = "PROPFIND"
                conn.connectTimeout = 10000
                val auth = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
                conn.setRequestProperty("Authorization", "Basic $auth")
                val code = conn.responseCode
                code in 200..299
            } catch (_: Exception) { false }
            finally { conn?.disconnect() }
        }
    }
}
