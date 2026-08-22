package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.example.security.ClipboardGuard
import com.example.security.ScreenSecurityManager
import com.example.ui.GameViewModel
import com.example.ui.GameViewModelFactory
import com.example.ui.viewmodels.ProtocolViewModel
import com.example.ui.viewmodels.AudioViewModel
import com.example.ui.viewmodels.NetworkViewModel
import com.example.ui.navigation.AppNavHost
import com.example.ui.navigation.DeepLinkHandler
import com.example.ui.navigation.NavRoutes
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.BootScreen
import com.example.ui.screens.auth.AppLockScreen
import com.example.ui.screens.terminal.TerminalSetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.service.PushNotificationForegroundService
import com.example.ui.StartupOrchestrator

class MainActivity : ComponentActivity() {

    private var showBoot by mutableStateOf(true)
    private var permissionsGranted by mutableStateOf(false)
    private var terminalDone by mutableStateOf(false)
    private var showTerminalSetup by mutableStateOf(false)
    private var showAppLock by mutableStateOf(false)
    private var pinSetupMode by mutableStateOf(false)
    private lateinit var protocolViewModel: ProtocolViewModel
    private lateinit var audioViewModel: AudioViewModel
    private lateinit var networkViewModel: NetworkViewModel
    private lateinit var startupOrchestrator: StartupOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        ScreenSecurityManager.registerActivity(this)
        ClipboardGuard.startAutoClearWorker(this)

        protocolViewModel = ProtocolViewModel(applicationContext)
        audioViewModel = AudioViewModel(applicationContext)
        networkViewModel = NetworkViewModel(applicationContext)

        val viewModel = ViewModelProvider(
            this,
            GameViewModelFactory(applicationContext)
        )[GameViewModel::class.java]

        // Инициализация оркестратора запуска
        startupOrchestrator = StartupOrchestrator(applicationContext)
        startupOrchestrator.initialize()

        // Определяем, что показать после сплэша
        if (startupOrchestrator.isPinSet) {
            showAppLock = true
            pinSetupMode = false
        } else {
            showAppLock = true
            pinSetupMode = true
        }

        setContent {
            val navController = rememberNavController()
            MyApplicationTheme(themeId = viewModel.selectedTheme) {
                if (showBoot) {
                    BootScreen(onBootFinished = { showBoot = false })
                } else if (!permissionsGranted) {
                    PermissionsScreen(onAllGranted = {
                        permissionsGranted = true
                        startPushService()
                    })
                } else if (showAppLock && !terminalDone) {
                    AppLockScreen(
                        isBiometricAvailable = false,
                        isSetupMode = pinSetupMode,
                        onUnlockWithPin = { pin ->
                            if (pinSetupMode) {
                                startupOrchestrator.setupPin(pin)
                                showAppLock = false
                                showTerminalSetup = true
                                startupOrchestrator.runDiagnostics()
                            } else if (startupOrchestrator.verifyPin(pin)) {
                                showAppLock = false
                                showTerminalSetup = true
                                startupOrchestrator.runDiagnostics()
                            }
                        },
                        failedAttempts = startupOrchestrator.failedPinAttempts,
                        onResetApp = {
                            startupOrchestrator.reset()
                            showAppLock = false
                            pinSetupMode = true
                            showAppLock = true
                        }
                    )
                } else if (showTerminalSetup && !terminalDone) {
                    TerminalSetupScreen(
                        currentStageIndex = startupOrchestrator.currentStageIndex,
                        stepResults = startupOrchestrator.stepResults.value,
                        isRunning = startupOrchestrator.isRunning,
                        terminalReady = startupOrchestrator.terminalState == com.example.ui.TerminalState.READY,
                        errorMessage = startupOrchestrator.errorMessage,
                        onRetry = { startupOrchestrator.runDiagnostics() },
                        onLaunchMessenger = {
                            terminalDone = true
                            showTerminalSetup = false
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onReset = {
                            startupOrchestrator.reset()
                            showTerminalSetup = false
                            showAppLock = true
                        }
                    )
                } else {
                    AppNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        protocolViewModel = protocolViewModel,
                        audioViewModel = audioViewModel,
                        networkViewModel = networkViewModel,
                        bandwidthMonitor = com.example.MyApplication.bandwidthMonitor,
                        startDestination = if (getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .getBoolean("onboarding_complete", false))
                            NavRoutes.Dashboard.route
                        else
                            NavRoutes.Onboarding.route
                    )
                }
            }
        }

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        val result = intent?.let { DeepLinkHandler().handleIntent(it) }
        if (result != null) {
            Log.d("DeepLinkHandler", "Deep link received: type=${result.type}, contactId=${result.contactId}, groupId=${result.groupId}, inviteCode=${result.inviteCode}")
        }
    }

    fun enableSecureMode() {
        ScreenSecurityManager.enableScreenSecurity()
    }

    fun disableSecureMode() {
        ScreenSecurityManager.disableScreenSecurity()
    }

    /** Запускает push-сервис после получения всех разрешений. */
    fun startPushService() {
        try {
            val prefs = getSharedPreferences("service_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("push_service_enabled", true)) {
                PushNotificationForegroundService.start(this)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start push service", e)
        }
    }

    override fun onDestroy() {
        try {
            if (::audioViewModel.isInitialized) audioViewModel.radioManager.stop()
            if (::protocolViewModel.isInitialized) {
                protocolViewModel.vpnManager.stopVpn()
                protocolViewModel.setTorEnabledState(false)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "exception during cleanup", e)
        }
        ClipboardGuard.stopAutoClearWorker()
        super.onDestroy()
    }
}
