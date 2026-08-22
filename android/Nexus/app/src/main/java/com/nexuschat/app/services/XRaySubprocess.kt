package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*

class XRaySubprocess private constructor() {
    companion object {
        private const val TAG = "NexusChat/XRay"
        const val XRAY_SOCKS_PORT = 10810
        const val XRAY_API_PORT = 10811
        @Volatile private var instance: XRaySubprocess? = null
        fun getInstance(): XRaySubprocess =
            instance ?: synchronized(this) {
                instance ?: XRaySubprocess().also { instance = it }
            }
    }

    data class XRayConfig(
        val protocol: String = "vless",
        val address: String = "",
        val port: Int = 443,
        val uuid: String = "",
        val flow: String = "",
        val encryption: String = "none",
        val network: String = "tcp",
        val tls: Boolean = true,
        val fingerprint: String = "chrome",
        val path: String = "/",
        val host: String = "",
        val security: String = "auto",
        val alpn: List<String> = listOf("h2", "http/1.1"),
        val allowInsecure: Boolean = false,
        val mux: Boolean = true,
        val muxConcurrency: Int = 8,
        val localSocksPort: Int = XRAY_SOCKS_PORT
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var xrayProcess: Process? = null
    var isRunning = false
        private set

    fun start(config: XRayConfig) {
        scope.launch {
            try {
                val configJson = generateXrayConfig(config)
                val configDir = java.io.File(
                    com.nexuschat.app.NexusChatApp.instance.filesDir, "xray"
                ).also { it.mkdirs() }
                val configFile = java.io.File(configDir, "config.json")
                configFile.writeText(configJson)
                var binary = findXrayBinary()
                if (binary == null) {
                    Log.w(TAG, "No xray binary found, attempting auto-download")
                    binary = BinaryDownloader.getInstance(
                        com.nexuschat.app.NexusChatApp.instance
                    ).downloadBinary(BinaryDownloader.BinaryType.XRAY)
                }
                if (binary == null) {
                    Log.e(TAG, "XRay binary unavailable — proxy will not start")
                    isRunning = false
                    return@launch
                }
                val pb = ProcessBuilder(binary.absolutePath, "run", "-config", configFile.absolutePath)
                pb.directory(configDir)
                pb.environment()["xray.location.assetdir"] = configDir.absolutePath
                pb.environment()["xray.location.confdir"] = configDir.absolutePath
                pb.redirectErrorStream(true)
                xrayProcess = pb.start()
                scope.launch {
                    xrayProcess?.inputStream?.bufferedReader()?.use { reader ->
                        reader.forEachLine { Log.d(TAG, "xray: $it") }
                    }
                }
                delay(2000)
                val portCheck = try {
                    val s = java.net.Socket()
                    s.connect(java.net.InetSocketAddress("127.0.0.1", XRAY_SOCKS_PORT), 1000)
                    s.close()
                    true
                } catch (e: Exception) { false }
                if (!portCheck) throw RuntimeException("XRay SOCKS5 port $XRAY_SOCKS_PORT not listening")
                isRunning = true
                Log.i(TAG, "XRay ${config.protocol} -> ${config.address}:${config.port}")
            } catch (e: Exception) {
                Log.e(TAG, "XRay start failed: ${e.message}")
                isRunning = false
                xrayProcess?.destroyForcibly()
                xrayProcess = null
            }
        }
    }

    private fun findXrayBinary(): java.io.File? {
        val ctx = com.nexuschat.app.NexusChatApp.instance
        val paths = listOf(
            java.io.File(ctx.applicationInfo.nativeLibraryDir, "libxray.so"),
            java.io.File(ctx.filesDir, "xray"),
            java.io.File(java.io.File(ctx.filesDir, "xray-core"), "xray"),
        )
        return paths.firstOrNull { it.exists() }
    }

    private fun generateXrayConfig(config: XRayConfig): String {
        return """{
            "log": {"loglevel":"warning"},
            "inbounds": [{
                "port":${config.localSocksPort},
                "listen":"127.0.0.1",
                "protocol":"socks",
                "settings":{"udp":true,"auth":"noauth"}
            }],
            "outbounds": [{
                "protocol":"${config.protocol}",
                "settings": {
                    "vnext": [{
                        "address":"${config.address}",
                        "port":${config.port},
                        "users": [{
                            "id":"${config.uuid}",
                            "flow":"${config.flow}",
                            "encryption":"${config.encryption}",
                            "security":"${config.security}"
                        }]
                    }]
                },
                "streamSettings": {
                    "network":"${config.network}",
                    "security":"${if (config.tls) "tls" else "none"}",
                    "tlsSettings": {
                        "serverName":"${config.host}",
                        "fingerprint":"${config.fingerprint}",
                        "alpn":${gsonList(config.alpn)},
                        "allowInsecure":${config.allowInsecure}
                    },
                    "wsSettings": ${if(config.network=="ws"||config.network=="websocket")"""{"path":"${config.path}","headers":{"Host":"${config.host}"}}""" else "null"},
                    "grpcSettings": ${if(config.network=="grpc")"""{"serviceName":"${config.host}"}""" else "null"}
                },
                "mux": {"enabled":${config.mux},"concurrency":${config.muxConcurrency}}
            },{
                "protocol":"freedom",
                "tag":"direct"
            }],
            "routing": {
                "domainStrategy":"AsIs",
                "rules":[
                    {"type":"field","ip":["geoip:private"],"outboundTag":"direct"},
                    {"type":"field","domain":["geosite:cn"],"outboundTag":"direct"}
                ]
            }
        }"""
    }

    private fun gsonList(items: List<String>): String {
        return items.joinToString(",", "[", "]") { "\"$it\"" }
    }

    fun stop() {
        isRunning = false
        xrayProcess?.destroy()
        xrayProcess?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        xrayProcess?.destroyForcibly()
        xrayProcess = null
        Log.i(TAG, "XRay stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
        instance = null
    }
}
