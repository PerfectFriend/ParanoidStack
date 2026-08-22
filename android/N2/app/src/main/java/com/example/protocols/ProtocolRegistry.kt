package com.example.protocols

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ProtocolCategory {
    TRANSPORT,
    MESSAGING,
    STORAGE,
    MESH,
    APPLICATION,
    SECURITY
}

enum class ProtocolStatus {
    UNINSTALLED,
    INSTALLED,
    CONFIGURED,
    RUNNING,
    ERROR,
    BLOCKED
}

data class ConfigField(
    val key: String,
    val label: String,
    val type: FieldType,
    val defaultValue: String = "",
    val required: Boolean = false,
    val hint: String = "",
    val options: List<String> = emptyList()
)

enum class FieldType { STRING, INT, PORT, BOOLEAN, PASSWORD, SELECT, MULTILINE }

data class ProtocolInfo(
    val id: String,
    val name: String,
    val category: ProtocolCategory,
    val description: String,
    val version: String,
    val homepage: String,
    val license: String,
    val dependencies: List<String> = emptyList(),
    val configFields: List<ConfigField> = emptyList(),
    val isEmbedded: Boolean = false,
    val isNative: Boolean = false
)

data class ProtocolInstance(
    val info: ProtocolInfo,
    var status: ProtocolStatus = ProtocolStatus.UNINSTALLED,
    val config: MutableMap<String, String> = ConcurrentHashMap(),
    var process: Process? = null,
    var errorMessage: String = ""
)

class ProtocolRegistry(private val context: Context) {
    private val instances = ConcurrentHashMap<String, ProtocolInstance>()
    private val _registryFlow = MutableStateFlow<List<ProtocolInstance>>(emptyList())
    val registryFlow: StateFlow<List<ProtocolInstance>> = _registryFlow.asStateFlow()

    init { registerBuiltinProtocols() }

    private fun registerBuiltinProtocols() {
        // ===== TRANSPORT LAYER =====
        register(ProtocolInfo(
            "tor", "Tor", ProtocolCategory.TRANSPORT,
            "Onion routing — анонимная сеть с луковой маршрутизацией",
            "0.4.8", "https://torproject.org", "BSD-3",
            configFields = listOf(
                ConfigField("socks_port", "SOCKS5 Port", FieldType.PORT, "9050"),
                ConfigField("dns_port", "DNS Port", FieldType.PORT, "5353"),
                ConfigField("bridge_type", "Bridge Type", FieldType.SELECT, "obfs4",
                    options = listOf("obfs4", "meek_lite", "snowflake", "none")),
                ConfigField("use_bridges", "Use Bridges", FieldType.BOOLEAN, "true"),
                ConfigField("data_dir", "Data Directory", FieldType.STRING, "tor_data")
            ), isEmbedded = true
        ))
        register(ProtocolInfo(
            "v2ray", "Xray/V2Ray", ProtocolCategory.TRANSPORT,
            "Мульти-протокольный прокси (VMess, VLESS, Trojan, Shadowsocks)",
            "1.8.24", "https://xtls.github.io", "MPL-2",
            configFields = listOf(
                ConfigField("local_port", "Local SOCKS5 Port", FieldType.PORT, "10808"),
                ConfigField("tor_port", "Upstream Tor Port", FieldType.PORT, "9050"),
                ConfigField("protocol", "Outbound Protocol", FieldType.SELECT, "socks",
                    options = listOf("socks", "vmess", "vless", "trojan", "shadowsocks")),
                ConfigField("log_level", "Log Level", FieldType.SELECT, "warning",
                    options = listOf("debug", "info", "warning", "error", "none"))
            ), isEmbedded = true
        ))
        register(ProtocolInfo(
            "i2p", "I2P", ProtocolCategory.TRANSPORT,
            "Garlic routing — анонимная сеть с чесночной маршрутизацией",
            "2.7.0", "https://geti2p.net", "Apache-2.0",
            configFields = listOf(
                ConfigField("http_port", "HTTP Proxy Port", FieldType.PORT, "4444"),
                ConfigField("socks_port", "SOCKS Proxy Port", FieldType.PORT, "4447"),
                ConfigField("sam_port", "SAM API Port", FieldType.PORT, "7656")
            )
        ))
        register(ProtocolInfo(
            "wireguard", "WireGuard", ProtocolCategory.TRANSPORT,
            "Современный VPN-протокол с шумовой криптографией",
            "1.0", "https://wireguard.com", "GPL-2.0",
            configFields = listOf(
                ConfigField("endpoint", "Endpoint (host:port)", FieldType.STRING, ""),
                ConfigField("private_key", "Private Key", FieldType.PASSWORD, ""),
                ConfigField("public_key", "Peer Public Key", FieldType.PASSWORD, ""),
                ConfigField("allowed_ips", "Allowed IPs", FieldType.STRING, "0.0.0.0/0"),
                ConfigField("dns", "DNS", FieldType.STRING, "1.1.1.1")
            )
        ))
        register(ProtocolInfo(
            "shadowsocks", "Shadowsocks", ProtocolCategory.TRANSPORT,
            "Легковесный зашифрованный прокси",
            "2024", "https://shadowsocks.org", "MIT",
            configFields = listOf(
                ConfigField("server", "Server", FieldType.STRING, ""),
                ConfigField("server_port", "Server Port", FieldType.PORT, "8388"),
                ConfigField("password", "Password", FieldType.PASSWORD, ""),
                ConfigField("method", "Encryption Method", FieldType.SELECT, "aes-256-gcm",
                    options = listOf("aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305")),
                ConfigField("local_port", "Local Port", FieldType.PORT, "1080")
            )
        ))
        register(ProtocolInfo(
            "yggdrasil", "Yggdrasil", ProtocolCategory.TRANSPORT,
            "IPv6 mesh-сеть с end-to-end шифрованием",
            "0.5", "https://yggdrasil-network.github.io", "LGPL-3.0",
            configFields = listOf(
                ConfigField("listen", "Listen Address", FieldType.STRING, "tcp://0.0.0.0:1906"),
                ConfigField("peers", "Peers (comma-separated)", FieldType.MULTILINE, ""),
                ConfigField("ifname", "TUN Interface Name", FieldType.STRING, "ygg0")
            )
        ))
        register(ProtocolInfo(
            "cjdns", "cjdns", ProtocolCategory.TRANSPORT,
            "Зашифрованная IPv6 mesh-сеть",
            "22", "https://github.com/cjdelisle/cjdns", "GPL-3.0",
            configFields = listOf(
                ConfigField("udp_port", "UDP Port", FieldType.PORT, "41920"),
                ConfigField("peers", "Peers (JSON)", FieldType.MULTILINE, ""),
                ConfigField("password", "Private Key (auto-generated)", FieldType.PASSWORD, "")
            )
        ))

        // ===== MESSAGING LAYER =====
        register(ProtocolInfo(
            "simplex", "SimpleX", ProtocolCategory.MESSAGING,
            "Децентрализованный мессенджер без ID (SMP+queues)",
            "6.2", "https://simplex.chat", "AGPL-3",
            configFields = listOf(
                ConfigField("socks_port", "Socks Port", FieldType.PORT, "10808"),
                ConfigField("user_handle", "User Handle", FieldType.STRING, ""),
                ConfigField("smp_servers", "SMP Servers (JSON)", FieldType.MULTILINE, ""),
                ConfigField("xftp_servers", "XFTP Servers (JSON)", FieldType.MULTILINE, "")
            ), isEmbedded = true
        ))
        register(ProtocolInfo(
            "matrix", "Matrix", ProtocolCategory.MESSAGING,
            "Открытый децентрализованный протокол обмена сообщениями",
            "1.11", "https://matrix.org", "Apache-2.0",
            configFields = listOf(
                ConfigField("homeserver", "Homeserver URL", FieldType.STRING, "https://matrix.org"),
                ConfigField("username", "Username", FieldType.STRING, ""),
                ConfigField("password", "Password", FieldType.PASSWORD, ""),
                ConfigField("device_name", "Device Name", FieldType.STRING, "N2-Node")
            )
        ))
        register(ProtocolInfo(
            "tox", "Tox", ProtocolCategory.MESSAGING,
            "P2P мессенджер с end-to-end шифрованием",
            "0.2", "https://tox.chat", "GPL-3.0",
            configFields = listOf(
                ConfigField("udp_port", "UDP Port", FieldType.PORT, "33445"),
                ConfigField("tcp_port", "TCP Relay Port", FieldType.PORT, "3389"),
                ConfigField("enable_udp", "Enable UDP", FieldType.BOOLEAN, "true"),
                ConfigField("proxy_host", "Proxy Host", FieldType.STRING, "127.0.0.1"),
                ConfigField("proxy_port", "Proxy Port", FieldType.PORT, "9050")
            )
        ))
        register(ProtocolInfo(
            "session", "Session (Oxen)", ProtocolCategory.MESSAGING,
            "Децентрализованный мессенджер на LokiNet",
            "1.14", "https://getsession.org", "GPL-3.0",
            configFields = listOf(
                ConfigField("socks_port", "SOCKS5 Port", FieldType.PORT, "9050"),
                ConfigField("storage_path", "Storage Path", FieldType.STRING, "session_data")
            )
        ))
        register(ProtocolInfo(
            "briar", "Briar", ProtocolCategory.MESSAGING,
            "P2P мессенджер через Tor/Bluetooth/WiFi Direct",
            "1.5", "https://briarproject.org", "GPL-3.0",
            configFields = listOf(
                ConfigField("tor_port", "Tor SOCKS Port", FieldType.PORT, "9050"),
                ConfigField("data_dir", "Data Directory", FieldType.STRING, "briar_data")
            )
        ))

        // ===== STORAGE LAYER =====
        register(ProtocolInfo(
            "ipfs", "IPFS", ProtocolCategory.STORAGE,
            "Межпланетная файловая система — P2P гипермедиа",
            "0.32", "https://ipfs.tech", "MIT/Apache-2.0",
            configFields = listOf(
                ConfigField("api_port", "API Port", FieldType.PORT, "5001"),
                ConfigField("gateway_port", "Gateway Port", FieldType.PORT, "8080"),
                ConfigField("swarm_port", "Swarm Port", FieldType.PORT, "4001"),
                ConfigField("data_dir", "Data Directory", FieldType.STRING, "ipfs_data"),
                ConfigField("storage_max", "Max Storage (GB)", FieldType.STRING, "10")
            )
        ))
        register(ProtocolInfo(
            "bittorrent", "BitTorrent (DHT)", ProtocolCategory.STORAGE,
            "P2P протокол обмена файлами с распределённым хеш-таблицей",
            "2.0", "https://bittorrent.org", "Public Domain",
            configFields = listOf(
                ConfigField("dht_port", "DHT Port", FieldType.PORT, "6881"),
                ConfigField("data_dir", "Download Directory", FieldType.STRING, "torrent_data"),
                ConfigField("max_upload", "Max Upload Speed (KB/s)", FieldType.STRING, "1024"),
                ConfigField("max_download", "Max Download Speed (KB/s)", FieldType.STRING, "5120")
            )
        ))
        register(ProtocolInfo(
            "hypercore", "Hypercore (Dat)", ProtocolCategory.STORAGE,
            "P2P append-only log для синхронизации данных",
            "11.0", "https://hypercore-protocol.org", "MIT",
            configFields = listOf(
                ConfigField("port", "Port", FieldType.PORT, "49737"),
                ConfigField("data_dir", "Data Directory", FieldType.STRING, "hyper_data"),
                ConfigField("storage_limit", "Storage Limit (MB)", FieldType.STRING, "1024")
            )
        ))
        register(ProtocolInfo(
            "archive_cloud", "Archive Cloud", ProtocolCategory.STORAGE,
            "Децентрализованное облако на дисках пиров (IPFS+torrent)",
            "1.0", "", "AGPL-3.0",
            configFields = listOf(
                ConfigField("storage_path", "Storage Path", FieldType.STRING, "archive_data"),
                ConfigField("max_space_gb", "Max Space (GB)", FieldType.STRING, "20"),
                ConfigField("replication", "Replication Factor", FieldType.STRING, "3"),
                ConfigField("public_host", "Public Host (optional)", FieldType.STRING, ""),
                ConfigField("public_port", "Public Port", FieldType.PORT, "9001"),
                ConfigField("enable_pinning", "Enable Pinning Service", FieldType.BOOLEAN, "true")
            ), isEmbedded = true
        ))

        // ===== MESH / DISCOVERY =====
        register(ProtocolInfo(
            "libp2p", "libp2p", ProtocolCategory.MESH,
            "Модульный сетевой стек для P2P-приложений",
            "0.52", "https://libp2p.io", "MIT/Apache-2.0",
            configFields = listOf(
                ConfigField("listen_port", "Listen Port", FieldType.PORT, "9000"),
                ConfigField("bootstrap_peers", "Bootstrap Peers", FieldType.MULTILINE, ""),
                ConfigField("enable_dht", "Enable DHT", FieldType.BOOLEAN, "true"),
                ConfigField("enable_relay", "Enable Relay", FieldType.BOOLEAN, "true")
            )
        ))
        register(ProtocolInfo(
            "kademlia", "Kademlia DHT", ProtocolCategory.MESH,
            "Распределённая хеш-таблица для поиска пиров",
            "1.0", "", "MIT",
            configFields = listOf(
                ConfigField("port", "UDP Port", FieldType.PORT, "9002"),
                ConfigField("bootstrap_nodes", "Bootstrap Nodes", FieldType.MULTILINE, "")
            ), isEmbedded = true
        ))

        // ===== APPLICATION LAYER =====
        register(ProtocolInfo(
            "matrix_voip", "Matrix VoIP (сall)", ProtocolCategory.APPLICATION,
            "Голосовые/видео звонки через Matrix+WebRTC",
            "1.0", "https://matrix.org", "Apache-2.0",
            dependencies = listOf("matrix"),
            configFields = listOf(
                ConfigField("turn_server", "TURN Server", FieldType.STRING, ""),
                ConfigField("turn_user", "TURN Username", FieldType.STRING, ""),
                ConfigField("turn_pass", "TURN Password", FieldType.PASSWORD, ""),
                ConfigField("stun_server", "STUN Server", FieldType.STRING, "stun.l.google.com:19302")
            )
        ))
        register(ProtocolInfo(
            "nostr", "Nostr", ProtocolCategory.MESSAGING,
            "Открытый протокол для социальных сетей (Notes)",
            "1.0", "https://nostr.com", "Public Domain",
            configFields = listOf(
                ConfigField("relays", "Relays (comma-separated)", FieldType.MULTILINE,
                    "wss://relay.damus.io,wss://nos.lol,wss://relay.snort.social"),
                ConfigField("private_key", "Private Key (hex)", FieldType.PASSWORD, ""),
                ConfigField("proxy", "SOCKS5 Proxy", FieldType.STRING, "127.0.0.1:9050")
            )
        ))

        Log.i("ProtocolRegistry", "Registered ${instances.size} protocols")
    }

    private fun register(info: ProtocolInfo) {
        instances[info.id] = ProtocolInstance(
            info = info,
            status = ProtocolStatus.INSTALLED.takeIf { info.isEmbedded } ?: ProtocolStatus.UNINSTALLED
        )
        _registryFlow.value = instances.values.toList()
    }

    fun getAll(): List<ProtocolInstance> = instances.values.toList()
    fun get(id: String): ProtocolInstance? = instances[id]
    fun getByCategory(cat: ProtocolCategory): List<ProtocolInstance> =
        instances.values.filter { it.info.category == cat }

    fun getRunning(): List<ProtocolInstance> =
        instances.values.filter { it.status == ProtocolStatus.RUNNING }

    fun updateStatus(id: String, status: ProtocolStatus, error: String = "") {
        instances[id]?.let {
            it.status = status; it.errorMessage = error
            _registryFlow.value = instances.values.toList()
        }
    }

    fun updateConfig(id: String, key: String, value: String) {
        instances[id]?.config?.put(key, value)
    }

    fun updateConfigBatch(id: String, config: Map<String, String>) {
        instances[id]?.config?.putAll(config)
    }

    fun getConfigSnapshot(id: String): Map<String, String> =
        instances[id]?.config?.toMap() ?: emptyMap()

    fun getActiveTransport(): List<String> {
        val transportOrder = listOf("yggdrasil", "cjdns", "wireguard", "shadowsocks", "tor", "v2ray", "i2p")
        return transportOrder.filter { instances[it]?.status == ProtocolStatus.RUNNING }
    }

    fun suggestOptimalTransportLayer(): String {
        val runningTransport = getActiveTransport()
        if (runningTransport.isNotEmpty()) return runningTransport.first()
        val order = listOf(
            "tor" to 80, "v2ray" to 75, "shadowsocks" to 60,
            "yggdrasil" to 50, "wireguard" to 40, "i2p" to 30, "cjdns" to 20
        )
        return order.maxByOrNull { it.second }?.first ?: "tor"
    }

    fun dispose() {
        instances.values.forEach { it.process?.destroy() }
        instances.clear()
    }
}
