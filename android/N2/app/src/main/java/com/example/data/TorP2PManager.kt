/**
 * P2P-менеджер для связи через Tor скрытые сервисы (.onion).
 * Позволяет создавать локальные серверы, доступные как .onion адреса,
 * и подключаться к удалённым .onion хостам через SOCKS5 прокси Tor.
 *
 * Поддерживает множественные одновременные подключения и скрытые сервисы.
 */
package com.example.data

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** Конфигурация скрытого сервиса Tor (.onion) */
data class HiddenServiceConfig(
    val localPort: Int,         // локальный порт для входящих соединений
    val targetPort: Int,        // целевой порт (перенаправление Tor)
    val hiddenServiceDir: File? = null  // директория скрытого сервиса
) {
    /** Получить .onion адрес из файла hostname */
    val onionAddress: String?
        get() {
            val dir = hiddenServiceDir ?: return null
            val hostnameFile = File(dir, "hostname")
            return if (hostnameFile.exists()) {
                hostnameFile.readText().trim()
            } else null
        }
}

/** Менеджер P2P-соединений через Tor */
class TorP2PManager(
    private val socksHost: String = "127.0.0.1",   // хост Tor SOCKS5
    private val socksPort: Int = 9050,              // порт Tor SOCKS5
    private val onLog: (String) -> Unit,             // callback логирования
    private val onConnectionStateChanged: (Boolean) -> Unit,  // статус соединения
    private val onMessageReceived: (String) -> Unit,  // получено сообщение
    private val baseDir: File = File(".")            // базовая директория для скрытых сервисов
) {
    private val services = mutableListOf<HiddenServiceConfig>()
    private val serverSockets = ConcurrentHashMap<Int, ServerSocket>()
    private val activeSockets = ConcurrentHashMap<String, Socket>()
    private val readers = ConcurrentHashMap<String, BufferedReader>()
    private val writers = ConcurrentHashMap<String, BufferedWriter>()
    private val runningServers = ConcurrentHashMap<Int, Boolean>()
    private var isRunning = false
    private var keepAliveThread: Thread? = null

    /**
     * Зарегистрировать скрытый сервис.
     * @param port локальный порт
     * @param targetPort целевой порт
     * @param hsDir директория скрытого сервиса (null = авто)
     * @return true если успешно
     */
    fun addHiddenService(port: Int, targetPort: Int, hsDir: File? = null): Boolean {
        synchronized(services) {
            if (services.any { it.localPort == port }) {
                onLog("[TorP2P] Hidden service on port $port already registered")
                return false
            }
            val dir = hsDir ?: File(baseDir, "tor_hs/service_$port")
            val config = HiddenServiceConfig(port, targetPort, dir)
            services.add(config)
            onLog("[TorP2P] Registered hidden service: localPort=$port, targetPort=$targetPort, dir=${dir.absolutePath}")
            dir.mkdirs()
            return true
        }
    }

    /** Удалить скрытый сервис */
    fun removeHiddenService(port: Int): Boolean {
        synchronized(services) {
            val config = services.find { it.localPort == port } ?: return false
            stopHostServer(port)
            services.remove(config)
            onLog("[TorP2P] Removed hidden service on port $port")
            return true
        }
    }

    /** Получить все .onion адреса */
    fun getOnionAddresses(): List<String> {
        return synchronized(services) {
            services.mapNotNull { it.onionAddress }
        }
    }

    /** Получить конфигурации скрытых сервисов */
    fun getHiddenServiceConfigs(): List<HiddenServiceConfig> {
        return synchronized(services) {
            services.toList()
        }
    }

    /** Запустить все серверы скрытых сервисов */
    fun startAllServers() {
        synchronized(services) {
            for (config in services) {
                startHostServer(config.localPort)
            }
        }
    }

    /**
     * Запустить локальный сервер для приёма .onion-соединений.
     * @param localPort порт для прослушивания
     */
    fun startHostServer(localPort: Int = 8080) {
        if (runningServers.getOrDefault(localPort, false)) {
            onLog("[TorP2P] Server on port $localPort is already running.")
            return
        }

        isRunning = true
        runningServers[localPort] = true
        onLog("[TorP2P] Starting local Onion Service server on port $localPort...")

        thread(name = "TorP2PServer-$localPort") {
            try {
                val serverSocket = ServerSocket(localPort)
                serverSockets[localPort] = serverSocket
                onLog("[TorP2P] Server socket on port $localPort listening for Tor Onion connections...")

                while (runningServers.getOrDefault(localPort, false)) {
                    val socket = serverSocket.accept()
                    val remoteAddr = socket.inetAddress.hostAddress
                    onLog("[TorP2P] Remote peer connected on port $localPort from $remoteAddr!")
                    handleConnectedSocket(socket, "incoming-$localPort-${System.currentTimeMillis()}")
                }
            } catch (e: Exception) {
                if (runningServers.getOrDefault(localPort, false)) {
                    onLog("[TorP2P] Server error on port $localPort: ${e.message}")
                }
            } finally {
                cleanupServer(localPort)
            }
        }
    }

    /** Остановить локальный сервер */
    fun stopHostServer(localPort: Int) {
        runningServers[localPort] = false
        try {
            serverSockets[localPort]?.close()
        } catch (_: java.lang.Exception) { Log.w("TorP2PManager", "ignored exception") }
        serverSockets.remove(localPort)
        onLog("[TorP2P] Stopped server on port $localPort")
    }

    /**
     * Подключиться к удалённому .onion хосту.
     * @param onionAddress .onion адрес
     * @param targetPort целевой порт
     */
    fun connectToRemoteHost(onionAddress: String, targetPort: Int = 8080) {
        if (!isRunning) {
            isRunning = true
        }
        val connectionId = "outgoing-$onionAddress-$targetPort-${System.currentTimeMillis()}"
        onLog("[TorP2P] Connecting to $onionAddress:$targetPort through Tor SOCKS5 proxy on $socksHost:$socksPort...")

        thread(name = "TorP2PConnect-$connectionId") {
            try {
                val proxyClient = TorProxyClient(socksHost, socksPort)
                val socket = proxyClient.connectThroughTor(onionAddress, targetPort)
                onLog("[TorP2P] Connected to $onionAddress:$targetPort!")
                handleConnectedSocket(socket, connectionId)
            } catch (e: Exception) {
                onLog("[TorP2P] Connection to $onionAddress:$targetPort failed: ${e.message}")
                onConnectionStateChanged(false)
            }
        }
    }

    /**
     * Отправить сообщение всем подключённым пирам.
     * @param payload текст сообщения
     * @return true если хотя бы одно сообщение отправлено
     */
    fun sendMessage(payload: String): Boolean {
        val snapshot = HashMap(writers)
        if (snapshot.isEmpty()) {
            onLog("[TorP2P] Error: Cannot send message, no active peer connections.")
            return false
        }
        val latch = CountDownLatch(snapshot.size)
        var anySent = false
        for ((id, writer) in snapshot) {
            thread(name = "TorP2PWriter-$id") {
                try {
                    synchronized(writer) {
                        writer.write(payload)
                        writer.newLine()
                        writer.flush()
                    }
                    Log.d("TorP2P", "Sent packet to $id: $payload")
                    anySent = true
                } catch (e: Exception) {
                    onLog("[TorP2P] Error sending packet to $id: ${e.message}")
                    disconnectConnection(id)
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        return anySent
    }

    /** Запустить keep-alive, отправляющий PING всем подключённым пирам каждые 30 секунд */
    fun startKeepAlive(intervalMs: Long = 30000) {
        stopKeepAlive()
        keepAliveThread = thread(name = "TorP2PKeepAlive", isDaemon = true) {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(intervalMs)
                    val snapshot = HashMap(writers)
                    for ((id, writer) in snapshot) {
                        try {
                            synchronized(writer) {
                                writer.write("PING")
                                writer.newLine()
                                writer.flush()
                            }
                        } catch (e: Exception) {
                            onLog("[TorP2P] KeepAlive error for $id: ${e.message}")
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    onLog("[TorP2P] KeepAlive error: ${e.message}")
                }
            }
        }
    }

    /** Остановить keep-alive поток */
    fun stopKeepAlive() {
        keepAliveThread?.interrupt()
        keepAliveThread = null
    }

    /** Отключить все соединения */
    fun disconnect() {
        stopKeepAlive()
        onLog("[TorP2P] Disconnecting all peer sessions...")
        isRunning = false
        synchronized(services) {
            for (config in services) {
                stopHostServer(config.localPort)
            }
        }
        val connectionIds = activeSockets.keys.toList()
        for (id in connectionIds) {
            disconnectConnection(id)
        }
        services.clear()
    }

    /** Отключить конкретное соединение */
    fun disconnectConnection(connectionId: String) {
        try { activeSockets[connectionId]?.close() } catch (_: java.lang.Exception) { Log.w("TorP2PManager", "ignored exception") }
        try { readers[connectionId]?.close() } catch (_: java.lang.Exception) { Log.w("TorP2PManager", "ignored exception") }
        try { writers[connectionId]?.close() } catch (_: java.lang.Exception) { Log.w("TorP2PManager", "ignored exception") }
        activeSockets.remove(connectionId)
        readers.remove(connectionId)
        writers.remove(connectionId)
    }

    /** Обработать новое соединение: создать потоки чтения и записи */
    private fun handleConnectedSocket(socket: Socket, connectionId: String) {
        activeSockets[connectionId] = socket
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        readers[connectionId] = reader
        writers[connectionId] = writer
        onConnectionStateChanged(true)

        // Spawn a dedicated reader thread that blocks on readLine()
        // Each peer connection gets its own thread identified by connectionId
        thread(name = "TorP2PReader-$connectionId") {
            try {
                var line = reader.readLine()
                while (line != null) {
                    val received = line.trim()
                    if (received.isNotEmpty()) {
                        Log.d("TorP2P", "Received from $connectionId: $received")
                        onMessageReceived(received)
                    }
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                onLog("[TorP2P] Connection $connectionId lost: ${e.message}")
            } finally {
                onLog("[TorP2P] Peer connection $connectionId closed.")
                disconnectConnection(connectionId)
                // Notify listeners when the last active connection drops
                if (activeSockets.isEmpty()) {
                    onConnectionStateChanged(false)
                }
            }
        }
    }

    /** Очистить ресурсы сервера */
    private fun cleanupServer(port: Int) {
        runningServers.remove(port)
        try { serverSockets[port]?.close() } catch (_: java.lang.Exception) { Log.w("TorP2PManager", "ignored exception") }
        serverSockets.remove(port)
        if (runningServers.isEmpty()) {
            isRunning = false
        }
    }

    /** Проверить, запущен ли какой-либо сервер */
    fun isAnyServerRunning(): Boolean {
        return runningServers.isNotEmpty()
    }

    /** Получить количество активных соединений */
    fun getActiveConnectionCount(): Int {
        return activeSockets.size
    }
}
