/**
 * Пакет ViewModel — модель представления для сетевых настроек.
 *
 * ## Содержимое
 * - [NetworkUiState] — состояние UI сетевого экрана (Tor, VPN, мост, статус, пинг).
 * - [NetworkViewModel] — ViewModel для управления Tor, VPN и тестирования соединения.
 */
package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FoxrayVpnManager
import com.example.data.TorEmbeddedController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние UI сетевого экрана.
 */
data class NetworkUiState(
    val torEnabled: Boolean = false,
    val vpnEnabled: Boolean = false,
    val bridgeType: String = "none",
    val connectionStatus: String = "Disconnected",
    val pingMs: Int = -1
)

/**
 * ViewModel для управления сетевыми компонентами: Tor, VPN, мосты.
 */
class NetworkViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    val vpnManager = FoxrayVpnManager(context)
    private var torController: TorEmbeddedController? = null

    /** Включает/выключает Tor. */
    fun toggleTor() {
        val current = _uiState.value.torEnabled
        if (current) {
            torController?.stop()
            _uiState.value = _uiState.value.copy(torEnabled = false, connectionStatus = "Disconnected")
        } else {
            torController?.stop()
            torController = TorEmbeddedController(
                context = context,
                socksPort = 9050,
                onLog = { },
                onStatusChange = { status ->
                    _uiState.value = _uiState.value.copy(
                        connectionStatus = status,
                        torEnabled = status == "ACTIVE"
                    )
                }
            )
            torController?.start()
            _uiState.value = _uiState.value.copy(torEnabled = true, connectionStatus = "INITIALIZING")
        }
    }

    /** Включает/выключает VPN. */
    fun toggleVpn() {
        val current = _uiState.value.vpnEnabled
        if (current) {
            vpnManager.stopVpn()
            _uiState.value = _uiState.value.copy(vpnEnabled = false)
        } else {
            vpnManager.startVpn()
            _uiState.value = _uiState.value.copy(vpnEnabled = true, pingMs = vpnManager.pingTime)
            viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(2000)
                _uiState.value = _uiState.value.copy(
                    vpnEnabled = vpnManager.vpnState == "Connected",
                    pingMs = vpnManager.pingTime
                )
            }
        }
    }

    /** Устанавливает тип моста. */
    fun setBridge(type: String) {
        _uiState.value = _uiState.value.copy(bridgeType = type)
    }

    /** Тестирует сетевое соединение. */
    fun testConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(connectionStatus = "TESTING")
            kotlinx.coroutines.delay(1000)
            val ping = vpnManager.pingTime
            _uiState.value = _uiState.value.copy(
                connectionStatus = if (ping > 0) "Connected" else "Failed",
                pingMs = ping
            )
        }
    }
}
