package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.GameViewModel
import com.example.ui.viewmodels.ProtocolViewModel
import com.example.ui.viewmodels.AudioViewModel
import com.example.ui.viewmodels.NetworkViewModel
import com.example.ui.screens.GameScreen
import com.example.ui.screens.about.AboutScreen
import com.example.ui.screens.auth.AppLockScreen
import com.example.ui.screens.contacts.BlockListScreen
import com.example.ui.screens.contacts.ContactDetailScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.debug.DebugPanel
import com.example.ui.screens.debug.LogExportScreen
import com.example.ui.screens.diagnostics.NetworkTestScreen
import com.example.ui.screens.files.FilePreviewScreen
import com.example.ui.screens.game.GameOverScreen
import com.example.ui.screens.game.GameStatsScreen
import com.example.ui.screens.groups.CreateGroupScreen
import com.example.ui.screens.groups.GroupInfoScreen
import com.example.ui.screens.groups.GroupSetupFlow
import com.example.ui.screens.messages.MessageDetailScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.profile.ProfileEditScreen
import com.example.ui.screens.settings.*
import com.example.data.BandwidthMonitor

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: GameViewModel? = null,
    protocolViewModel: ProtocolViewModel? = null,
    audioViewModel: AudioViewModel? = null,
    networkViewModel: NetworkViewModel? = null,
    bandwidthMonitor: BandwidthMonitor? = null,
    startDestination: String = NavRoutes.Dashboard.route
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.Dashboard.route) {
            DashboardScreen(
                networkState = viewModel?.protocolOrchestrator?.autoDetector?.networkState?.value,
                bandwidthMonitor = bandwidthMonitor,
                onOpenChat = { navController.navigate(NavRoutes.Chat.createRoute(it)) },
                onOpenGame = { navController.navigate(NavRoutes.Game.route) },
                onSettings = { navController.navigate(NavRoutes.SettingsMain.route) }
            )
        }
        composable(NavRoutes.Game.route) {
            if (viewModel != null) GameScreen(viewModel = viewModel, audioViewModel = audioViewModel)
        }
        composable(NavRoutes.Chat.route, arguments = listOf(navArgument("contactId") { type = NavType.StringType })) {
        }
        composable(NavRoutes.SettingsMain.route) {
            SettingsScreen(
                onNavigateToNetwork = { navController.navigate(NavRoutes.NetworkSettings.route) },
                onNavigateToPrivacy = { navController.navigate(NavRoutes.PrivacySettings.route) },
                onNavigateToNotifications = { navController.navigate(NavRoutes.NotificationSettings.route) },
                onNavigateToProtocol = { navController.navigate(NavRoutes.ProtocolSettings.route) },
                onNavigateToDataUsage = { navController.navigate(NavRoutes.DataUsage.route) },
                onNavigateToSnooze = { navController.navigate(NavRoutes.Snooze.route) },
                onNavigateToDebugPanel = { navController.navigate(NavRoutes.DebugPanel.route) },
                onNavigateToAbout = { navController.navigate(NavRoutes.About.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.NetworkSettings.route) { NetworkSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.PrivacySettings.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val prefs = ctx.getSharedPreferences("service_prefs", android.content.Context.MODE_PRIVATE)
            PrivacySettingsScreen(
                screenSecurityEnabled = true,
                clipboardGuardEnabled = prefs.getBoolean("clipboard_guard", true),
                onToggleClipboardGuard = { prefs.edit().putBoolean("clipboard_guard", it).apply() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.NotificationSettings.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val prefs = ctx.getSharedPreferences("service_prefs", android.content.Context.MODE_PRIVATE)
            NotificationSettingsScreen(
                pushServiceEnabled = prefs.getBoolean("push_service_enabled", true),
                onTogglePushService = { enabled ->
                    prefs.edit().putBoolean("push_service_enabled", enabled).apply()
                    com.example.MyApplication.instance.restartManagedServices()
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.ProtocolSettings.route) {
            val pvm = protocolViewModel
            ProtocolSettingsScreen(
                registry = pvm?.protocolOrchestrator?.registry ?: viewModel?.protocolOrchestrator?.registry,
                autoDetector = pvm?.protocolOrchestrator?.autoDetector ?: viewModel?.protocolOrchestrator?.autoDetector,
                onBack = { navController.popBackStack() },
                onStartNode = { pvm?.startFullProtocolNode() ?: viewModel?.startFullProtocolNode() },
                onStopNode = { pvm?.stopProtocolNode() ?: viewModel?.stopProtocolNode() },
                isRunning = pvm?.protocolOrchestrator?.isStarted ?: viewModel?.protocolOrchestrator?.isStarted ?: false,
                phase = pvm?.protocolOrchestrator?.phase?.value ?: viewModel?.protocolOrchestrator?.phase?.value ?: "IDLE",
                onConfigure = {}
            )
        }
        composable(NavRoutes.DataUsage.route) { DataUsageScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.Snooze.route) { SnoozeSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.About.route) { AboutScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.ProfileEdit.route) { ProfileEditScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.BlockList.route) { BlockListScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.NetworkTest.route) { NetworkTestScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.LogExport.route) { LogExportScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.DebugPanel.route) { DebugPanel(onBack = { navController.popBackStack() }) }
        composable(NavRoutes.Onboarding.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            OnboardingScreen(onComplete = {
                ctx.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarding_complete", true).apply()
                navController.navigate(NavRoutes.Dashboard.route) {
                    popUpTo(NavRoutes.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(NavRoutes.AppLock.route) { AppLockScreen() }
        composable(NavRoutes.FilePreview.route, arguments = listOf(navArgument("fileId") { type = NavType.StringType })) {
            FilePreviewScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.MessageDetail.route, arguments = listOf(navArgument("messageId") { type = NavType.LongType })) {
            MessageDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.ContactDetail.route, arguments = listOf(navArgument("contactId") { type = NavType.StringType })) {
            ContactDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.CreateGroup.route) {
            CreateGroupScreen(contacts = emptyList(), onCreateGroup = { _, _ -> navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.GroupInfo.route, arguments = listOf(navArgument("groupId") { type = NavType.StringType })) {
            GroupInfoScreen(
                group = com.example.ui.screens.groups.GroupInfo("", "", emptyList()),
                currentUserId = "",
                onAddMember = {},
                onRemoveMember = {},
                onLeaveGroup = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.GameOver.route, arguments = listOf(navArgument("winner") { type = NavType.StringType })) {
            GameOverScreen(
                data = com.example.ui.screens.game.GameOverData(winner = "", finalScorePlayer = 0, finalScoreOpponent = 0, isPlayerWinner = false, gameDuration = 0L, moveCount = 0),
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.GameStats.route) { GameStatsScreen(onBack = { navController.popBackStack() }) }
    }
}
