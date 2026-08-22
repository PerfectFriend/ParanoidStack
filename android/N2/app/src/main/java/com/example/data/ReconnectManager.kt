/**
 * Менеджер автоматического переподключения с экспоненциальной задержкой.
 * При разрыве соединения пытается переподключиться с увеличивающейся
 * паузой (1с -> 2с -> 4с -> ... до 30с максимум).
 */
package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Автоматическое переподключение с exponential backoff */
class ReconnectManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var backoffMs = 1000L      // начальная задержка 1 сек
    private var shouldReconnect = false
    private var connected = false

    private var onReconnect: (suspend () -> Unit)? = null

    /** Установить callback переподключения */
    fun onReconnect(action: suspend () -> Unit) {
        onReconnect = action
    }

    /**
     * Запустить переподключение к серверу.
     * @param host хост для подключения
     */
    fun start(host: String) {
        shouldReconnect = true
        if (!connected) {
            job?.cancel()
            job = scope.launch {
                while (shouldReconnect) {
                    delay(backoffMs)
                    if (!shouldReconnect) break
                    onReconnect?.invoke()
                }
            }
        }
    }

    /** Остановить переподключение */
    fun stop() {
        shouldReconnect = false
        job?.cancel()
        job = null
    }

    /** Вызвать при успешном подключении (сбрасывает задержку) */
    fun onConnected() {
        connected = true
        backoffMs = 1000L    // сбрасываем задержку на минимум
        job?.cancel()
        job = null
    }

    /**
     * Вызвать при разрыве соединения.
     * Запускает цикл переподключения с экспоненциальным ростом задержки
     * (максимум 30 секунд).
     */
    fun onDisconnected() {
        connected = false
        if (shouldReconnect) {
            job?.cancel()
            job = scope.launch {
                while (shouldReconnect) {
                    onReconnect?.invoke()
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    if (!shouldReconnect) break
                    delay(backoffMs)
                }
            }
        }
    }
}
