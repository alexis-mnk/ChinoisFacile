package com.chinoisfacile.mandarin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.model.ThemeMode
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.SoundEffects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: MandarinRepository,
    private val soundEffects: SoundEffects,
) : ViewModel() {

    private val showResetDialog = MutableStateFlow(false)
    private val resetSuccessEvent = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeThemeMode(),
        repository.observeShowPinyinByDefault(),
        repository.observeSoundEffectsEnabled(),
        showResetDialog,
        resetSuccessEvent,
    ) { themeMode, showPinyin, soundEffectsEnabled, dialogVisible, resetCount ->
        SettingsUiState(
            themeMode = themeMode,
            showPinyinByDefault = showPinyin,
            soundEffectsEnabled = soundEffectsEnabled,
            showResetDialog = dialogVisible,
            resetSuccessEvent = resetCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun onSelectThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun onToggleShowPinyinByDefault(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShowPinyinByDefault(enabled)
        }
    }

    fun onToggleSoundEffects(enabled: Boolean) {
        if (enabled) {
            soundEffects.playCorrect()
        }
        viewModelScope.launch {
            repository.setSoundEffectsEnabled(enabled)
        }
    }

    fun onRequestResetProgress() {
        showResetDialog.value = true
    }

    fun onDismissResetDialog() {
        showResetDialog.value = false
    }

    fun onConfirmResetProgress() {
        showResetDialog.value = false
        viewModelScope.launch {
            repository.resetAllProgress()
            resetSuccessEvent.update { it + 1 }
        }
    }
}
