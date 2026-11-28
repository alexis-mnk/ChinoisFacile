package com.chinoisfacile.mandarin.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.BackupSnapshot
import com.chinoisfacile.mandarin.data.model.ThemeMode

enum class CloudAuthDialogMode {
    SIGN_IN,
    SIGN_UP,
}

@Immutable
data class CloudAuthDialogState(
    val visible: Boolean = false,
    val mode: CloudAuthDialogMode = CloudAuthDialogMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    @StringRes val errorResId: Int? = null,
    val infoEmail: String? = null,
)

@Immutable
data class CloudBackupPrompt(
    val snapshot: BackupSnapshot,
    val isPostSignIn: Boolean,
)

@Immutable
data class CloudBackupUiState(
    val isConfigured: Boolean = false,
    val isInitializing: Boolean = false,
    val signedInEmail: String? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val isSyncing: Boolean = false,
    val isRestoring: Boolean = false,
    val isDeletingCloud: Boolean = false,
    val authDialog: CloudAuthDialogState = CloudAuthDialogState(),
    val restorePrompt: CloudBackupPrompt? = null,
    val showDeleteCloudDialog: Boolean = false,
)

@Immutable
sealed interface SettingsSnackbarMessage {
    val id: Long

    data class FromRes(
        override val id: Long,
        @StringRes val resId: Int,
        val formatArg: String? = null,
    ) : SettingsSnackbarMessage

    data class Plain(
        override val id: Long,
        val text: String,
    ) : SettingsSnackbarMessage
}

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showPinyinByDefault: Boolean = true,
    val toneColorsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val showResetDialog: Boolean = false,
    val cloud: CloudBackupUiState = CloudBackupUiState(),
    val snackbarMessage: SettingsSnackbarMessage? = null,
)
