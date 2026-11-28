package com.chinoisfacile.mandarin.data.cloud

import com.chinoisfacile.mandarin.data.model.BackupSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserBackupRow(
    @SerialName("user_id") val userId: String,
    @SerialName("schema_version") val schemaVersion: Int = BackupSnapshot.CURRENT_SCHEMA_VERSION,
    @SerialName("payload") val payload: BackupSnapshot,
    @SerialName("updated_at") val updatedAt: String,
)

sealed interface CloudAuthState {
    data object Initializing : CloudAuthState
    data object SignedOut : CloudAuthState
    data class SignedIn(
        val userId: String,
        val email: String,
    ) : CloudAuthState
}

data class CloudBackupState(
    val isConfigured: Boolean = false,
    val authState: CloudAuthState = CloudAuthState.SignedOut,
    val lastSyncedAtEpochMillis: Long? = null,
)

sealed interface CloudAuthOutcome {
    data class SignedIn(
        val email: String,
        val existingBackup: BackupSnapshot?,
    ) : CloudAuthOutcome

    data class ConfirmationEmailSent(
        val email: String,
    ) : CloudAuthOutcome
}

class UnsupportedBackupSchemaException(
    val remoteVersion: Int,
    val maxSupportedVersion: Int = BackupSnapshot.CURRENT_SCHEMA_VERSION,
) : Exception(
    "Unsupported backup schema version $remoteVersion (max supported: $maxSupportedVersion).",
)
