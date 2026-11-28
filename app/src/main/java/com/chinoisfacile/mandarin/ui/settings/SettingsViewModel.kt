package com.chinoisfacile.mandarin.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.cloud.CloudAuthOutcome
import com.chinoisfacile.mandarin.data.cloud.CloudAuthState
import com.chinoisfacile.mandarin.data.cloud.CloudBackupRepository
import com.chinoisfacile.mandarin.data.cloud.CloudErrorMapper
import com.chinoisfacile.mandarin.data.cloud.NoOpCloudBackupRepository
import com.chinoisfacile.mandarin.data.model.ThemeMode
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.SoundEffects
import java.util.concurrent.atomic.AtomicLong
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
    private val cloudBackupRepository: CloudBackupRepository = NoOpCloudBackupRepository(),
) : ViewModel() {

    private val showResetDialog = MutableStateFlow(false)
    private val cloudInteraction = MutableStateFlow(CloudInteractionState())
    private val snackbarMessage = MutableStateFlow<SettingsSnackbarMessage?>(null)
    private val snackbarIdGenerator = AtomicLong(0L)

    private val preferencesFlow = combine(
        repository.observeThemeMode(),
        repository.observeShowPinyinByDefault(),
        repository.observeToneColorsEnabled(),
        repository.observeSoundEffectsEnabled(),
    ) { themeMode, showPinyin, toneColorsEnabled, soundEffectsEnabled ->
        SettingsPreferences(
            themeMode = themeMode,
            showPinyinByDefault = showPinyin,
            toneColorsEnabled = toneColorsEnabled,
            soundEffectsEnabled = soundEffectsEnabled,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesFlow,
        showResetDialog,
        cloudBackupRepository.state,
        cloudInteraction,
        snackbarMessage,
    ) { prefs, resetDialogVisible, cloudState, interaction, currentSnackbar ->
        val signedInEmail = (cloudState.authState as? CloudAuthState.SignedIn)?.email
        SettingsUiState(
            themeMode = prefs.themeMode,
            showPinyinByDefault = prefs.showPinyinByDefault,
            toneColorsEnabled = prefs.toneColorsEnabled,
            soundEffectsEnabled = prefs.soundEffectsEnabled,
            showResetDialog = resetDialogVisible,
            cloud = CloudBackupUiState(
                isConfigured = cloudState.isConfigured,
                isInitializing = cloudState.authState is CloudAuthState.Initializing,
                signedInEmail = signedInEmail,
                lastSyncedAtEpochMillis = cloudState.lastSyncedAtEpochMillis,
                isSyncing = interaction.isSyncing,
                isRestoring = interaction.isRestoring,
                isDeletingCloud = interaction.isDeletingCloud,
                authDialog = interaction.authDialog,
                restorePrompt = interaction.restorePrompt,
                showDeleteCloudDialog = interaction.showDeleteCloudDialog,
            ),
            snackbarMessage = currentSnackbar,
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

    fun onToggleToneColors(enabled: Boolean) {
        viewModelScope.launch {
            repository.setToneColorsEnabled(enabled)
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
            emitSnackbar(R.string.settings_reset_done)
        }
    }

    fun onOpenAuthDialog() {
        cloudInteraction.update {
            it.copy(
                authDialog = CloudAuthDialogState(visible = true),
            )
        }
    }

    fun onDismissAuthDialog() {
        if (cloudInteraction.value.authDialog.isSubmitting) return
        cloudInteraction.update {
            it.copy(authDialog = CloudAuthDialogState(visible = false))
        }
    }

    fun onChangeAuthMode(mode: CloudAuthDialogMode) {
        cloudInteraction.update { state ->
            state.copy(
                authDialog = state.authDialog.copy(
                    mode = mode,
                    errorMessage = null,
                    errorResId = null,
                    infoEmail = null,
                ),
            )
        }
    }

    fun onAuthEmailChange(email: String) {
        cloudInteraction.update { state ->
            state.copy(
                authDialog = state.authDialog.copy(
                    email = email,
                    errorMessage = null,
                    errorResId = null,
                ),
            )
        }
    }

    fun onAuthPasswordChange(password: String) {
        cloudInteraction.update { state ->
            state.copy(
                authDialog = state.authDialog.copy(
                    password = password,
                    errorMessage = null,
                    errorResId = null,
                ),
            )
        }
    }

    fun onSubmitAuth() {
        val dialog = cloudInteraction.value.authDialog
        if (dialog.isSubmitting) return

        val trimmedEmail = dialog.email.trim()
        if (!isValidEmail(trimmedEmail)) {
            cloudInteraction.update { state ->
                state.copy(
                    authDialog = state.authDialog.copy(
                        errorResId = R.string.settings_cloud_error_invalid_email,
                        errorMessage = null,
                    ),
                )
            }
            return
        }
        if (dialog.password.length < MIN_PASSWORD_LENGTH) {
            cloudInteraction.update { state ->
                state.copy(
                    authDialog = state.authDialog.copy(
                        errorResId = R.string.settings_cloud_error_short_password,
                        errorMessage = null,
                    ),
                )
            }
            return
        }

        cloudInteraction.update { state ->
            state.copy(
                authDialog = state.authDialog.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    errorResId = null,
                    infoEmail = null,
                ),
            )
        }

        viewModelScope.launch {
            val result = when (dialog.mode) {
                CloudAuthDialogMode.SIGN_IN ->
                    cloudBackupRepository.signIn(trimmedEmail, dialog.password)

                CloudAuthDialogMode.SIGN_UP ->
                    cloudBackupRepository.signUp(trimmedEmail, dialog.password)
            }

            result.onSuccess { outcome ->
                when (outcome) {
                    is CloudAuthOutcome.SignedIn -> {
                        val existingBackup = outcome.existingBackup
                        cloudInteraction.update { state ->
                            state.copy(
                                authDialog = CloudAuthDialogState(visible = false),
                                restorePrompt = existingBackup?.let {
                                    CloudBackupPrompt(snapshot = it, isPostSignIn = true)
                                },
                            )
                        }
                        if (existingBackup == null) {
                            val resId = if (dialog.mode == CloudAuthDialogMode.SIGN_UP) {
                                R.string.settings_cloud_snackbar_account_created
                            } else {
                                R.string.settings_cloud_snackbar_signed_in
                            }
                            emitSnackbar(resId, outcome.email)
                        }
                    }

                    is CloudAuthOutcome.ConfirmationEmailSent -> {
                        cloudInteraction.update { state ->
                            state.copy(
                                authDialog = state.authDialog.copy(
                                    mode = CloudAuthDialogMode.SIGN_IN,
                                    password = "",
                                    isSubmitting = false,
                                    errorMessage = null,
                                    errorResId = null,
                                    infoEmail = outcome.email,
                                ),
                            )
                        }
                    }
                }
            }.onFailure { error ->
                cloudInteraction.update { state ->
                    state.copy(
                        authDialog = state.authDialog.copy(
                            isSubmitting = false,
                            errorMessage = CloudErrorMapper.toUserMessage(error),
                            errorResId = null,
                        ),
                    )
                }
            }
        }
    }

    fun onSyncNow() {
        if (cloudInteraction.value.isSyncing) return
        cloudInteraction.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            cloudBackupRepository.uploadBackup()
                .onSuccess {
                    emitSnackbar(R.string.settings_cloud_snackbar_synced)
                }
                .onFailure { error ->
                    emitSnackbarText(CloudErrorMapper.toUserMessage(error))
                }
            cloudInteraction.update { it.copy(isSyncing = false) }
        }
    }

    fun onRequestRestoreFromCloud() {
        if (cloudInteraction.value.isRestoring) return
        cloudInteraction.update { it.copy(isRestoring = true) }
        viewModelScope.launch {
            cloudBackupRepository.fetchBackup()
                .onSuccess { snapshot ->
                    if (snapshot == null) {
                        cloudInteraction.update { it.copy(isRestoring = false) }
                        emitSnackbar(R.string.settings_cloud_snackbar_no_backup)
                    } else if (repository.hasLocalProgress()) {
                        cloudInteraction.update {
                            it.copy(
                                isRestoring = false,
                                restorePrompt = CloudBackupPrompt(
                                    snapshot = snapshot,
                                    isPostSignIn = false,
                                ),
                            )
                        }
                    } else {
                        cloudBackupRepository.restoreBackup(snapshot)
                            .onSuccess {
                                emitSnackbar(R.string.settings_cloud_snackbar_restored)
                            }
                            .onFailure { error ->
                                emitSnackbarText(CloudErrorMapper.toUserMessage(error))
                            }
                        cloudInteraction.update { it.copy(isRestoring = false) }
                    }
                }
                .onFailure { error ->
                    cloudInteraction.update { it.copy(isRestoring = false) }
                    emitSnackbarText(CloudErrorMapper.toUserMessage(error))
                }
        }
    }

    fun onConfirmRestorePrompt() {
        val prompt = cloudInteraction.value.restorePrompt ?: return
        cloudInteraction.update {
            it.copy(restorePrompt = null, isRestoring = true)
        }
        viewModelScope.launch {
            cloudBackupRepository.restoreBackup(prompt.snapshot)
                .onSuccess {
                    emitSnackbar(R.string.settings_cloud_snackbar_restored)
                }
                .onFailure { error ->
                    emitSnackbarText(CloudErrorMapper.toUserMessage(error))
                }
            cloudInteraction.update { it.copy(isRestoring = false) }
        }
    }

    fun onDismissRestorePrompt() {
        val prompt = cloudInteraction.value.restorePrompt ?: return
        cloudInteraction.update { it.copy(restorePrompt = null) }
        if (prompt.isPostSignIn) {
            val email = uiState.value.cloud.signedInEmail
            if (!email.isNullOrBlank()) {
                emitSnackbar(R.string.settings_cloud_snackbar_signed_in, email)
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            cloudBackupRepository.signOut()
                .onSuccess {
                    emitSnackbar(R.string.settings_cloud_snackbar_signed_out)
                }
                .onFailure { error ->
                    emitSnackbarText(CloudErrorMapper.toUserMessage(error))
                }
        }
    }

    fun onRequestDeleteCloudBackup() {
        cloudInteraction.update { it.copy(showDeleteCloudDialog = true) }
    }

    fun onDismissDeleteCloudDialog() {
        if (cloudInteraction.value.isDeletingCloud) return
        cloudInteraction.update { it.copy(showDeleteCloudDialog = false) }
    }

    fun onConfirmDeleteCloudBackup() {
        if (cloudInteraction.value.isDeletingCloud) return
        cloudInteraction.update {
            it.copy(showDeleteCloudDialog = false, isDeletingCloud = true)
        }
        viewModelScope.launch {
            cloudBackupRepository.deleteCloudBackup()
                .onSuccess {
                    emitSnackbar(R.string.settings_cloud_snackbar_deleted)
                }
                .onFailure { error ->
                    emitSnackbarText(CloudErrorMapper.toUserMessage(error))
                }
            cloudInteraction.update { it.copy(isDeletingCloud = false) }
        }
    }

    private fun emitSnackbar(@StringRes resId: Int, formatArg: String? = null) {
        snackbarMessage.value = SettingsSnackbarMessage.FromRes(
            id = snackbarIdGenerator.incrementAndGet(),
            resId = resId,
            formatArg = formatArg,
        )
    }

    private fun emitSnackbarText(message: String) {
        snackbarMessage.value = SettingsSnackbarMessage.Plain(
            id = snackbarIdGenerator.incrementAndGet(),
            text = message,
        )
    }

    private fun isValidEmail(email: String): Boolean {
        val atIndex = email.indexOf('@')
        val dotIndex = email.lastIndexOf('.')
        return email.isNotEmpty() &&
            !email.contains(' ') &&
            atIndex > 0 &&
            dotIndex > atIndex + 1 &&
            dotIndex < email.lastIndex
    }

    private data class SettingsPreferences(
        val themeMode: ThemeMode,
        val showPinyinByDefault: Boolean,
        val toneColorsEnabled: Boolean,
        val soundEffectsEnabled: Boolean,
    )

    private data class CloudInteractionState(
        val isSyncing: Boolean = false,
        val isRestoring: Boolean = false,
        val isDeletingCloud: Boolean = false,
        val authDialog: CloudAuthDialogState = CloudAuthDialogState(),
        val restorePrompt: CloudBackupPrompt? = null,
        val showDeleteCloudDialog: Boolean = false,
    )

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}
