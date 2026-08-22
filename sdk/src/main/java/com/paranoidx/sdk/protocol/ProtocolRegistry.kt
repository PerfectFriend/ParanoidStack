package com.paranoidx.sdk.protocol

import com.paranoidx.sdk.security.SdkLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object ProtocolRegistry {
    private const val TAG = "ProtocolRegistry"

    private val protocols = ConcurrentHashMap<String, ConfigurableProtocol>()
    private val _allStatuses = MutableStateFlow<Map<String, ProtocolStatus>>(emptyMap())
    val allStatuses: StateFlow<Map<String, ProtocolStatus>> = _allStatuses.asStateFlow()

    private val _activeProtocolCount = MutableStateFlow(0)
    val activeProtocolCount: StateFlow<Int> = _activeProtocolCount.asStateFlow()

    fun register(protocol: ConfigurableProtocol) {
        protocols[protocol.id] = protocol
        SdkLogger.i(TAG, "Registered protocol: ${protocol.id}")
        refreshStatus()
    }

    fun get(id: String): ConfigurableProtocol? = protocols[id]

    fun getAll(): List<ConfigurableProtocol> = protocols.values.toList()

    fun getByCapability(predicate: (ProtocolCapability) -> Boolean): List<ConfigurableProtocol> =
        protocols.values.filter { predicate(it.getCapabilities()) }

    fun getTransportProtocols(): List<PluggableTransport> =
        protocols.values.filterIsInstance<PluggableTransport>()

    fun getMessagingProtocols(): List<PluggableMessagingProtocol> =
        protocols.values.filterIsInstance<PluggableMessagingProtocol>()

    fun getStorageProtocols(): List<DecentralizedStorageProtocol> =
        protocols.values.filterIsInstance<DecentralizedStorageProtocol>()

    fun getBypassProtocols(): List<CensorshipBypassProtocol> =
        protocols.values.filterIsInstance<CensorshipBypassProtocol>()

    fun unregister(id: String) {
        protocols.remove(id)
        refreshStatus()
    }

    fun refreshStatus() {
        val statuses = protocols.mapValues { it.value.status.value }
        _allStatuses.value = statuses
        _activeProtocolCount.value = statuses.count { it.value.state == ProtocolState.RUNNING }
    }

    fun clear() {
        protocols.clear()
        _allStatuses.value = emptyMap()
        _activeProtocolCount.value = 0
    }

    fun getDependencyOrder(): List<ConfigurableProtocol> {
        val sorted = mutableListOf<ConfigurableProtocol>()
        val visited = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in visited) return
            visited.add(id)
            val proto = protocols[id] ?: return
            for (depId in proto.config.value.dependencies) {
                visit(depId)
            }
            sorted.add(proto)
        }

        protocols.keys.sorted().forEach { visit(it) }
        return sorted
    }
}
