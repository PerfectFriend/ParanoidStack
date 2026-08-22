package com.example.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * P2P App Transport Store — децентрализованный магазин приложений,
 * работающих поверх Tor/I2P транспортного протокола [TransportProvider].
 *
 * Каждое приложение в магазине — это APK, подписанный и распространяемый
 * через P2P-сеть (SMP queues, Tor hidden services, IPFS).
 * Установка и обновление происходят полностью анонимно через Tor.
 */
object TransportAppStore {

    private const val TAG = "TransportAppStore"

    /** Реестр доступных P2P-приложений (загружается из P2P-сети) */
    private val registry = mutableListOf<AppEntry>()

    /** Корневая директория для хранения APK */
    private var appsDir: File? = null

    /**
     * Инициализация стора. Загружает реестр из кеша и подписывается на обновления.
     */
    fun init(context: Context) {
        appsDir = File(context.filesDir, "p2p_apps").also { it.mkdirs() }
        loadCache()
        subscribeToUpdates()
    }

    /** Возвращает список доступных приложений */
    fun getAvailableApps(): List<AppEntry> = registry.toList()

    /**
     * Скачивает и устанавливает P2P-приложение.
     * Весь трафик идёт через Tor → V2Ray цепочку [TransportProvider].
     */
    fun installApp(entry: AppEntry, onProgress: (Float) -> Unit = {}): Boolean {
        val dir = appsDir ?: return false
        try {
            val apkFile = File(dir, "${entry.packageName}.apk")
            if (apkFile.exists() && verifySignature(apkFile, entry.sha256)) {
                Log.i(TAG, "App ${entry.name} already cached")
                return installApk(apkFile)
            }

            // Скачиваем APK через P2P-транспорт
            val sock = TransportProvider.connect(entry.downloadHost, entry.downloadPort)
            try {
                val request = JSONObject().apply {
                    put("action", "download")
                    put("package", entry.packageName)
                }
                sock.outputStream.write(request.toString().toByteArray())
                sock.outputStream.flush()

                val buf = ByteArray(65536)
                val fos = apkFile.outputStream()
                try {
                    var totalRead = 0L
                    val maxSize = entry.sizeBytes
                    while (true) {
                        val n = sock.inputStream.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                        totalRead += n
                        if (maxSize > 0) onProgress(totalRead.toFloat() / maxSize)
                    }
                } finally {
                    fos.close()
                }
            } finally {
                sock.close()
            }

            if (!verifySignature(apkFile, entry.sha256)) {
                Log.e(TAG, "Signature mismatch for ${entry.name}")
                apkFile.delete()
                return false
            }

            return installApk(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed for ${entry.name}", e)
            return false
        }
    }

    /** Публикует приложение в P2P-реестр */
    fun publishApp(entry: AppEntry, apkBytes: ByteArray): Boolean {
        val dir = appsDir ?: return false
        return try {
            val apkFile = File(dir, "${entry.packageName}.apk")
            apkFile.writeBytes(apkBytes)

            val manifest = entry.toJson()
            val manFile = File(dir, "${entry.packageName}.json")
            manFile.writeText(manifest.toString(2))

            broadcastToNetwork(manifest)
            registry.add(entry)
            saveCache()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Publish failed", e)
            false
        }
    }

    // --- Регистрация подписок на P2P-обновления ---

    private fun subscribeToUpdates() {
        // В реальной реализации: подписываемся на SMP-каналы/onion-фиды
        Log.i(TAG, "Subscribed to P2P app store updates via Tor hidden services")
    }

    private fun broadcastToNetwork(manifest: JSONObject) {
        // В реальной реализации: публикуем через SMP broadcast / DHT
        Log.i(TAG, "Broadcasting app manifest to P2P network: ${manifest.optString("name")}")
    }

    // --- Локальный кеш ---

    private fun loadCache() {
        val dir = appsDir ?: return
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        for (f in files) {
            try {
                val json = JSONObject(f.readText())
                registry.add(AppEntry.fromJson(json))
            } catch (_: java.lang.Exception) { Log.w("TransportAppStore", "ignored exception") }
        }
        Log.i(TAG, "Loaded ${registry.size} cached apps")
    }

    private fun saveCache() {
        val dir = appsDir ?: return
        registry.forEach { entry ->
            val f = File(dir, "${entry.packageName}.json")
            f.writeText(entry.toJson().toString(2))
        }
    }

    // --- Верификация и установка ---

    private fun verifySignature(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isEmpty()) return true
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            hash == expectedSha256
        } catch (_: Exception) { false }
    }

    private fun installApk(apkFile: File): Boolean {
        // В реальной реализации: PackageInstaller API или root install
        Log.i(TAG, "Installing ${apkFile.name} (requires user consent via system dialog)")
        return true
    }

    // --- data class'ы ---

    data class AppEntry(
        val packageName: String,
        val name: String,
        val version: Int,
        val description: String,
        val category: AppCategory,
        val downloadHost: String,
        val downloadPort: Int,
        val sizeBytes: Long,
        val sha256: String,
        val onionAddress: String  // .onion адрес для P2P-коммуникации
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("package", packageName)
            put("name", name)
            put("version", version)
            put("description", description)
            put("category", category.name)
            put("download_host", downloadHost)
            put("download_port", downloadPort)
            put("size_bytes", sizeBytes)
            put("sha256", sha256)
            put("onion", onionAddress)
        }

        companion object {
            fun fromJson(json: JSONObject): AppEntry = AppEntry(
                packageName = json.getString("package"),
                name = json.getString("name"),
                version = json.getInt("version"),
                description = json.optString("description", ""),
                category = try { AppCategory.valueOf(json.getString("category")) } catch (_: Exception) { AppCategory.OTHER },
                downloadHost = json.optString("download_host", ""),
                downloadPort = json.optInt("download_port", 443),
                sizeBytes = json.optLong("size_bytes", 0),
                sha256 = json.optString("sha256", ""),
                onionAddress = json.optString("onion", "")
            )
        }
    }

    enum class AppCategory {
        MESSENGER,
        SOCIAL,
        FILE_SHARING,
        VPN,
        WALLET,
        NEWS,
        BROWSER,
        GAME,
        MEDIA,
        DEV_TOOLS,
        OTHER
    }
}

/**
 * Список концептуальных P2P-приложений для транспорта.
 * Реальный реестр загружается из P2P-сети, это демо-набор.
 */
object DemoAppCatalogue {

    fun getDemoApps(): List<TransportAppStore.AppEntry> = listOf(
        TransportAppStore.AppEntry(
            packageName = "com.zarik.p2pchat",
            name = "Zarik P2P Chat",
            version = 1,
            description = "Децентрализованный мессенджер поверх Tor/SMP. Полное E2E-шифрование, " +
                    "никаких серверов, P2P-маршрутизация через .onion.",
            category = TransportAppStore.AppCategory.MESSENGER,
            downloadHost = "p2pchat",
            downloadPort = 443,
            sizeBytes = 8_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.zarik.anonsocial",
            name = "AnonSocial",
            version = 1,
            description = "Анонимная социальная сеть. Посты, лайки, репосты — " +
                    "всё через Tor. Никакой цензуры, полная псевдонимность.",
            category = TransportAppStore.AppCategory.SOCIAL,
            downloadHost = "anonsocial",
            downloadPort = 443,
            sizeBytes = 15_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.zarik.anontube",
            name = "AnonTube",
            version = 1,
            description = "Видеохостинг через P2P-транспорт. Стриминг и загрузка " +
                    "видео через Tor + IPFS. Никакого отслеживания.",
            category = TransportAppStore.AppCategory.MEDIA,
            downloadHost = "anontube",
            downloadPort = 443,
            sizeBytes = 20_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.zarik.darkwallet",
            name = "DarkWallet",
            version = 1,
            description = "Криптовалютный кошелёк с маршрутизацией всех " +
                    "транзакций через Tor. Поддержка BTC, XMR, ETH.",
            category = TransportAppStore.AppCategory.WALLET,
            downloadHost = "darkwallet",
            downloadPort = 443,
            sizeBytes = 12_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.zarik.freedomnews",
            name = "Freedom News",
            version = 1,
            description = "Агрегатор новостей из независимых источников. " +
                    "RSS-фиды через Tor, никакой фильтрации трафика.",
            category = TransportAppStore.AppCategory.NEWS,
            downloadHost = "freedomnews",
            downloadPort = 443,
            sizeBytes = 5_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.zarik.onionshare",
            name = "OnionShare Files",
            version = 1,
            description = "Файлообменник через Tor hidden services. " +
                    "Временные .onion ссылки для одноразового обмена файлами.",
            category = TransportAppStore.AppCategory.FILE_SHARING,
            downloadHost = "onionshare",
            downloadPort = 443,
            sizeBytes = 10_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.zarik.exitnode",
            name = "ExitNode VPN",
            version = 1,
            description = "Децентрализованный VPN-маркетплейс. Покупайте/продавайте " +
                    "выходные ноды через Tor. Оплата в Monero.",
            category = TransportAppStore.AppCategory.VPN,
            downloadHost = "exitnode",
            downloadPort = 443,
            sizeBytes = 18_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.zarik.zeroname",
            name = "ZeroName DNS",
            version = 1,
            description = "Децентрализованная DNS-система на блокчейне. " +
                    ".anon домены, не подлежащие цензуре.",
            category = TransportAppStore.AppCategory.DEV_TOOLS,
            downloadHost = "zeroname",
            downloadPort = 443,
            sizeBytes = 7_000_000,
            sha256 = "",
            onionAddress = ""
        ),
        TransportAppStore.AppEntry(
            packageName = "com.notgammon.app",
            name = "Not Gammon Online",
            version = 1,
            description = "Not Gammon Online. PvP через Tor. " +
                    "Рейтинг, турниры, чат. Полная анонимность.",
            category = TransportAppStore.AppCategory.GAME,
            downloadHost = "cbgonion",
            downloadPort = 443,
            sizeBytes = 25_000_000,
            sha256 = "",
            onionAddress = ""
        )
    )
}
