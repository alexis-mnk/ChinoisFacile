package com.chinoisfacile.mandarin.ui.settings

import com.chinoisfacile.mandarin.data.model.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showPinyinByDefault: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val showResetDialog: Boolean = false,
    val resetSuccessEvent: Int = 0,
)
