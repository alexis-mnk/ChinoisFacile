package com.chinoisfacile.mandarin.ui.settings

import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.ThemeMode

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showPinyinByDefault: Boolean = true,
    val toneColorsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val showResetDialog: Boolean = false,
    val resetSuccessEvent: Int = 0,
)
