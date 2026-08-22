/**
 * Пакет ViewModel — модель представления для управления профилями.
 *
 * ## Содержимое
 * - [ProfileInfo] — информация о профиле для отображения в UI.
 * - [ProfileUiState] — состояние UI экрана профилей.
 * - [ProfileViewModel] — ViewModel для переключения, создания и удаления профилей.
 */
package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Информация о профиле для отображения в UI.
 */
data class ProfileInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val isActive: Boolean,
    val isDecoy: Boolean
)

/**
 * Состояние UI экрана управления профилями.
 */
data class ProfileUiState(
    val profiles: List<ProfileInfo> = emptyList(),
    val activeProfileId: String? = null
)

/**
 * ViewModel для управления профилями пользователя.
 * Позволяет переключать, создавать и удалять профили.
 */
class ProfileViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refreshProfiles()
    }

    /** Обновляет список профилей из [ProfileManager]. */
    private fun refreshProfiles() {
        val profiles = ProfileManager.getProfiles().map { p ->
            ProfileInfo(
                id = p.id,
                name = p.name,
                createdAt = p.createdAt,
                isActive = p.isActive,
                isDecoy = p.isDecoy
            )
        }
        val activeId = profiles.find { it.isActive }?.id
        _uiState.value = ProfileUiState(profiles = profiles, activeProfileId = activeId)
    }

    /** Переключается на указанный профиль. */
    fun switchProfile(id: String) {
        ProfileManager.switchProfile(id)
        refreshProfiles()
    }

    /** Создаёт новый профиль. */
    fun createProfile(name: String) {
        ProfileManager.createProfile(name)
        refreshProfiles()
    }

    /** Удаляет профиль. */
    fun deleteProfile(id: String) {
        ProfileManager.deleteProfile(id)
        refreshProfiles()
    }
}
