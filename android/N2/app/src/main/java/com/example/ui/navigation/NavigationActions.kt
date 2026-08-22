/**
 * Пакет навигации — действия навигации.
 * Класс-обёртка над [NavController] для типизированных переходов.
 *
 * ## Назначение
 * [NavigationActions] предоставляет типизированные методы навигации,
 * избавляя вызывающий код от необходимости конструировать строки маршрутов вручную.
 */
package com.example.ui.navigation

import androidx.navigation.NavController

/**
 * Обёртка над [NavController], предоставляющая типизированные методы
 * для навигации между экранами приложения.
 * @param navController контроллер навигации.
 */
class NavigationActions(private val navController: NavController) {

    /** Переход на экран чата с указанным контактом. */
    fun navigateToChat(contactId: String) {
        navController.navigate(NavRoutes.Chat.createRoute(contactId))
    }
    /** Переход на экран списка чатов. */
    fun navigateToChatList() = navController.navigate(NavRoutes.ChatList.route)
    /** Переход на экран настроек. */
    fun navigateToSettings() = navController.navigate(NavRoutes.Settings.route)
    /** Переход на экран сетевых настроек. */
    fun navigateToNetwork() = navController.navigate(NavRoutes.Network.route)
    /** Переход на экран управления профилями. */
    fun navigateToProfiles() = navController.navigate(NavRoutes.Profiles.route)
    /** Переход на экран радиостанций. */
    fun navigateToRadio() = navController.navigate(NavRoutes.Radio.route)
    /** Переход на экран сканера QR-кодов. */
    fun navigateToQrScanner() = navController.navigate(NavRoutes.QrScanner.route)
    /** Переход на экран отображения QR-кода с указанными данными. */
    fun navigateToQrDisplay(data: String) = navController.navigate(NavRoutes.QrDisplay.createRoute(data))
    /** Переход на отладочную панель. */
    fun navigateToDebug() = navController.navigate(NavRoutes.Debug.route)
    /** Возврат на предыдущий экран. */
    fun goBack() = navController.popBackStack()
}
