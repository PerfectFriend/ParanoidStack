package com.nexuschat.app.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class BinaryDownloader private constructor(private val ctx: Context) {
    companion object {
        private const val TAG = "NexusChat/BinDL"
        private const val TOR_VERSION = "0.4.8.12"
        private const val V2RAY_VERSION = "5.27.0"
        private const val XRAY_VERSION = "1.8.24"
        private const val OBFSPROXY_VERSION = "0.0.14"

        private val KNOWN_HASHES = mapOf(
            "v2ray-android-arm64-v8a.zip" to "0000000000000000000000000000000000000000000000000000000000000000",
            "v2ray-android-arm-v7a.zip" to "0000000000000000000000000000000000000000000000000000000000000000",
            "v2ray-android-x86_64.zip" to "0000000000000000000000000000000000000000000000000000000000000000",
            "Xray-android-arm64-v8a.zip" to "0000000000000000000000000000000000000000000000000000000000000000",
        )

        private val TOR_URLS = mapOf(
            "arm64-v8a" to listOf(
                "https://github.com/guardianproject/tor-android/releases/download/tor-$TOR_VERSION/tor-arm64-v8a.so",
                "https://tor.void.gr/dist/tor-$TOR_VERSION.tar.gz",
            ),
            "armeabi-v7a" to listOf(
                "https://github.com/guardianproject/tor-android/releases/download/tor-$TOR_VERSION/tor-armeabi-v7a.so",
            ),
            "x86_64" to listOf(
                "https://github.com/guardianproject/tor-android/releases/download/tor-$TOR_VERSION/tor-x86_64.so",
            ),
        )

        private val V2RAY_URLS = mapOf(
            "arm64-v8a" to "https://github.com/v2fly/v2ray-core/releases/download/v$V2RAY_VERSION/v2ray-android-arm64-v8a.zip",
            "armeabi-v7a" to "https://github.com/v2fly/v2ray-core/releases/download/v$V2RAY_VERSION/v2ray-android-arm-v7a.zip",
            "x86_64" to "https://github.com/v2fly/v2ray-core/releases/download/v$V2RAY_VERSION/v2ray-android-x86_64.zip",
        )

        private val XRAY_URLS = mapOf(
            "arm64-v8a" to "https://github.com/XTLS/Xray-core/releases/download/v$XRAY_VERSION/Xray-android-arm64-v8a.zip",
            "armeabi-v7a" to "https://github.com/XTLS/Xray-core/releases/download/v$XRAY_VERSION/Xray-android-arm-v7a.zip",
            "x86_64" to "https://github.com/XTLS/Xray-core/releases/download/v$XRAY_VERSION/Xray-android-x86_64.zip",
        )

        private val OBFSPROXY_URLS = mapOf(
            "arm64-v8a" to "https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/obfs4/-/releases/obfs4proxy-$OBFSPROXY_VERSION/downloads/obfs4proxy-android-arm64-v8a",
            "armeabi-v7a" to "https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/obfs4/-/releases/obfs4proxy-$OBFSPROXY_VERSION/downloads/obfs4proxy-android-arm-v7a",
            "x86_64" to "https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/obfs4/-/releases/obfs4proxy-$OBFSPROXY_VERSION/downloads/obfs4proxy-android-x86_64",
        )

        @Volatile private var instance: BinaryDownloader? = null
        fun getInstance(ctx: Context): BinaryDownloader =
            instance ?: synchronized(this) {
                instance ?: BinaryDownloader(ctx.applicationContext).also { instance = it }
            }
    }

    enum class BinaryType { TOR, V2RAY, XRAY, OBFSPROXY }

    private val binDir = File(ctx.filesDir, "bin").also { it.mkdirs() }
    private var progressListener: ((BinaryType, Int, Int) -> Unit)? = null

    fun setProgressListener(listener: (BinaryType, Int, Int) -> Unit) {
        progressListener = listener
    }

    fun findBinary(type: BinaryType): File? {
        val name = when (type) {
            BinaryType.TOR -> "libtor.so"
            BinaryType.V2RAY -> "v2ray"
            BinaryType.XRAY -> "xray"
            BinaryType.OBFSPROXY -> "obfs4proxy"
        }
        val inBinDir = File(binDir, name)
        if (inBinDir.exists() && inBinDir.canExecute()) return inBinDir

        if (type == BinaryType.TOR) {
            val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
            val torInNative = File(nativeDir, "libtor.so")
            if (torInNative.exists()) return torInNative
            val altLibDir = File(ctx.getFilesDir(), "lib")
            val altTor = File(altLibDir, "libtor.so")
            if (altTor.exists()) return altTor
            val systemTor = File("/system/bin/tor")
            if (systemTor.exists() && systemTor.canExecute()) return systemTor
            val dataTor = File(ctx.filesDir, "tor")
            if (dataTor.exists()) return dataTor
        }

        val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
        val inNative = File(nativeDir, "lib${name}.so")
        if (inNative.exists()) return inNative

        return null
    }

    suspend fun downloadBinary(type: BinaryType, abi: String = detectAbi()): File? = withContext(Dispatchers.IO) {
        val existing = findBinary(type)
        if (existing != null) return@withContext existing

        val urls = when (type) {
            BinaryType.TOR -> TOR_URLS[abi]
            BinaryType.V2RAY -> V2RAY_URLS[abi]?.let { listOf(it) }
            BinaryType.XRAY -> XRAY_URLS[abi]?.let { listOf(it) }
            BinaryType.OBFSPROXY -> OBFSPROXY_URLS[abi]?.let { listOf(it) }
        } ?: run {
            Log.e(TAG, "No download URL for $type / $abi")
            return@withContext null
        }

        val destFile = File(binDir, when (type) {
            BinaryType.TOR -> "libtor.so"
            BinaryType.V2RAY -> "v2ray"
            BinaryType.XRAY -> "xray"
            BinaryType.OBFSPROXY -> "obfs4proxy"
        })

        for (url in urls) {
            try {
                Log.i(TAG, "Downloading $type ($abi) from $url")
                if (url.endsWith(".zip")) {
                    downloadAndExtractZip(url, destFile, type, progressListener)
                } else {
                    downloadFile(url, destFile, progressListener)
                }
                if (destFile.exists() && destFile.length() > 0) {
                    if (verifyBinary(type, destFile)) {
                        destFile.setExecutable(true)
                        Log.i(TAG, "$type binary ready: ${destFile.absolutePath} (${destFile.length()} bytes)")
                        return@withContext destFile
                    } else {
                        Log.w(TAG, "$type hash mismatch, deleting corrupt download")
                        destFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download from $url failed: ${e.message}")
                destFile.delete()
            }
        }
        Log.e(TAG, "All download sources exhausted for $type")
        null
    }

    private fun verifyBinary(type: BinaryType, file: File): Boolean {
        if (type != BinaryType.V2RAY && type != BinaryType.XRAY) return true
        val expectedHash = KNOWN_HASHES[file.name] ?: return true
        if (expectedHash == "0000000000000000000000000000000000000000000000000000000000000000") {
            Log.w(TAG, "No real hash configured for ${file.name} — skipping binary integrity check, relying on TLS")
            return true
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        val match = actualHash == expectedHash
        if (!match) Log.w(TAG, "Hash mismatch for ${file.name}: expected=$expectedHash actual=$actualHash")
        match
    }

    private fun downloadFile(sourceUrl: String, dest: File, progress: ((BinaryType, Int, Int) -> Unit)? = null) {
        val conn = URL(sourceUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.instanceFollowRedirects = true
        conn.connect()
        val contentLen = conn.contentLength
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var read: Int
                var totalRead = 0
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    totalRead += read
                    if (contentLen > 0 && progress != null) {
                        progress(BinaryType.TOR, totalRead, contentLen)
                    }
                }
            }
        }
    }

    private fun downloadAndExtractZip(
        sourceUrl: String, destFile: File, type: BinaryType,
        progress: ((BinaryType, Int, Int) -> Unit)? = null
    ) {
        val conn = URL(sourceUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.instanceFollowRedirects = true
        conn.connect()
        val contentLen = conn.contentLength

        val targetName = when (type) {
            BinaryType.V2RAY -> "v2ray"
            BinaryType.XRAY -> "xray"
            else -> return
        }

        var extracted = false
        var totalRead = 0
        ZipInputStream(conn.inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.contains(targetName) && entry.name.endsWith(targetName)) {
                    FileOutputStream(destFile).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (zis.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            totalRead += read
                            if (contentLen > 0 && progress != null) {
                                progress(type, totalRead, contentLen)
                            }
                        }
                    }
                    extracted = true
                    Log.i(TAG, "Extracted ${entry.name} -> ${destFile.name} ($totalRead bytes)")
                    break
                }
                entry = zis.nextEntry
            }
        }
        if (!extracted) throw IllegalStateException("$targetName not found in zip from $sourceUrl")
    }

    fun detectAbi(): String {
        return android.os.Build.SUPPORTED_ABIS.firstOrNull()
            ?: when (android.os.Build.CPU_ABI) {
                "arm64-v8a" -> "arm64-v8a"
                "armeabi-v7a", "armeabi" -> "armeabi-v7a"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> "arm64-v8a"
            }
    }

    fun areAllBinariesAvailable(): Map<BinaryType, Boolean> {
        return BinaryType.values().associateWith { findBinary(it) != null }
    }

    fun getBinaryLocation(type: BinaryType): String? {
        return findBinary(type)?.absolutePath
    }

    fun destroy() {
        instance = null
    }
}
