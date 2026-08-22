package com.paranoidx.sdk.transport

import com.paranoidx.sdk.security.SdkLogger
import com.paranoidx.sdk.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

// Local definitions to avoid dependency on NetworkDiagnostics
data class NetworkCondition(
    val isOnline: Boolean = true,
    val connectionType: String = "wifi",
    val rttMs: Long = 0,
    val packetLossPercent: Float = 0f,
    val censorshipDetected: Boolean = false,
    val censorshipScore: Int = 0,
    val isBehindDPI: Boolean = false,
    val dnsIsBlocked: Boolean = false,
    val tlsIsBlocked: Boolean = false,
    val udpIsBlocked: Boolean = false,
    val isInternetReachable: Boolean = true,
    val blockedPorts: List<Int> = emptyList(),
    val metered: Boolean = false,
    val rawDiagnosis: String = ""
)

enum class BypassMethod(val description: String) {
    FRAGMENTATION("IP Packet Fragmentation"),
    DNS_TUNNEL("DNS-over-HTTPS Tunnel"),
    HTTP_WS_UPGRADE("HTTP WebSocket Upgrade"),
    DOMAIN_FRONTING("Domain Fronting"),
    TOR("Tor Onion Routing")
}

data class TransportStack(
    val name: String,
    val layers: List<String>,
    val estimatedLatencyMs: Long = 0,
    val censorshipResilience: Int = 0,
    val throughputScore: Int = 0,
    val isActive: Boolean = false
)

data class TransportSelection(
    val transportStack: TransportStack,
    val bypassMethod: BypassMethod? = null,
    val providesAnonymity: Boolean = false,
    val providesObfuscation: Boolean = false,
    val providesE2E: Boolean = false,
    val configHints: Map<String, String> = emptyMap()
)

object TransportSelector {
    private const val TAG = "TransportSelector"

    private val _currentStack = MutableStateFlow<TransportSelection?>(null)
    val currentStack: StateFlow<TransportSelection?> = _currentStack.asStateFlow()

    private val _availableStacks = MutableStateFlow<List<TransportStack>>(emptyList())
    val availableStacks: StateFlow<List<TransportStack>> = _availableStacks.asStateFlow()

    private val predefinedStacks = listOf(
        TransportStack(
            name = "direct_tls",
            layers = listOf("tls1.3", "x25519"),
            estimatedLatencyMs = 50, censorshipResilience = 20, throughputScore = 90
        ),
        TransportStack(
            name = "tls_obfuscated",
            layers = listOf("tls1.3", "padding", "domain_fronting", "x25519"),
            estimatedLatencyMs = 100, censorshipResilience = 50, throughputScore = 70
        ),
        TransportStack(
            name = "tor_onion",
            layers = listOf("tor", "tls1.3", "padding", "x25519"),
            estimatedLatencyMs = 500, censorshipResilience = 85, throughputScore = 30
        ),
        TransportStack(
            name = "i2p_garlic",
            layers = listOf("i2p", "ntcp2", "padding", "x25519"),
            estimatedLatencyMs = 800, censorshipResilience = 90, throughputScore = 20
        ),
        TransportStack(
            name = "v2ray_chain",
            layers = listOf("v2ray_vmess", "websocket", "tls1.3", "padding", "domain_fronting", "x25519"),
            estimatedLatencyMs = 300, censorshipResilience = 75, throughputScore = 50
        ),
        TransportStack(
            name = "multi_layer",
            layers = listOf("v2ray_vmess", "websocket", "tls1.3", "tor", "padding", "domain_fronting", "x25519"),
            estimatedLatencyMs = 1500, censorshipResilience = 95, throughputScore = 10
        ),
        TransportStack(
            name = "yggdrasil_mesh",
            layers = listOf("yggdrasil", "noise_xk", "x25519"),
            estimatedLatencyMs = 200, censorshipResilience = 70, throughputScore = 60
        ),
        TransportStack(
            name = "libp2p_autonat",
            layers = listOf("libp2p", "noise", "yamux", "x25519"),
            estimatedLatencyMs = 150, censorshipResilience = 60, throughputScore = 65
        ),
        TransportStack(
            name = "ssb_gossip",
            layers = listOf("ssb", "noise", "tls1.3", "x25519"),
            estimatedLatencyMs = 400, censorshipResilience = 75, throughputScore = 25
        ),
        TransportStack(
            name = "ws_upgrade",
            layers = listOf("http_ws", "tls1.3", "padding", "x25519"),
            estimatedLatencyMs = 120, censorshipResilience = 55, throughputScore = 60
        ),
        TransportStack(
            name = "fragmented",
            layers = listOf("ip_fragmentation", "tls1.3", "padding", "x25519"),
            estimatedLatencyMs = 200, censorshipResilience = 65, throughputScore = 40
        ),
        TransportStack(
            name = "dns_tunnel",
            layers = listOf("dns53", "doh", "tls1.3", "x25519"),
            estimatedLatencyMs = 1000, censorshipResilience = 80, throughputScore = 5
        )
    )

    suspend fun selectOptimalStack(condition: NetworkCondition): TransportSelection = withContext(Dispatchers.Default) {
        SdkLogger.i(TAG, "Selecting optimal transport for censorshipScore=${condition.censorshipScore}")

        val scored = predefinedStacks.map { stack ->
            val score = scoreStack(stack, condition)
            stack to score
        }.sortedByDescending { it.second }

        _availableStacks.value = scored.map { it.first }

        val bestStack = scored.firstOrNull()?.first ?: predefinedStacks.first()
        val bypassMethod = selectBypassMethod(condition, bestStack)
        val providesAnonymity = bestStack.layers.any { it in listOf("tor", "i2p", "multi_layer") }
        val providesObfuscation = bestStack.layers.any { it in listOf("padding", "domain_fronting", "fragmentation") }

        val selection = TransportSelection(
            transportStack = bestStack,
            bypassMethod = bypassMethod,
            providesAnonymity = providesAnonymity,
            providesObfuscation = providesObfuscation,
            providesE2E = bestStack.layers.contains("x25519"),
            configHints = buildConfigHints(bestStack, condition)
        )

        _currentStack.value = selection
        SdkLogger.i(TAG, "Selected stack: ${bestStack.name} (score=${scored.firstOrNull()?.second ?: 0})")
        selection
    }

    private fun scoreStack(stack: TransportStack, condition: NetworkCondition): Int {
        var score = 0

        val censored = condition.isBehindDPI || condition.dnsIsBlocked || condition.tlsIsBlocked
        when {
            condition.censorshipScore < 10 -> {
                if (stack.name == "direct_tls") score += 100
                if (stack.estimatedLatencyMs < 100) score += 20
            }
            condition.censorshipScore < 30 -> {
                if (stack.name == "tls_obfuscated") score += 90
                score += stack.censorshipResilience * 2
                score += stack.throughputScore
                score -= (stack.estimatedLatencyMs / 10).toInt()
            }
            condition.censorshipScore < 50 -> {
                if (stack.name == "v2ray_chain" || stack.name == "ws_upgrade" || stack.name == "ssb_gossip") score += 80
                score += stack.censorshipResilience * 3
                score += stack.throughputScore / 2
                score -= (stack.estimatedLatencyMs / 20).toInt()
            }
            condition.censorshipScore < 70 -> {
                if (stack.name == "tor_onion") score += 85
                score += stack.censorshipResilience * 4
                score -= (stack.estimatedLatencyMs / 30).toInt()
            }
            else -> {
                if (stack.name == "multi_layer" || stack.name == "i2p_garlic") score += 100
                if (stack.name == "dns_tunnel") score += 60
                score += stack.censorshipResilience * 5
            }
        }

        if (condition.udpIsBlocked && stack.layers.any { it in listOf("quic", "i2p", "yggdrasil") }) score -= 50
        if (condition.tlsIsBlocked && stack.layers.contains("tls1.3")) score -= 40
        if (condition.isInternetReachable && stack.name == "dns_tunnel") score -= 30

        return score.coerceIn(0, 500)
    }

    private fun selectBypassMethod(condition: NetworkCondition, stack: TransportStack): BypassMethod? {
        return when {
            condition.isBehindDPI -> BypassMethod.FRAGMENTATION
            condition.dnsIsBlocked -> BypassMethod.DNS_TUNNEL
            condition.tlsIsBlocked -> BypassMethod.HTTP_WS_UPGRADE
            condition.blockedPorts.contains(443) -> BypassMethod.DOMAIN_FRONTING
            condition.censorshipScore > 70 -> BypassMethod.TOR
            else -> null
        }
    }

    private fun buildConfigHints(stack: TransportStack, condition: NetworkCondition): Map<String, String> {
        val hints = mutableMapOf<String, String>()
        when (stack.name) {
            "tls_obfuscated" -> {
                hints["tls_sni"] = if (condition.censorshipScore > 30) "www.cloudflare.com" else "www.google.com"
                hints["padding_min"] = "32"
                hints["padding_max"] = "1024"
            }
            "tor_onion" -> hints["tor_bridges"] = if (condition.censorshipScore > 80) "obfs4" else "builtin"
            "multi_layer" -> {
                hints["v2ray_outbound"] = "tor"
                hints["tls_sni"] = "www.cloudflare.com"
            }
            "v2ray_chain" -> hints["v2ray_protocol"] = "vmess"
            "libp2p_autonat" -> hints["nat_traversal"] = "autonat"
        }
        return hints
    }
}
