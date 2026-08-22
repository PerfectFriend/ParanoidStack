/**
 * Менеджер VPN-подключений на базе Foxray (V2Ray/Xray).
 * Поддерживает протоколы: VLESS, VMess, Trojan, Shadowsocks.
 * Управляет конфигурациями, импортом из ссылок/подписок,
 * тестированием скорости и автоматическим выбором сервера.
 */
package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.service.FoxrayVpnService
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.concurrent.thread
import java.util.Random

/**
 * Конфигурация VPN-сервера Foxray.
 *
 * @property id уникальный идентификатор
 * @property name отображаемое имя
 * @property protocol протокол (VLESS, VMess, Trojan, SS)
 * @property server адрес сервера
 * @property port порт
 * @property uuidOrPassword UUID или пароль
 * @property encryptionOrSecurity тип шифрования/безопасности
 * @property rawContent оригинальное содержимое (ссылка/JSON)
 */
data class FoxrayVpnConfig(
    val id: String,
    val name: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val uuidOrPassword: String,
    val encryptionOrSecurity: String,
    val rawContent: String
)

/**
 * Управление VPN-подключениями Foxray.
 * Поддерживает импорт конфигураций, переключение между серверами,
 * запуск/остановку VPN, тестирование пинга, импорт подписок.
 *
 * @param context контекст приложения
 */
class FoxrayVpnManager(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("foxray_vpn_prefs", Context.MODE_PRIVATE)

    var configs by mutableStateOf<List<FoxrayVpnConfig>>(emptyList())
        private set

    var selectedConfig by mutableStateOf<FoxrayVpnConfig?>(null)
        private set

    var vpnState by mutableStateOf("Disconnected")
        private set

    var pingTime by mutableStateOf(-1)
        private set

    var downloadSpeed by mutableStateOf(0.0)
    var uploadSpeed by mutableStateOf(0.0)
    var totalBytesRx by mutableStateOf(0L)
    var totalBytesTx by mutableStateOf(0L)

    var configPings by mutableStateOf<Map<String, Int>>(emptyMap())
    var isTestingSpeeds by mutableStateOf(false)

    private val random = Random()
    private var statsUpdateThread: Thread? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init { loadConfigs() } // загружаем сохранённые конфигурации при создании

    /** Загрузить конфигурации VPN из SharedPreferences */
    private fun loadConfigs() {
        val jsonString = sharedPrefs.getString("vpn_configs", null)
        val loadedList = mutableListOf<FoxrayVpnConfig>()
        if (jsonString != null) {
            try {
                val ja = JSONArray(jsonString)
                for (i in 0 until ja.length()) {
                    val obj = ja.getJSONObject(i)
                    loadedList.add(FoxrayVpnConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        protocol = obj.getString("protocol"),
                        server = obj.getString("server"),
                        port = obj.getInt("port"),
                        uuidOrPassword = obj.getString("uuid"),
                        encryptionOrSecurity = obj.getString("security"),
                        rawContent = obj.getString("raw")
                    ))
                }
            } catch (e: Exception) { Log.e("FoxrayVpnManager", "exception", e) }
        }
        if (loadedList.isEmpty()) {
            loadedList.add(FoxrayVpnConfig(
                id = "demo_vpn", name = "Demo (configure your own)",
                protocol = "VLESS", server = "127.0.0.1", port = 0,
                uuidOrPassword = "00000000-0000-0000-0000-000000000000",
                encryptionOrSecurity = "none",
                rawContent = ""
            ))
            saveConfigs(loadedList)
        }
        configs = loadedList
        val selectedId = sharedPrefs.getString("selected_vpn_id", null)
        selectedConfig = configs.firstOrNull { it.id == selectedId } ?: configs.firstOrNull()
    }

    private fun saveConfigs(list: List<FoxrayVpnConfig>) {
        try {
            val ja = JSONArray()
            for (c in list) {
                ja.put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("protocol", c.protocol)
                    put("server", c.server); put("port", c.port); put("uuid", c.uuidOrPassword)
                    put("security", c.encryptionOrSecurity); put("raw", c.rawContent)
                })
            }
            sharedPrefs.edit().putString("vpn_configs", ja.toString()).apply()
        } catch (e: Exception) { Log.e("FoxrayVpnManager", "exception", e) }
    }

    /** Выбрать конфигурацию VPN */
    fun selectConfig(config: FoxrayVpnConfig) {
        selectedConfig = config
        sharedPrefs.edit().putString("selected_vpn_id", config.id).apply()
    }

    /** Удалить конфигурацию (нельзя удалить последнюю) */
    fun deleteConfig(id: String) {
        if (configs.size <= 1) return
        val list = configs.filter { it.id != id }
        configs = list
        saveConfigs(list)
        if (selectedConfig?.id == id) selectConfig(configs.first())
    }

    /**
     * Импортировать конфигурации из текста (ссылкок или JSON).
     * Поддерживает форматы: vmess://, vless://, ss://, trojan://, Xray JSON.
     * Также автоматически декодирует Base64-кодированные подписки.
     * @return строка с результатом операции
     */
    fun importConfigsFromText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "Пустой текст ввода"
        var processedText = trimmed
        try {
            if (!trimmed.startsWith("vmess://") && !trimmed.startsWith("vless://") && !trimmed.startsWith("ss://") && !trimmed.startsWith("trojan://") && !trimmed.startsWith("{")) {
                val decodedBytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                val decoded = String(decodedBytes, Charsets.UTF_8).trim()
                if (decoded.contains("://") || decoded.startsWith("{")) processedText = decoded
            }
        } catch (e: Exception) { android.util.Log.d("FoxrayVpnManager", "Base64 decode skipped", e) }
        val lines = processedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var importedCount = 0
        val newList = configs.toMutableList()
        for (line in lines) {
            if (line.startsWith("{")) { parseXrayJsonConfig(line)?.let { newList.add(it); importedCount++ } }
            else if (line.startsWith("vless://") || line.startsWith("vmess://") || line.startsWith("ss://") || line.startsWith("trojan://")) { parseVpnUriLink(line)?.let { newList.add(it); importedCount++ } }
        }
        if (importedCount > 0) { configs = newList; saveConfigs(newList); selectConfig(newList.last()); return "Успешно импортировано профилей: $importedCount" }
        return "Не удалось распознать формат"
    }

    /** Импортировать подписку из URL (загружает и парсит) */
    fun importSubscriptionFromUrl(url: String, onFinished: (String) -> Unit) {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            onFinished("Некорректная ссылка подписки"); return
        }
        Thread {
            var conn: java.net.HttpURLConnection? = null
            try {
                conn = java.net.URL(trimmedUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.requestMethod = "GET"; conn.connect()
                if (conn.responseCode == 200) {
                    var text = conn.inputStream.bufferedReader().readText().trim()
                    try { val decodedBytes = android.util.Base64.decode(text, android.util.Base64.DEFAULT); text = String(decodedBytes, Charsets.UTF_8).trim() } catch (e: Exception) { android.util.Log.d("FoxrayVpnManager", "Subscription Base64 decode skipped", e) }
                    mainHandler.post { onFinished(importConfigsFromText(text)) }
                } else { mainHandler.post { onFinished("Ошибка сети: код ${conn.responseCode}") } }
            } catch (e: Exception) { mainHandler.post { onFinished("Ошибка: ${e.localizedMessage}") } }
            finally { conn?.disconnect() }
        }.start()
    }

    /** Проверить, готов ли VPN (разрешён ли пользователем) */
    fun isVpnPrepared(): Boolean {
        if (isEmulatorOrVirtualDevice()) return true
        return android.net.VpnService.prepare(context) == null
    }

    /** Получить Intent для запроса разрешения VPN */
    fun getVpnPrepareIntent(): Intent? {
        if (isEmulatorOrVirtualDevice()) return null
        return android.net.VpnService.prepare(context)
    }

    /** Запустить VPN-подключение */
    fun startVpn() {
        if (vpnState == "Connected" || vpnState == "Connecting") return
        vpnState = "Connecting"
        pingTime = -1
        try {
            val intent = Intent(context, FoxrayVpnService::class.java).apply {
                action = FoxrayVpnService.ACTION_CONNECT
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("FoxrayVpnManager", "Start failed", e)
        }
        thread(start = false, name = "VpnConnectThread") {
            try {
                Thread.sleep(2000)
                if (FoxrayVpnService.isActive()) {
                    mainHandler.post {
                        vpnState = "Connected"
                        val (rx, tx) = FoxrayVpnService.getStats()
                        totalBytesRx = rx; totalBytesTx = tx
                        pingTime = measurePing()
                    }
                    startStatsUpdater()
                } else {
                    mainHandler.post {
                        vpnState = "Disconnected"
                        pingTime = 0
                    }
                    Log.e("FoxrayVpnManager", "VPN service failed to start - not active")
                }
            } catch (e: Exception) {
                android.util.Log.e("FoxrayVpnManager", "VpnConnectThread error", e)
                mainHandler.post { vpnState = "Disconnected" }
            }
        }.start()
    }

    /** Остановить VPN-подключение */
    fun stopVpn() {
        if (vpnState == "Disconnected") return
        vpnState = "Disconnecting"
        stopStatsUpdater()
        try {
            val intent = Intent(context, FoxrayVpnService::class.java).apply {
                action = FoxrayVpnService.ACTION_DISCONNECT
            }
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("FoxrayVpnManager", "Stop failed", e)
        }
        thread(start = false, name = "VpnDisconnectThread") {
            Thread.sleep(800)
            mainHandler.post {
                vpnState = "Disconnected"
                pingTime = -1; downloadSpeed = 0.0; uploadSpeed = 0.0
            }
        }.start()
    }

    /** Сбросить все настройки VPN к значениям по умолчанию */
    fun resetToDefaults() {
        stopVpn()
        mainHandler.post {
            vpnState = "Disconnected"; pingTime = -1; downloadSpeed = 0.0; uploadSpeed = 0.0
            totalBytesRx = 0L; totalBytesTx = 0L; configPings = emptyMap()
        }
        sharedPrefs.edit().remove("vpn_configs").remove("selected_vpn_id").apply()
        loadConfigs()
    }

    /** Измерить пинг до выбранного сервера */
    private fun measurePing(): Int {
        val cfg = selectedConfig ?: return -1
        return try {
            val start = System.currentTimeMillis()
            val s = java.net.Socket()
            s.connect(InetSocketAddress(cfg.server, cfg.port), 2000)
            s.close()
            (System.currentTimeMillis() - start).toInt().coerceIn(1, 9999)
        } catch (e: Exception) { -1 }
    }

    /** Запустить фоновое обновление статистики трафика */
    private fun startStatsUpdater() {
        stopStatsUpdater()
        statsUpdateThread = thread {
            try {
                while (!Thread.currentThread().isInterrupted && vpnState == "Connected") {
                    Thread.sleep(1500)
                    val (rx, tx) = FoxrayVpnService.getStats()
                    mainHandler.post {
                        totalBytesRx = rx; totalBytesTx = tx
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /** Остановить обновление статистики */
    private fun stopStatsUpdater() {
        statsUpdateThread?.interrupt()
        statsUpdateThread = null
    }

    /** Протестировать все конфигурации и подключиться к самой быстрой */
    fun testAllConfigsAndConnectFastest(onComplete: (String) -> Unit) {
        if (isTestingSpeeds) return
        isTestingSpeeds = true
        Thread {
            val pingsMap = mutableMapOf<String, Int>()
            var bestConfig: FoxrayVpnConfig? = null
            var bestPing = Int.MAX_VALUE
            for (config in configs) {
                pingsMap[config.id] = -2
                mainHandler.post { configPings = pingsMap.toMap() }
                val ping = try {
                    val start = System.currentTimeMillis()
                    val s = java.net.Socket()
                    s.connect(InetSocketAddress(config.server, config.port), 2000)
                    s.close()
                    (System.currentTimeMillis() - start).toInt().coerceIn(1, 9999)
                } catch (e: Exception) { -1 }
                pingsMap[config.id] = ping
                mainHandler.post { configPings = pingsMap.toMap() }
                if (ping > 0 && ping < bestPing) { bestPing = ping; bestConfig = config }
                Thread.sleep(120)
            }
            mainHandler.post { isTestingSpeeds = false }
            if (bestConfig != null) {
                bestConfig?.let { selectConfig(it) }
                if (vpnState == "Connected") { stopVpn(); Thread.sleep(600); startVpn() }
                onComplete("Быстрейший сервер: " + (bestConfig?.name ?: "unknown") + " (${bestPing}ms)")
            } else { onComplete("Все прокси недоступны.") }
        }.start()
    }

    /** Импортировать конфигурацию из файла */
    fun importConfigFromFile(uri: Uri): String {
        return try {
            val content = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) { content.append(line).append("\n"); line = reader.readLine() }
                }
            }
            if (content.isBlank()) return "Файл пуст"
            importConfigsFromText(content.toString())
        } catch (e: Exception) { "Ошибка: ${e.localizedMessage}" }
    }

    /** Разобрать URI-ссылку VPN (vmess://, vless://, ss://, trojan://) */
    private fun parseVpnUriLink(uriString: String): FoxrayVpnConfig? {
        try {
            val protocol = uriString.substringBefore("://").uppercase()
            val afterProtocol = uriString.substringAfter("://")
            if (protocol == "VMESS") {
                val base64Body = afterProtocol.substringBefore("#")
                val decoded = try { String(android.util.Base64.decode(base64Body, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { base64Body }
                var jsonStr = decoded.trim()
                if (jsonStr.startsWith("{")) {
                    if (!jsonStr.endsWith("}")) jsonStr += "}"
                    val j = JSONObject(jsonStr)
                    return FoxrayVpnConfig(
                        id = "vmess_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                        name = j.optString("ps", "VMess Server"),
                        protocol = "VMESS", server = j.optString("add", "127.0.0.1"),
                        port = j.optInt("port", 80), uuidOrPassword = j.optString("id", "none"),
                        encryptionOrSecurity = j.optString("tls", "none"), rawContent = uriString
                    )
                }
            }
            val mainPart = afterProtocol.substringBefore("#")
            val rawName = afterProtocol.substringAfter("#", "")
            val name = try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }.ifEmpty { "$protocol Server [${configs.size + 1}]" }
            val authAndServer = mainPart.substringBefore("?")
            val uuid = try { URLDecoder.decode(authAndServer.substringBefore("@"), "UTF-8") } catch (_: Exception) { authAndServer.substringBefore("@") }
            val serverAndPort = authAndServer.substringAfter("@")
            val server = serverAndPort.substringBefore(":")
            val port = serverAndPort.substringAfter(":", "443").toIntOrNull() ?: 443
            val params = mainPart.substringAfter("?", "")
            val security = params.substringAfter("security=", "tls").substringBefore("&")
            return FoxrayVpnConfig(
                id = "link_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                name = name, protocol = protocol, server = server, port = port,
                uuidOrPassword = uuid, encryptionOrSecurity = security, rawContent = uriString
            )
        } catch (e: Exception) { Log.e("FoxrayVpnManager", "exception", e); return null }
    }

    /** Разобрать Xray JSON конфигурацию */
    private fun parseXrayJsonConfig(jsonString: String): FoxrayVpnConfig? {
        try {
            val root = JSONObject(jsonString)
            val outbounds = root.optJSONArray("outbounds") ?: return null
            for (i in 0 until outbounds.length()) {
                val item = outbounds.getJSONObject(i)
                val protocol = item.optString("protocol", "VMess")
                val settings = item.optJSONObject("settings")
                val vnext = settings?.optJSONArray("vnext")
                if (vnext != null && vnext.length() > 0) {
                    val so = vnext.getJSONObject(0)
                    val server = so.optString("address", "127.0.0.1")
                    val port = so.optInt("port", 443)
                    val uuid = so.optJSONArray("users")?.let { if (it.length() > 0) it.getJSONObject(0).optString("id", "none") else "none" } ?: "none"
                    val streamSettings = root.optJSONObject("streamSettings")
                    val security = streamSettings?.optString("security", "tls") ?: "tls"
                    return FoxrayVpnConfig(
                        id = "json_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                        name = "Foxray (JSON $protocol)", protocol = protocol.uppercase(),
                        server = server, port = port, uuidOrPassword = uuid,
                        encryptionOrSecurity = security, rawContent = jsonString
                    )
                }
            }
        } catch (e: Exception) { Log.e("FoxrayVpnManager", "exception", e) }
        return null
    }

    private fun isEmulatorOrVirtualDevice(): Boolean {
        val fp = android.os.Build.FINGERPRINT ?: ""
        val model = android.os.Build.MODEL ?: ""
        return fp.startsWith("generic") || fp.startsWith("unknown") ||
                model.contains("google_sdk") || model.contains("Emulator") ||
                model.contains("Android SDK built for x86") ||
                android.os.Build.MANUFACTURER?.contains("Genymotion") == true ||
                android.os.Build.HARDWARE?.contains("goldfish") == true ||
                android.os.Build.HARDWARE?.contains("ranchu") == true
    }
}
