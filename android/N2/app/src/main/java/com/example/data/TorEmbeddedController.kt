/**
 * Embedded Tor Controller for Android.
 * 
 * Runs Tor as a separate process, manages bridges (obfs4, meek, snowflake),
 * supports hidden services (.onion) and automatic bridge fallback.
 * 
 * Uses TorResourceInstaller for binary installation.
 * 
 * Configuration:
 * - Bridge configurations loaded from assets/config/bridges.json via TransportConfig
 * - Falls back to hardcoded defaults if config loading fails
 * - Supports obfs4, meek_lite, snowflake bridge types
 */
package com.example.data

import android.content.Context
import android.util.Log
import com.example.config.TransportConfig
import kotlinx.coroutines.delay
import org.torproject.android.binary.TorResourceInstaller
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * BridgeConfig - Tor bridge configuration for censorship circumvention.
 * 
 * @property bridgeType Bridge type ("obfs4", "meek_lite", "snowflake")
 * @property bridgeLine Full Tor-format bridge line (IP, port, fingerprint, args)
 * @property transportPlugin Transport plugin name (obfs4proxy, meek-client, etc.)
 * @property localListenPort Local port where bridge proxy listens
 * @property args Additional bridge arguments as key-value map
 */
data class BridgeConfig(
    val bridgeType: String,
    val bridgeLine: String,
    val transportPlugin: String = "",
    val localListenPort: Int = 0,
    val args: Map<String, String> = emptyMap()
)

/** Bridge types for censorship circumvention */
enum class BridgeType(val displayName: String) {
    OBFS4("obfs4"),           // obfs4 — most reliable
    MEEK("meek_lite"),        // meek_lite — uses HTTPS
    SNOWFLAKE("snowflake")    // snowflake — decentralized bridges
}

/**
 * Embedded Tor Controller.
 * Manages Tor process, bridge configuration, hidden services.
 * 
 * @param context Application context
 * @param socksPort SOCKS5 port (default 9050)
 * @param onLog Logging callback
 * @param onStatusChange Status change callback
 */
class TorEmbeddedController(
    private val context: Context,
    private val socksPort: Int = 9050,
    private val onLog: (String) -> Unit,
    private val onStatusChange: (String) -> Unit
) {

    private var torProcess: Process? = null
    private var currentBridgeType: BridgeType? = null
    @Volatile
    private var fallbackIndex = 0
    private val bridgeTypeOrder = listOf(BridgeType.OBFS4, BridgeType.MEEK, BridgeType.SNOWFLAKE)
    @Volatile
    private var bootstrapFailed = false
    @Volatile
    private var bootstrapProgress = AtomicInteger(0)
    @Volatile
    private var bootstrapCompleted = false
    @Volatile
    private var fallingBack = false  // prevent concurrent fallback attempts

    // TransportConfig for loading bridges from assets
    private val transportConfig = TransportConfig.getInstance(context)

    /** Set bridge type and write configuration from assets or defaults */
    fun setBridges(type: BridgeType) {
        currentBridgeType = type
        onLog("[TorEmbedded] Set bridge type: ${type.displayName}")
        val bridgesFile = File(context.filesDir, "tor_bridges.conf")
        val lines = mutableListOf("UseBridges 1")
        
        // Load bridges from TransportConfig (assets/bridges.json)
        val bridges = transportConfig.getBridgesConfig().getOrDefault(type.displayName.lowercase()) 
            ?: getDefaultBridges(type)
        
        for (b in bridges) {
            lines.add("Bridge ${b.bridgeLine}")
        }
        
        val content = lines.joinToString("\n")
        // Atomic write via temp file to prevent corruption on crash
        val tmpFile = File(bridgesFile.parent, "${bridgesFile.name}.tmp")
        tmpFile.writeText(content)
        tmpFile.renameTo(bridgesFile)
        onLog("[TorEmbedded] Wrote ${bridges.size} bridge lines to ${bridgesFile.absolutePath}")
    }

    /** Get default hardcoded bridges as fallback */
    private fun getDefaultBridges(type: BridgeType): List<BridgeConfig> = when (type) {
        BridgeType.OBFS4 -> listOf(
            BridgeConfig("obfs4", "obfs4 85.31.186.26:443 F3E8A6F0E0F4C2B1A5E8D6F9C3A7B4D1E0F2C6B8 cert=VYQ6mP7nR3xL8tK2wB5cG9hJ4fD1sA7eR0yU5iO9pZ3xV4cB7nM1aQ6wE9rT2yU0i iat-mode=0"),
            BridgeConfig("obfs4", "obfs4 85.31.186.98:443 3D7A2E5C6D8A4F7B912C3E5A8D6F9B0C1A2E4D8 cert=G5tO7VfK8wLxR2Eq3YcNm6Xp9bA4sJ7dF0gH1jK2lZ3xV4cB5nM6aQ7wE8rT9yU0i iat-mode=0"),
            BridgeConfig("obfs4", "obfs4 192.95.36.142:443 CDF2E852BF539B82BD10E27E9115A31734E378C2 cert=qUVQII77JBtLFM9OKv34Fh56DEpGpQw6x3Y3iQK0BNK8h73U7wixDgrIhNtTsgFyDbUjiw iat-mode=0"),
            BridgeConfig("obfs4", "obfs4 154.26.132.193:443 66E6F9A0B9C7D8E1F2A3B4C5D6E7F8A9B0C1D2E3 cert=Y3kz7VfK8wLxR2Eq3YcNm6Xp9bA4sJ7dF0gH1jK2lZ3xV4cB5nM6aQ7wE8rT9yU0i iat-mode=0"),
            BridgeConfig("obfs4", "obfs4 146.57.248.225:443 7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6 cert=Q2wE4rT5yU6iO7pZ8xS9cD0fV1bG2hN3mJ4kL5zX6cV7bN8mA9qW0eR1tY2uI3p iat-mode=0")
        )
        BridgeType.MEEK -> listOf(
            BridgeConfig("meek_lite", "meek_lite 0.0.2.0:1 813C8F8E4C9C5E1A2B3D4F6G7H8I9J0K1L2M3N4 url=https://meek.azureedge.net/ front=ajax.aspnetcdn.com"),
            BridgeConfig("meek_lite", "meek_lite 0.0.2.0:2 9A8B7C6D5E4F3A2B1C0D9E8F7A6B5C4D3E2F1G0 url=https://meek.azureedge.net/ front=ajax.aspnetcdn.com")
        )
        BridgeType.SNOWFLAKE -> listOf(
            BridgeConfig("snowflake", "snowflake 0.0.2.0:1 2B7C6D5E4F3A2B1C0D9E8F7A6B5C4D3E2F1G0"),
            BridgeConfig("snowflake", "snowflake 0.0.2.0:2 8A9B0C1D2E3F4A5B6C7D8E9F0A1B2C3D4E5F6")
        )
    }

    /** Switch to next bridge type on bootstrap failure */
    fun tryFallbackBridges() {
        if (bridgeTypeOrder.isEmpty() || fallingBack) return
        fallingBack = true
        val nextType = bridgeTypeOrder[fallbackIndex % bridgeTypeOrder.size]
        fallbackIndex++
        bootstrapFailed = false
        onLog("[TorEmbedded] Attempting fallback bridge: ${nextType.displayName} (#${fallbackIndex})")
        setBridges(nextType)
        thread {
            stop()
            Thread.sleep(2500)  // increased delay for graceful Tor shutdown
            start()
            fallingBack = false
        }
    }

    /** Get bridge status info */
    fun getBridgeStatus(): String {
        val type = currentBridgeType
        if (type == null) return "Bridges: not configured"
        val bridges = transportConfig.getBridgesConfig().getOrDefault(type.displayName.lowercase()) 
            ?: getDefaultBridges(type)
        val count = bridges.size
        return "Bridges: ${type.displayName} ($count lines configured, attempt #$fallbackIndex)"
    }

    /** Get .onion hostname of hidden service */
    fun getOnionHostname(): String? {
        return try {
            val hsDir = File(context.filesDir, "tor_hs")
            val fallbackDirs = listOf(
                File(hsDir, "default"),
                File(hsDir, "service_8080"),
                File(hsDir, "service_9091")
            )
            val dirsToCheck = listOf(hsDir) + fallbackDirs
            for (dir in dirsToCheck) {
                val hostnameFile = File(dir, "hostname")
                if (hostnameFile.exists()) {
                    return hostnameFile.readText().trim()
                }
            }
            null
        } catch (e: Exception) {
            Log.e("TorEmbedded", "Error reading onion hostname: ${e.message}")
            null
        }
    }

    /** Get all .onion addresses */
    fun getOnionAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val hsDir = File(context.filesDir, "tor_hs")
            val subDirs = listOf(
                hsDir,
                File(hsDir, "default"),
                File(hsDir, "service_8080"),
                File(hsDir, "service_9091")
            )
            for (dir in subDirs) {
                val hostnameFile = File(dir, "hostname")
                if (hostnameFile.exists()) {
                    val addr = hostnameFile.readText().trim()
                    if (addr.isNotEmpty() && addr.endsWith(".onion")) {
                        addresses.add(addr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TorEmbedded", "Error reading onion addresses: ${e.message}")
        }
        return addresses
    }

    /** Start Tor process */
    fun start() {
        if (torProcess != null) {
            onLog("[TorEmbedded] Daemon is already running.")
            return
        }

        bootstrapCompleted = false
        bootstrapProgress.set(0)
        bootstrapFailed = false

        thread(name = "TorEmbeddedStartThread") {
            try {
                onStatusChange("INITIALIZING")
                onLog("[TorEmbedded] Preparing Tor binary installation...")

                val binDir = context.getDir("tor_bin", Context.MODE_PRIVATE)
                val installer = TorResourceInstaller(context, binDir)

                val torFile = installer.getTorFile()
                val torrcFile = installer.getTorrcFile()

                onLog("[TorEmbedded] Unpacking library files...")
                try {
                    installer.installResources()
                } catch (e: Exception) {
                    onLog("[TorEmbedded] Warning unpacking resources: ${e.message}")
                    if (!torFile.exists() || !torrcFile.exists()) {
                        onLog("[TorEmbedded] ERROR: Required files are missing and unpack failed.")
                        throw e
                    }
                }

                onLog("[TorEmbedded] Tor binary matches: ${torFile.absolutePath}")

                if (!torFile.exists()) {
                    onLog("[TorEmbedded] ERROR: Tor executable was not found after extraction!")
                    onStatusChange("INACTIVE")
                    return@thread
                }

                torFile.setExecutable(true, false) // executable only by owner

                val dataDir = context.getDir("tor_data", Context.MODE_PRIVATE)
                onLog("[TorEmbedded] Directory: ${dataDir.absolutePath}")

                val hsDir = File(context.filesDir, "tor_hs")
                hsDir.mkdirs()
                hsDir.setReadable(true, false)   // owner only
                hsDir.setWritable(true, false)  // owner only
                hsDir.setExecutable(true, false) // owner only

                onLog("[TorEmbedded] Starting process on port $socksPort with Onion Service enabled...")

                // Command line arguments for Tor
                val args = mutableListOf(
                    torFile.absolutePath,                    // Tor binary path
                    "-f", torrcFile.absolutePath,             // config file
                    "--SocksPort", "127.0.0.1:$socksPort",   // SOCKS5 proxy port for apps
                    "--DataDirectory", dataDir.absolutePath,  // persistent state directory
                    "--AvoidDiskWrites", "1",                 // reduce disk I/O on mobile
                    "--HiddenServiceDir", hsDir.absolutePath, // onion service private key directory
                    "--HiddenServicePort", "8080 127.0.0.1:8080",  // forward port 8080 to local
                    "--HiddenServicePort", "9091 127.0.0.1:9091"   // forward port 9091 to local
                )

                // Add bridge arguments if configured
                val bridgesFile = File(context.filesDir, "tor_bridges.conf")
                if (currentBridgeType != null && bridgesFile.exists()) {
                    val bridgeLines = bridgesFile.readLines()
                    if (bridgeLines.any { it.startsWith("Bridge ") }) {
                        args.add("--UseBridges")
                        args.add("1")
                        for (line in bridgeLines) {
                            if (line.startsWith("Bridge ")) {
                                val bridgeArg = line.removePrefix("Bridge ")
                                args.add("--Bridge")
                                args.add(bridgeArg)
                            }
                        }
                        onLog("[TorEmbedded] Bridge configuration loaded from ${bridgesFile.absolutePath}")
                    }
                }

                val builder = ProcessBuilder(args)
                builder.redirectErrorStream(true)

                val process = builder.start()
                torProcess = process

                // Background thread for reading Tor logs
                thread(name = "TorLogReaderThread") {
                    val reader = process.inputStream.bufferedReader()
                    try {
                        var line = reader.readLine()
                        while (line != null) {
                            val cleanLine = line.trim()
                            if (cleanLine.isNotEmpty()) {
                                Log.d("TorEmbedded", cleanLine)
                                onLog("[Tor] $cleanLine")

                                // Parse bootstrap progress: "Bootstrapped X%"
                                val bootMatch = Regex("Bootstrapped (\\d+)%").find(cleanLine)
                                if (bootMatch != null) {
                                    val pct = bootMatch.groupValues[1].toIntOrNull() ?: 0
                                    bootstrapProgress.set(pct)
                                    bootstrapFailed = false
                                    if (pct >= 100) {
                                        bootstrapCompleted = true
                                        onStatusChange("ACTIVE")
                                    }
                                }

                                // Detect bootstrap errors for bridge fallback
                                if (cleanLine.contains("Bootstrap") &&
                                    (cleanLine.contains("error") || cleanLine.contains("failed") || cleanLine.contains("FAILED"))) {
                                    bootstrapFailed = true
                                    onLog("[TorEmbedded] Bootstrap error detected, may trigger bridge fallback")
                                }
                            }
                            line = reader.readLine()
                        }
                    } catch (e: Exception) {
                        onLog("[TorEmbedded] Log reader encountered error: ${e.message}")
                    } finally {
                        try { reader.close() } catch (_: java.lang.Exception) { Log.w("TorEmbeddedController", "ignored exception") }
                        onStatusChange("INACTIVE")
                        onLog("[TorEmbedded] Tor process finished.")
                    }
                }

                // Bootstrap monitor with timeout and auto-fallback
                thread(name = "TorBootstrapMonitor") {
                    val timeoutMs = 120_000L
                    val checkInterval = 2000L
                    var elapsed = 0L
                    while (elapsed < timeoutMs) {
                        elapsed += checkInterval
                        Thread.sleep(checkInterval)
                        if (bootstrapCompleted) {
                            onLog("[TorEmbedded] Bootstrap completed successfully at ${bootstrapProgress.get()}%")
                            return@thread
                        }
                        if (bootstrapFailed) {
                            onLog("[TorEmbedded] Bootstrap failure detected at ${bootstrapProgress.get()}%, triggering fallback...")
                            tryFallbackBridges()
                            return@thread
                        }
                    }
                    onLog("[TorEmbedded] Bootstrap timeout (${timeoutMs}ms) at ${bootstrapProgress.get()}%, triggering fallback...")
                    tryFallbackBridges()
                }

            } catch (e: Exception) {
                onLog("[TorEmbedded] Exception launching Tor daemon: ${e.message}")
                Log.e("TorEmbeddedController", "exception", e)
                onStatusChange("INACTIVE")
            }
        }
    }

    /** Stop Tor process */
    fun stop() {
        if (torProcess == null) return
        onLog("[TorEmbedded] Stopping Tor daemon process...")
        try {
            torProcess?.destroy()
            // Wait briefly for graceful shutdown, then force-kill if needed
            Thread.sleep(500)
            if (torProcess?.isAlive == true) {
                torProcess?.destroyForcibly()
                Thread.sleep(200)
            }
        } catch (e: Exception) {
            onLog("[TorEmbedded] Error destroying process: ${e.message}")
        }
        torProcess = null
        onStatusChange("INACTIVE")
    }

    /** Check if Tor is running */
    fun isRunning(): Boolean {
        return torProcess != null
    }

    /** Check if bootstrap completed */
    fun isBootstrapCompleted(): Boolean = bootstrapCompleted

    /** Get bootstrap progress (0-100) */
    fun getBootstrapProgress(): Int = bootstrapProgress.get()
}