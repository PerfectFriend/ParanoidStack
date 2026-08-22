package com.paranoidx.sdk.protocol

import kotlinx.coroutines.flow.StateFlow

data class ProtocolCapability(
    val name: String,
    val providesTransport: Boolean = false,
    val providesMessaging: Boolean = false,
    val providesStorage: Boolean = false,
    val providesE2E: Boolean = false,
    val providesBypass: Boolean = false,
    val requiresTor: Boolean = false,
    val requiresI2P: Boolean = false,
    val requiresDns: Boolean = false,
    val latencySensitive: Boolean = false,
    val bandwidthHeavy: Boolean = false
)

data class ProtocolConfig(
    val id: String,
    val displayName: String,
    val version: String,
    val enabled: Boolean = false,
    val autoStart: Boolean = false,
    val priority: Int = 100,
    val capabilities: ProtocolCapability = ProtocolCapability(name = id),
    val config: Map<String, String> = emptyMap(),
    val dependencies: List<String> = emptyList()
)

data class ProtocolStatus(
    val id: String,
    val state: ProtocolState,
    val uptimeMs: Long = 0,
    val lastError: String? = null,
    val latencyMs: Long = 0,
    val throughputBps: Long = 0,
    val peerCount: Int = 0
)

enum class ProtocolState {
    UNINITIALIZED, INITIALIZING, RUNNING, DEGRADED, FAILED, STOPPED
}

interface ConfigurableProtocol {
    val id: String
    val displayName: String
    val status: StateFlow<ProtocolStatus>
    val config: StateFlow<ProtocolConfig>

    fun configure(newConfig: ProtocolConfig)
    suspend fun start(): Boolean
    suspend fun stop()
    suspend fun healthCheck(): Boolean
    fun getCapabilities(): ProtocolCapability
}

interface PluggableTransport : ConfigurableProtocol {
    suspend fun openConnection(target: String, port: Int): java.io.InputStream
    suspend fun wrapStream(input: java.io.InputStream, output: java.io.OutputStream): Pair<java.io.InputStream, java.io.OutputStream>
    val estimatedLatencyMs: Long
}

interface PluggableMessagingProtocol : ConfigurableProtocol {
    suspend fun sendMessage(recipient: String, data: ByteArray): Boolean
    suspend fun subscribe(channel: String): kotlinx.coroutines.flow.Flow<ByteArray>
    suspend fun createChannel(config: Map<String, String>): String?
}

interface DecentralizedStorageProtocol : ConfigurableProtocol {
    suspend fun store(data: ByteArray, metadata: Map<String, String> = emptyMap()): String?
    suspend fun retrieve(id: String): ByteArray?
    suspend fun delete(id: String): Boolean
    suspend fun list(): List<String>
}

interface CensorshipBypassProtocol : ConfigurableProtocol {
    suspend fun checkAccessibility(host: String, port: Int): BypassResult
    suspend fun establishBypass(target: String, port: Int): java.io.InputStream?
}

enum class BypassMethod {
    TLS_WRAP, DOMAIN_FRONTING, TOR, I2P, VPN, SSH_TUNNEL, DNS_TUNNEL, HTTP_WS_UPGRADE, SNI_SPOOF, FRAGMENTATION
}

data class BypassResult(
    val success: Boolean,
    val method: BypassMethod,
    val latencyMs: Long = 0,
    val effectiveHost: String = "",
    val effectivePort: Int = 0,
    val errorMessage: String = ""
)
