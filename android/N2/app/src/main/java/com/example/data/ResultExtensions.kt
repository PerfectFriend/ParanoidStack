/**
 * Функции-расширения для удобной работы с [AppResult].
 * Предоставляют функциональный стиль обработки результатов:
 * map, onSuccess, onError, преобразование в success/error.
 */
package com.example.data

/** Получить значение или null в случае ошибки */
fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> data
    is AppResult.Error -> null
}

/** Получить значение или выбросить исключение в случае ошибки */
fun <T> AppResult<T>.getOrThrow(): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Error -> throw exception
}

/** Выполнить действие при успешном результате */
fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

/** Выполнить действие при ошибке */
fun <T> AppResult<T>.onError(action: (AppException) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(exception)
    return this
}

// asSuccess(), asError(), runCatchingApp() — определены в AppResult.kt
