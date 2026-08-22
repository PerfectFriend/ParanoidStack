/**
 * Конфигурация исчезающих сообщений.
 * Определяет доступные длительности, после которых сообщение
 * автоматически удаляется с устройств участников.
 */
package com.example.data

/**
 * Длительность до автоматического удаления сообщения.
 *
 * @property durationMs длительность в миллисекундах (0 = отключено)
 * @property displayName отображаемое название на английском
 */
enum class DisappearingDuration(val durationMs: Long, val displayName: String) {
    OFF(0, "Off"),
    FIVE_SECONDS(5000, "5 seconds"),
    THIRTY_SECONDS(30000, "30 seconds"),
    ONE_MINUTE(60000, "1 minute"),
    FIVE_MINUTES(300000, "5 minutes"),
    ONE_HOUR(3600000, "1 hour"),
    ONE_DAY(86400000, "1 day")
}
