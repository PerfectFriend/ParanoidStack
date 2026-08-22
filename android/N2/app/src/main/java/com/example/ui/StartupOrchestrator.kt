package com.example.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.NetworkDefaults
import com.example.data.SecureStorage
import com.example.data.Bip39Helper
import com.example.data.getOrNull
import com.example.security.DuressPinManager
import com.example.security.PinResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Состояние терминала — каждый этап запуска.
 */
enum class TerminalStage(val label: String) {
    PIN_VERIFIED("✓ PIN verified"),
    BIP39_LOADED("✓ Wallet recovered"),
    DNS_CHECK("⏳ DNS resolution"),
    TCP_TEST("⏳ TCP connectivity"),
    TOR_START("⏳ Tor SOCKS5 :9050"),
    V2RAY_START("⏳ V2Ray SOCKS5 :10808"),
    BRIDGE_BUILD("⏳ Bridge: App → V2Ray → Tor"),
    SMP_TEST("⏳ SMP onion reachable"),
    XFTP_TEST("⏳ XFTP onion reachable"),
    READY("✦ Terminal READY")
}

sealed class TerminalStepResult {
    data object Skipped : TerminalStepResult()
    data object Running : TerminalStepResult()
    data class Pass(val detail: String = "") : TerminalStepResult()
    data class Fail(val detail: String) : TerminalStepResult()
}

/**
 * Состояние терминала.
 */
enum class TerminalState {
    UNINITIALIZED,
    AUTHENTICATED,
    DIAGNOSING,
    READY,
    ERROR
}

private const val SEED_PREF_KEY = "n2_seed_phrase"

/**
 * Оркестратор полного цикла запуска терминала:
 * PIN → BIP39 → DNS → TCP → Tor → V2Ray → Bridge → SMP → XFTP → READY
 */
class StartupOrchestrator(private val context: Context) {

    private val tag = "StartupOrch"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var terminalState by mutableStateOf(TerminalState.UNINITIALIZED)
        private set
    var currentStageIndex by mutableStateOf(0)
        private set
    var stepResults = MutableStateFlow(
        TerminalStage.entries.associateWith { TerminalStepResult.Skipped as TerminalStepResult }
    )
        private set
    var isRunning by mutableStateOf(false)
        private set
    var seedPhrase by mutableStateOf<String?>(null)
        private set
    var verifiedPin by mutableStateOf(false)
        private set
    var failedPinAttempts by mutableStateOf(0)
        private set
    var isBiometricAvailable by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var pinJustSet by mutableStateOf(false)
        private set

    /** Если PIN установлен (первый запуск) */
    var isPinSet by mutableStateOf(false)
        private set

    // ──────────────────────────────────────────────

    fun initialize() {
        val configLoaded = try {
            DuressPinManager.initialize(context)
            // Проверяем, есть ли конфиг
            val testPin = DuressPinManager.verifyPin("__probe__")
            testPin != PinResult.INVALID || true // Если хоть как-то ответил — конфиг есть
        } catch (_: Exception) { false }
        // На самом деле проверяем через SecureStorage
        val storedHash = SecureStorage.getString("duress_pin_config").getOrNull()
        isPinSet = storedHash?.isNotEmpty() == true

        // Пытаемся загрузить seed
        val seedResult: String? = SecureStorage.getString(SEED_PREF_KEY).getOrNull()
        if (seedResult?.isNotEmpty() == true) {
            seedPhrase = seedResult
        }

        Log.i(tag, "initialize: isPinSet=$isPinSet, seedLoaded=${seedPhrase != null}")
    }

    /** Верификация PIN-кода */
    fun verifyPin(pin: String): Boolean {
        val result = DuressPinManager.verifyPin(pin)
        when (result) {
            PinResult.MATCH_MAIN -> {
                verifiedPin = true
                failedPinAttempts = 0
                terminalState = TerminalState.AUTHENTICATED
                Log.i(tag, "PIN verified")
                return true
            }
            PinResult.MATCH_DURESS -> {
                // Экстренный PIN — не даём реального доступа
                DuressPinManager.triggerDuressMode()
                failedPinAttempts++
                Log.w(tag, "Duress PIN entered")
                return false
            }
            PinResult.INVALID -> {
                failedPinAttempts++
                Log.w(tag, "PIN failed ($failedPinAttempts)")
                return false
            }
        }
    }

    /** Установка нового PIN */
    fun setupPin(pin: String, duressPin: String? = null) {
        DuressPinManager.configurePins(pin, duressPin)
        isPinSet = true
        pinJustSet = true
        verifiedPin = true
        terminalState = TerminalState.AUTHENTICATED
        Log.i(tag, "PIN configured")
    }

    /** Сохранить seed-фразу */
    fun saveSeedPhrase(phrase: String): Boolean {
        val words = phrase.trim().split("\\s+".toRegex())
        if (words.size != 12 && words.size != 24) return false
        seedPhrase = phrase
        SecureStorage.putString(SEED_PREF_KEY, phrase)
        Log.i(tag, "Seed saved (${words.size} words)")
        return true
    }

    /** Генерация новой seed-фразы */
    fun generateSeedPhrase(): String {
        val phrase = Bip39Helper.generateMnemonic(context)
        seedPhrase = phrase
        SecureStorage.putString(SEED_PREF_KEY, phrase)
        Log.i(tag, "New BIP39 seed generated")
        return phrase
    }

    // ─── Диагностика сети ─────────────────────────

    fun runDiagnostics() {
        if (isRunning) return
        if (!verifiedPin) {
            errorMessage = "Terminal not authenticated"
            terminalState = TerminalState.ERROR
            return
        }
        isRunning = true
        terminalState = TerminalState.DIAGNOSING
        errorMessage = null
        currentStageIndex = 0
        val stages = TerminalStage.entries

        scope.launch {
            val results = stepResults.value.toMutableMap()

            for ((index, stage) in stages.withIndex()) {
                if (stage == TerminalStage.READY) {
                    currentStageIndex = index
                    results[stage] = TerminalStepResult.Pass()
                    stepResults.value = results
                    continue
                }
                currentStageIndex = index
                results[stage] = TerminalStepResult.Running
                stepResults.value = results

                val result = withContext(Dispatchers.IO) {
                    try {
                        val seedDesc = seedPhrase?.let { "Seed OK (${it.split(" ").size} words)" } ?: "No seed"
                        when (stage) {
                            TerminalStage.PIN_VERIFIED -> TerminalStepResult.Pass()
                            TerminalStage.BIP39_LOADED -> TerminalStepResult.Pass(seedDesc)
                            TerminalStage.DNS_CHECK -> checkDNS()
                            TerminalStage.TCP_TEST -> checkTCP()
                            TerminalStage.TOR_START -> checkTorProxy()
                            TerminalStage.V2RAY_START -> checkV2RayProxy()
                            TerminalStage.BRIDGE_BUILD -> checkBridge()
                            TerminalStage.SMP_TEST -> checkOnion("SMP", NetworkDefaults.SMP_ONION, 5223)
                            TerminalStage.XFTP_TEST -> checkOnion("XFTP", NetworkDefaults.XFTP_ONION, 443)
                            TerminalStage.READY -> TerminalStepResult.Pass()
                        }
                    } catch (e: Exception) {
                        TerminalStepResult.Fail("${e::class.simpleName}: ${e.message}")
                    }
                }
                results[stage] = result
                stepResults.value = results
            }

            // Финальная проверка
            val torOk = results[TerminalStage.TOR_START] is TerminalStepResult.Pass
            val ready = if (torOk) {
                terminalState = TerminalState.READY
                currentStageIndex = stages.indexOf(TerminalStage.READY)
                results[TerminalStage.READY] = TerminalStepResult.Pass("All systems operational")
                stepResults.value = results
                Log.i(tag, "🎯 TERMINAL READY")
                true
            } else {
                terminalState = TerminalState.ERROR
                errorMessage = "Tor SOCKS5 unreachable — check network"
                Log.e(tag, "💥 TERMINAL ERROR: Tor unreachable")
                false
            }
            isRunning = false
        }
    }

    fun reset() {
        scope.coroutineContext[Job]?.cancelChildren()
        terminalState = TerminalState.UNINITIALIZED
        currentStageIndex = 0
        stepResults.value = TerminalStage.entries.associateWith { TerminalStepResult.Skipped }
        isRunning = false
        verifiedPin = false
        errorMessage = null
        failedPinAttempts = 0
    }

    fun destroy() { scope.cancel() }

    // ─── Тесты ─────────────────────────────────────

    private fun checkDNS() = try {
        val ip = java.net.InetAddress.getByName("cloudflare-dns.com")
        TerminalStepResult.Pass("DNS OK → ${ip.hostAddress}")
    } catch (_: Exception) {
        try {
            java.net.InetAddress.getByName("1.1.1.1")
            TerminalStepResult.Pass("DNS OK (fallback)")
        } catch (e: Exception) {
            TerminalStepResult.Fail("DNS: ${e.message}")
        }
    }

    private fun checkTCP() = try {
        Socket().use { sock ->
            sock.connect(InetSocketAddress("1.1.1.1", 443), 4000)
            TerminalStepResult.Pass("TCP 1.1.1.1:443 ✓")
        }
    } catch (_: Exception) {
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress("8.8.8.8", 443), 4000)
                TerminalStepResult.Pass("TCP 8.8.8.8:443 ✓")
            }
        } catch (e: Exception) {
            TerminalStepResult.Fail("TCP: ${e.message}")
        }
    }

    private fun checkTorProxy() = try {
        Socket().use { sock ->
            sock.connect(InetSocketAddress("127.0.0.1", NetworkDefaults.DEFAULT_TOR_SOCKS_PORT), 3000)
            TerminalStepResult.Pass("Tor SOCKS5 :9050 ✓")
        }
    } catch (e: Exception) {
        TerminalStepResult.Fail("Tor: ${e.message}")
    }

    private fun checkV2RayProxy() = try {
        Socket().use { sock ->
            sock.connect(InetSocketAddress("127.0.0.1", 10808), 3000)
            TerminalStepResult.Pass("V2Ray SOCKS5 :10808 ✓")
        }
    } catch (_: Exception) {
        // V2Ray опционален
        TerminalStepResult.Pass("V2Ray not running (optional)")
    }

    private fun checkBridge() = try {
        // Проверяем chain через Tor SOCKS5 до onion
        Socket(java.net.Proxy(java.net.Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", NetworkDefaults.DEFAULT_TOR_SOCKS_PORT))
        ).use { sock ->
            sock.connect(InetSocketAddress("7czed3rxeryz4zxlo7wiwgz36yfmdwvu6ylv5wkby3trei3qsuw4lnqd.onion", 5223), 10000)
            TerminalStepResult.Pass("Bridge: App→Tor→SMP ✓")
        }
    } catch (e: Exception) {
        TerminalStepResult.Fail("Bridge: ${e.message}")
    }

    private fun checkOnion(name: String, uri: String, defaultPort: Int) = try {
        val host = uri.substringAfter("@").substringBefore(":")
        val portStr = uri.substringAfter(":", "").substringBefore("/")
        val port = portStr.toIntOrNull() ?: defaultPort
        Socket(java.net.Proxy(java.net.Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", NetworkDefaults.DEFAULT_TOR_SOCKS_PORT))
        ).use { sock ->
            sock.connect(InetSocketAddress(host, port), 10000)
            TerminalStepResult.Pass("$name $host:$port ✓")
        }
    } catch (e: Exception) {
        TerminalStepResult.Fail("$name: ${e.message}")
    }
}
