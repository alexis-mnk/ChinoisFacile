package com.chinoisfacile.mandarin.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.ThemeMode
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingSnackbarText = when (val msg = uiState.snackbarMessage) {
        is SettingsSnackbarMessage.FromRes -> {
            if (msg.formatArg != null) {
                stringResource(msg.resId, msg.formatArg)
            } else {
                stringResource(msg.resId)
            }
        }
        is SettingsSnackbarMessage.Plain -> msg.text
        null -> null
    }

    LaunchedEffect(uiState.snackbarMessage?.id) {
        if (pendingSnackbarText != null) {
            snackbarHostState.showSnackbar(pendingSnackbarText)
        }
    }

    if (uiState.showResetDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissResetDialog,
            title = { Text(text = stringResource(R.string.settings_reset_dialog_title)) },
            text = { Text(text = stringResource(R.string.settings_reset_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::onConfirmResetProgress,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissResetDialog) {
                    Text(text = stringResource(R.string.settings_reset_cancel))
                }
            },
        )
    }

    if (uiState.cloud.authDialog.visible) {
        CloudAuthDialog(
            state = uiState.cloud.authDialog,
            onDismiss = viewModel::onDismissAuthDialog,
            onModeChange = viewModel::onChangeAuthMode,
            onEmailChange = viewModel::onAuthEmailChange,
            onPasswordChange = viewModel::onAuthPasswordChange,
            onSubmit = viewModel::onSubmitAuth,
        )
    }

    uiState.cloud.restorePrompt?.let { prompt ->
        RestoreBackupDialog(
            prompt = prompt,
            onConfirmRestore = viewModel::onConfirmRestorePrompt,
            onDismiss = viewModel::onDismissRestorePrompt,
        )
    }

    if (uiState.cloud.showDeleteCloudDialog) {
        DeleteCloudBackupDialog(
            onConfirmDelete = viewModel::onConfirmDeleteCloudBackup,
            onDismiss = viewModel::onDismissDeleteCloudDialog,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "settings_header") {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            item(key = "settings_appearance") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_appearance_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_appearance_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val options = listOf(
                            ThemeMode.SYSTEM to R.string.settings_theme_system,
                            ThemeMode.LIGHT to R.string.settings_theme_light,
                            ThemeMode.DARK to R.string.settings_theme_dark,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, (mode, labelRes) ->
                                SegmentedButton(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.onSelectThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = options.size,
                                    ),
                                ) {
                                    Text(text = stringResource(labelRes))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "settings_learning_audio") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.stats_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSwitchRow(
                            title = stringResource(R.string.stats_pinyin_default_title),
                            description = stringResource(R.string.stats_pinyin_default_desc),
                            checked = uiState.showPinyinByDefault,
                            onCheckedChange = viewModel::onToggleShowPinyinByDefault,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_tone_colors_title),
                            description = stringResource(R.string.settings_tone_colors_desc),
                            checked = uiState.toneColorsEnabled,
                            onCheckedChange = viewModel::onToggleToneColors,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ToneColorLegend()
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.stats_sound_effects_title),
                            description = stringResource(R.string.stats_sound_effects_desc),
                            checked = uiState.soundEffectsEnabled,
                            onCheckedChange = viewModel::onToggleSoundEffects,
                        )
                    }
                }
            }

            if (uiState.cloud.isConfigured) {
                item(key = "settings_cloud_backup") {
                    CloudBackupCard(
                        cloud = uiState.cloud,
                        onOpenAuthDialog = viewModel::onOpenAuthDialog,
                        onSyncNow = viewModel::onSyncNow,
                        onRestoreFromCloud = viewModel::onRequestRestoreFromCloud,
                        onSignOut = viewModel::onSignOut,
                        onDeleteCloudBackup = viewModel::onRequestDeleteCloudBackup,
                    )
                }
            }

            item(key = "settings_data") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_data_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_reset_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = viewModel::onRequestResetProgress,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.settings_reset_button))
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneColorLegend() {
    val extended = MandarinTheme.extendedColors
    val legendItems = listOf(
        1 to R.string.settings_tone_legend_1,
        2 to R.string.settings_tone_legend_2,
        3 to R.string.settings_tone_legend_3,
        4 to R.string.settings_tone_legend_4,
        5 to R.string.settings_tone_legend_5,
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        legendItems.forEach { (tone, labelRes) ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.toneColor(tone),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
