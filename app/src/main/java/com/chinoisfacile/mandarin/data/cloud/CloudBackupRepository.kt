package com.chinoisfacile.mandarin.data.cloud

import android.util.Log
import com.chinoisfacile.mandarin.data.local.UserPreferencesDataStore
import com.chinoisfacile.mandarin.data.model.BackupJson
import com.chinoisfacile.mandarin.data.model.BackupSnapshot
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.okhttp.OkHttp
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CloudBackupRepository {
    val state: StateFlow<CloudBackupState>

    suspend fun signUp(email: String, password: String): Result<CloudAuthOutcome>
    suspend fun signIn(email: String, password: String): Result<CloudAuthOutcome>
    suspend fun signOut(): Result<Unit>
    suspend fun uploadBackup(): Result<Long>
    suspend fun fetchBackup(): Result<BackupSnapshot?>
    suspend fun restoreBackup(snapshot: BackupSnapshot): Result<Unit>
    suspend fun deleteCloudBackup(): Result<Unit>
    fun triggerAutoSyncIfSignedIn()
    fun close()
}

class NoOpCloudBackupRepository : CloudBackupRepository {
    private val _state = MutableStateFlow(CloudBackupState(isConfigured = false))
    override val state: StateFlow<CloudBackupState> = _state.asStateFlow()

    override suspend fun signUp(email: String, password: String): Result<CloudAuthOutcome> =
        Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))

    override suspend fun signIn(email: String, password: String): Result<CloudAuthOutcome> =
        Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)

    override suspend fun uploadBackup(): Result<Long> =
        Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))

    override suspend fun fetchBackup(): Result<BackupSnapshot?> =
        Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))

    override suspend fun restoreBackup(snapshot: BackupSnapshot): Result<Unit> =
        Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))

    override suspend fun deleteCloudBackup(): Result<Unit> =
        Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))

    override fun triggerAutoSyncIfSignedIn() = Unit

    override fun close() = Unit

    private companion object {
        const val NOT_CONFIGURED_MSG = "Cloud backup is not configured."
    }
}

class SupabaseCloudBackupRepository(
    supabaseUrl: String,
    supabaseAnonKey: String,
    private val mandarinRepository: MandarinRepository,
    private val preferences: UserPreferencesDataStore,
) : CloudBackupRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var lastKnownSignedIn: CloudAuthState.SignedIn? = null

    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey,
    ) {
        httpEngine = OkHttp.create {
            config {
                connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                readTimeout(READ_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                writeTimeout(READ_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            }
        }
        defaultSerializer = KotlinXSerializer(BackupJson)
        install(Auth) {
            autoLoadFromStorage = true
            autoSaveToStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)
    }

    override val state: StateFlow<CloudBackupState> = combine(
        supabase.auth.sessionStatus,
        preferences.cloudLastSyncedAtMillis,
    ) { status, lastSyncedAt ->
        val authState = when (status) {
            is SessionStatus.Authenticated -> {
                val user = status.session.user
                val userId = user?.id
                if (!userId.isNullOrBlank()) {
                    CloudAuthState.SignedIn(
                        userId = userId,
                        email = user.email.orEmpty(),
                    ).also { lastKnownSignedIn = it }
                } else {
                    CloudAuthState.SignedOut
                }
            }

            is SessionStatus.NotAuthenticated -> {
                lastKnownSignedIn = null
                CloudAuthState.SignedOut
            }

            is SessionStatus.RefreshFailure -> {
                val cachedUser = supabase.auth.currentUserOrNull()
                when {
                    cachedUser != null -> CloudAuthState.SignedIn(
                        userId = cachedUser.id,
                        email = cachedUser.email.orEmpty(),
                    ).also { lastKnownSignedIn = it }

                    lastKnownSignedIn != null -> lastKnownSignedIn!!
                    else -> CloudAuthState.SignedOut
                }
            }

            is SessionStatus.Initializing -> CloudAuthState.Initializing
        }

        CloudBackupState(
            isConfigured = true,
            authState = authState,
            lastSyncedAtEpochMillis = if (authState is CloudAuthState.SignedIn) lastSyncedAt else null,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = CloudBackupState(
            isConfigured = true,
            authState = CloudAuthState.Initializing,
            lastSyncedAtEpochMillis = null,
        ),
    )

    override suspend fun signUp(email: String, password: String): Result<CloudAuthOutcome> {
        val trimmedEmail = email.trim()
        return runSuspendCatching {
            supabase.auth.signUpWith(Email) {
                this.email = trimmedEmail
                this.password = password
            }
            val session = supabase.auth.currentSessionOrNull()
            if (session == null) {
                CloudAuthOutcome.ConfirmationEmailSent(email = trimmedEmail)
            } else {
                val user = session.user
                if (user != null) {
                    lastKnownSignedIn = CloudAuthState.SignedIn(
                        userId = user.id,
                        email = user.email ?: trimmedEmail,
                    )
                }
                CloudAuthOutcome.SignedIn(
                    email = user?.email ?: trimmedEmail,
                    existingBackup = null,
                )
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<CloudAuthOutcome> {
        val trimmedEmail = email.trim()
        return runSuspendCatching {
            supabase.auth.signInWith(Email) {
                this.email = trimmedEmail
                this.password = password
            }
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                lastKnownSignedIn = CloudAuthState.SignedIn(
                    userId = user.id,
                    email = user.email ?: trimmedEmail,
                )
            }
            val existingBackup = fetchBackup().getOrNull()
            CloudAuthOutcome.SignedIn(
                email = user?.email ?: trimmedEmail,
                existingBackup = existingBackup,
            )
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return runSuspendCatching {
            try {
                supabase.auth.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                runSuspendCatching {
                    supabase.auth.signOut(SignOutScope.LOCAL)
                }
            }
            lastKnownSignedIn = null
            preferences.setCloudLastSyncedAtMillis(null)
        }
    }

    override suspend fun uploadBackup(): Result<Long> = syncMutex.withLock {
        runSuspendCatching {
            val userId = requireSignedInUserId()
            val nowMillis = System.currentTimeMillis()
            val snapshot = mandarinRepository.exportBackupSnapshot(nowEpochMillis = nowMillis)
            val row = UserBackupRow(
                userId = userId,
                schemaVersion = snapshot.schemaVersion,
                payload = snapshot,
                updatedAt = Instant.ofEpochMilli(nowMillis).toString(),
            )
            supabase.postgrest
                .from(TABLE_USER_BACKUPS)
                .upsert(row) {
                    onConflict = COLUMN_USER_ID
                }
            preferences.setCloudLastSyncedAtMillis(nowMillis)
            nowMillis
        }
    }

    override suspend fun fetchBackup(): Result<BackupSnapshot?> = runSuspendCatching {
        val userId = requireSignedInUserId()
        val row = supabase.postgrest
            .from(TABLE_USER_BACKUPS)
            .select {
                filter {
                    eq(COLUMN_USER_ID, userId)
                }
            }
            .decodeSingleOrNull<UserBackupRow>()
            ?: return@runSuspendCatching null

        val remoteVersion = maxOf(row.schemaVersion, row.payload.schemaVersion)
        if (remoteVersion > BackupSnapshot.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedBackupSchemaException(
                remoteVersion = remoteVersion,
                maxSupportedVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
            )
        }
        row.payload
    }

    override suspend fun restoreBackup(snapshot: BackupSnapshot): Result<Unit> = syncMutex.withLock {
        runSuspendCatching {
            if (snapshot.schemaVersion > BackupSnapshot.CURRENT_SCHEMA_VERSION) {
                throw UnsupportedBackupSchemaException(
                    remoteVersion = snapshot.schemaVersion,
                    maxSupportedVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                )
            }
            mandarinRepository.restoreBackupSnapshot(snapshot)
            val syncedAt = snapshot.updatedAtEpochMillis.takeIf { it > 0L }
                ?: System.currentTimeMillis()
            preferences.setCloudLastSyncedAtMillis(syncedAt)
        }
    }

    override suspend fun deleteCloudBackup(): Result<Unit> = syncMutex.withLock {
        runSuspendCatching {
            val userId = requireSignedInUserId()
            supabase.postgrest
                .from(TABLE_USER_BACKUPS)
                .delete {
                    filter {
                        eq(COLUMN_USER_ID, userId)
                    }
                }
            preferences.setCloudLastSyncedAtMillis(null)
        }
    }

    override fun triggerAutoSyncIfSignedIn() {
        val isSignedIn = state.value.authState is CloudAuthState.SignedIn ||
            supabase.auth.currentUserOrNull() != null
        if (!isSignedIn) return

        scope.launch {
            uploadBackup().onFailure { error ->
                Log.d(TAG, "Background auto-sync skipped: ${error.message}")
            }
        }
    }

    override fun close() {
        scope.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { supabase.close() }
        }
    }

    private fun requireSignedInUserId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: (state.value.authState as? CloudAuthState.SignedIn)?.userId
            ?: lastKnownSignedIn?.userId
            ?: throw IllegalStateException("You need to sign in first.")
    }

    private inline fun <T> runSuspendCatching(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private companion object {
        const val TAG = "CloudBackupRepository"
        const val TABLE_USER_BACKUPS = "user_backups"
        const val COLUMN_USER_ID = "user_id"
        const val CONNECT_TIMEOUT_SEC = 10L
        const val READ_WRITE_TIMEOUT_SEC = 15L
    }
}
