/**
 * Результат проверки сетевого подключения.
 * Используется для диагностики доступности и задержки
 * сетевых сервисов (SMP-серверы, прокси и т.д.).
 */
package com.example.data

/**
 * Результат сетевого теста.
 *
 * @property success успешен ли тест
 * @property latencyMs задержка в миллисекундах
 * @property error сообщение об ошибке (null если успешно)
 */
data class NetworkTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val error: String? = null
)
