/**
 * Пакет навигации приложения.
 * Определяет маршруты, действия навигации и NavHost.
 *
 * ## Архитектура
 * - [NavRoutes] — запечатанный класс всех возможных маршрутов приложения.
 * - [NavigationActions] — типизированная обёртка над NavController.
 * - [AppNavHost] — главный NavHost, регистрирующий composable для всех маршрутов.
 * - [DeepLinkHandler] — обработка deep link'ов схемы simplex://.
 * - [ScreenConnector] — связывает маршруты с реальными экранами.
 */
package com.example.ui.navigation

/**
 * Запечатанный класс маршрутов навигации.
 * Каждый объект соответствует экрану приложения.
 * Некоторые маршруты содержат параметры (contactId, data).
 */
sealed class NavRoutes(val route: String) {
    object Game : NavRoutes("game")
    object Chat : NavRoutes("chat/{contactId}") {
        fun createRoute(contactId: String) = "chat/$contactId"
    }
    object Settings : NavRoutes("settings")
    object Network : NavRoutes("network")
    object Profiles : NavRoutes("profiles")
    object Radio : NavRoutes("radio")
    object ChatList : NavRoutes("chat_list")
    object QrScanner : NavRoutes("qr_scanner")
    object Debug : NavRoutes("debug")
    object QrDisplay : NavRoutes("qr_display/{data}") {
        fun createRoute(data: String) = "qr_display/$data"
    }
    // Новые экраны этапа 3 (добавить в sealed class NavRoutes)
    object Dashboard : NavRoutes("dashboard")
    object Onboarding : NavRoutes("onboarding")
    object AppLock : NavRoutes("app_lock")
    object TerminalSetup : NavRoutes("terminal_setup")
    object FilePreview : NavRoutes("file_preview/{fileId}") {
        fun createRoute(fileId: String) = "file_preview/$fileId"
    }
    object MessageDetail : NavRoutes("message_detail/{messageId}") {
        fun createRoute(messageId: Long) = "message_detail/$messageId"
    }
    object ContactDetail : NavRoutes("contact_detail/{contactId}") {
        fun createRoute(contactId: String) = "contact_detail/$contactId"
    }
    object CreateGroup : NavRoutes("create_group")
    object GroupInfo : NavRoutes("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object SettingsMain : NavRoutes("settings")
    object NetworkSettings : NavRoutes("settings/network")
    object PrivacySettings : NavRoutes("settings/privacy")
    object NotificationSettings : NavRoutes("settings/notifications")
    object About : NavRoutes("about")
    object ProfileEdit : NavRoutes("profile_edit")
    object NetworkTest : NavRoutes("network_test")
    object LogExport : NavRoutes("log_export")
    object DebugPanel : NavRoutes("debug")
    object ProtocolSettings : NavRoutes("protocol_settings")
    object DataUsage : NavRoutes("settings/data_usage")
    object Snooze : NavRoutes("settings/snooze")
    object BlockList : NavRoutes("block_list")
    object GameOver : NavRoutes("game_over/{winner}") {
        fun createRoute(winner: String) = "game_over/$winner"
    }
    object GameStats : NavRoutes("game_stats")
}
