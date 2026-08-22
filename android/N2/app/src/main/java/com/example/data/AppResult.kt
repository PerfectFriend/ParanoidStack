package com.example.data

/**
 * Пакет `com.example.data` содержит классы и утилиты для работы с данными,
 * криптографией, сетевыми протоколами и архитектурой приложения Not Gammon.
 */

/**
 * Запечатанный класс результата операции. Используется вместо исключений
 * для явного представления успеха или ошибки в функциональном стиле.
 *
 * @param T тип данных при успешном результате
 */
sealed class AppResult<out T> {
    /** Успешный результат, содержащий данные типа T */
    data class Success<T>(val data: T) : AppResult<T>()
    /** Результат с ошибкой, содержащий исключение приложения */
    data class Error(val exception: AppException) : AppResult<Nothing>()
}

/**
 * Запечатанный класс исключений приложения.
 * Позволяет классифицировать ошибки по типам: сетевые, криптографические,
 * протокольные, ошибки хранилища, таймауты, отсутствие данных и авторизации.
 */
sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Сетевая ошибка (недоступность сервера, разрыв соединения и т.д.) */
    class NetworkException(msg: String, cause: Throwable? = null) : AppException(msg, cause)
    /** Ошибка криптографических операций (расшифровка, проверка подписи) */
    class CryptoException(msg: String, cause: Throwable? = null) : AppException(msg, cause)
    /** Ошибка протокола (неверный формат, неожиданная команда) */
    class ProtocolException(msg: String, cause: Throwable? = null) : AppException(msg, cause)
    /** Ошибка хранилища (проблемы с чтением/записью) */
    class StorageException(msg: String, cause: Throwable? = null) : AppException(msg, cause)
    /** Таймаут операции */
    class TimeoutException(msg: String = "Timeout") : AppException(msg)
    /** Ресурс не найден */
    class NotFoundException(msg: String = "Not found") : AppException(msg)
    /** Ошибка авторизации */
    class UnauthorizedException(msg: String = "Unauthorized") : AppException(msg)
}

/** Вспомогательные extension-функции для удобного создания [AppResult] */
fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)

fun AppException.asError(): AppResult<Nothing> = AppResult.Error(this)

/**
 * Execute a block of code and wrap the result in [AppResult].
 * Catches [AppException] (passed through as-is) and generic [Exception] (wrapped as StorageException).
 */
inline fun <T> runCatchingApp(block: () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: AppException) {
        AppResult.Error(e)
    } catch (e: Exception) {
        AppResult.Error(AppException.StorageException(e.message ?: "Unknown error", e))
    }
}
