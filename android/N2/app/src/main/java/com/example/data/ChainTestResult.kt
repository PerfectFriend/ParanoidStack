/**
 * Результат тестирования цепочки прокси (Tor -> V2Ray).
 * Содержит информацию о доступности каждого узла и общем статусе цепочки.
 */
package com.example.data

/**
 * Результат проверки прокси-цепочки.
 *
 * @property torReachable доступен ли Tor SOCKS5 прокси
 * @property torPingMs пинг до Tor (мс), -1 если недоступен
 * @property v2rayReachable доступен ли V2Ray прокси
 * @property v2rayPingMs пинг до V2Ray (мс), -1 если недоступен
 * @property chainWorking работает ли цепочка Tor -> V2Ray целиком
 * @property errorMessage сообщение об ошибке, если тест провален
 */
data class ChainTestResult(
    val torReachable: Boolean = false,
    val torPingMs: Long = -1,
    val v2rayReachable: Boolean = false,
    val v2rayPingMs: Long = -1,
    val chainWorking: Boolean = false,
    val errorMessage: String? = null
)
