package com.example.ui.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.NetworkDefaults

class NetworkViewModel(private val ctx: Context) : ViewModel() {

    private val prefs = ctx.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)

    var serverStatus by mutableStateOf("DISCONNECTED")
        private set
    var isConnectingToServer by mutableStateOf(false)
        private set
    var challengeGameStartedTrigger by mutableStateOf(false)

    var serverUrl by mutableStateOf(
        prefs.getString("server_url", NetworkDefaults.SERVER_URL)
            ?: NetworkDefaults.SERVER_URL
    )
        private set

    var isConnectOnStartupEnabled by mutableStateOf(prefs.getBoolean("connect_on_startup", false))
        private set

    var isNoGameModeEnabled by mutableStateOf(prefs.getBoolean("is_no_game_mode", false))
        private set

    // --- MIMICRY / CAMOUFLAGE ---
    var isMimicryActive by mutableStateOf(com.example.service.MimicryController.isActive(prefs))
    var selectedMimicMode by mutableStateOf(com.example.service.MimicryController.getMode(prefs).name)
    var showMimicryDialog by mutableStateOf(false)

    // --- GAME SERVER & CONNECTION ---

    fun updateServerUrl(url: String) {
        serverUrl = url
        prefs.edit().putString("server_url", url).apply()
    }

    fun updateConnectOnStartup(enabled: Boolean) {
        isConnectOnStartupEnabled = enabled
        prefs.edit().putBoolean("connect_on_startup", enabled).apply()
    }

    fun updateNoGameMode(enabled: Boolean) {
        isNoGameModeEnabled = enabled
        prefs.edit().putBoolean("is_no_game_mode", enabled).apply()
    }

    fun connectToServer() {
        if (isConnectingToServer) return
        isConnectingToServer = true
        serverStatus = "CONNECTING"
        serverStatus = "CONNECTED"
        isConnectingToServer = false
    }

    fun disconnectFromServer() {
        serverStatus = "DISCONNECTED"
    }

    // --- MIMICRY ---

    fun activateMimicry(mode: String) {
        selectedMimicMode = mode
        val mimicMode = try {
            com.example.service.MimicryController.MimicMode.valueOf(mode)
        } catch (e: Exception) {
            com.example.service.MimicryController.MimicMode.CALCULATOR
        }
        com.example.service.MimicryController.activate(ctx, mimicMode)
        isMimicryActive = true
        prefs.edit().putBoolean("mimicry_active", true).apply()
    }

    fun deactivateMimicry() {
        com.example.service.MimicryController.deactivate(ctx)
        isMimicryActive = false
        prefs.edit().putBoolean("mimicry_active", false).apply()
    }

    override fun onCleared() {
        super.onCleared()
    }
}

class NetworkViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NetworkViewModel::class.java)) {
            return NetworkViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
