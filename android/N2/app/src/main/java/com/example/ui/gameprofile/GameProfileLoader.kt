package com.example.ui.gameprofile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.SecureStorage
import com.example.data.getOrNull

/**
 * Профиль игры-маскировки (decoy game profile).
 * Каждый профиль — сменная «скина» поверх основного интерфейса.
 *
 * Плагинная система: при добавлении новой игры достаточно зарегистрировать
 * [GameProfile] и экспортировать её как overlay-composable.
 *
 * @property id уникальный идентификатор профиля
 * @property name отображаемое название игры
 * @property description краткое описание
 * @property icon эмодзи-иконка (для списка выбора профиля)
 * @property overlay @Composable функция, рендерящая игровой UI поверх чата
 */
data class GameProfile(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val overlay: @Composable () -> Unit
)

/**
 * Загрузчик профилей игр-маскировки.
 * Позволяет переключать decoy-игру как скин, сохраняя выбор в SecureStorage.
 *
 * Использование:
 * ```kotlin
 * val loader = remember { GameProfileLoader(context) }
 * val currentGame = loader.currentProfile
 *
 * // В UI:
 * if (showGameOverlay) currentGame.overlay()
 * ```
 */
class GameProfileLoader(private val context: Context) {

    private val prefsKey = "active_game_profile"

    /** Список зарегистрированных игр */
    val availableProfiles: MutableList<GameProfile> = mutableListOf()

    /** Текущий активный профиль */
    var currentProfile by mutableStateOf<GameProfile?>(null)
        private set

    /** Флаг показа игрового overlay */
    var showGameOverlay by mutableStateOf(false)

    /** Инициализация — загружает сохранённый профиль */
    fun initialize(profiles: List<GameProfile>) {
        availableProfiles.clear()
        availableProfiles.addAll(profiles)

        val savedId = SecureStorage.getString(prefsKey).getOrNull()
        currentProfile = availableProfiles.find { it.id == savedId } ?: availableProfiles.firstOrNull()
    }

    /** Переключить профиль */
    fun switchProfile(profileId: String): Boolean {
        val profile = availableProfiles.find { it.id == profileId } ?: return false
        currentProfile = profile
        SecureStorage.putString(prefsKey, profileId)
        return true
    }

    /** Показать/скрыть игровой overlay */
    fun toggleOverlay() {
        showGameOverlay = !showGameOverlay
    }
}
