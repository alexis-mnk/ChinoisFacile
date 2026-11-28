package com.chinoisfacile.mandarin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chinoisfacile.mandarin.data.model.BackupPreferences
import com.chinoisfacile.mandarin.data.model.LexiconViewMode
import com.chinoisfacile.mandarin.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesDataStore(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val showPinyinByDefault: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_SHOW_PINYIN_BY_DEFAULT] ?: true }
        .distinctUntilChanged()

    val toneColorsEnabled: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_TONE_COLORS_ENABLED] ?: true }
        .distinctUntilChanged()

    val soundEffectsEnabled: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_SOUND_EFFECTS_ENABLED] ?: true }
        .distinctUntilChanged()

    val selectedHskLevel: Flow<Int> = dataStore.data
        .map { prefs ->
            (prefs[KEY_SELECTED_HSK_LEVEL] ?: DEFAULT_HSK_LEVEL).coerceIn(MIN_HSK_LEVEL, MAX_HSK_LEVEL)
        }
        .distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_THEME_MODE]
            ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
        }
        .distinctUntilChanged()

    val lexiconViewMode: Flow<LexiconViewMode> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_LEXICON_VIEW_MODE]
            LexiconViewMode.entries.firstOrNull { it.name == raw } ?: LexiconViewMode.GRID
        }
        .distinctUntilChanged()

    val completedStepLessons: Flow<Set<String>> = dataStore.data
        .map { prefs -> prefs[KEY_COMPLETED_STEP_LESSONS] ?: emptySet() }
        .distinctUntilChanged()

    val studyEpochDays: Flow<Set<Long>> = dataStore.data
        .map { prefs ->
            (prefs[KEY_STUDY_EPOCH_DAYS] ?: emptySet())
                .mapNotNullTo(HashSet()) { it.toLongOrNull() }
        }
        .distinctUntilChanged()

    val sessionAccuracyTotals: Flow<Pair<Int, Int>> = dataStore.data
        .map { prefs ->
            val sum = prefs[KEY_SESSION_ACCURACY_SUM] ?: 0
            val count = prefs[KEY_SESSION_ACCURACY_COUNT] ?: 0
            sum to count
        }
        .distinctUntilChanged()

    suspend fun getCompletedStepLessons(): Set<String> = completedStepLessons.first()

    suspend fun getSeededCatalogVersion(hskLevel: Int): Int {
        val prefs = dataStore.data.first()
        val levelKey = seededCatalogVersionKey(hskLevel)
        return prefs[levelKey] ?: if (hskLevel == 1) {
            prefs[KEY_LEGACY_SEEDED_CATALOG_VERSION] ?: 0
        } else {
            0
        }
    }

    suspend fun setShowPinyinByDefault(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_PINYIN_BY_DEFAULT] = enabled
        }
    }

    suspend fun setToneColorsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TONE_COLORS_ENABLED] = enabled
        }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SOUND_EFFECTS_ENABLED] = enabled
        }
    }

    suspend fun setSelectedHskLevel(level: Int) {
        val clamped = level.coerceIn(MIN_HSK_LEVEL, MAX_HSK_LEVEL)
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_HSK_LEVEL] = clamped
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setLexiconViewMode(mode: LexiconViewMode) {
        dataStore.edit { prefs ->
            prefs[KEY_LEXICON_VIEW_MODE] = mode.name
        }
    }

    suspend fun setSeededCatalogVersion(hskLevel: Int, version: Int) {
        dataStore.edit { prefs ->
            prefs[seededCatalogVersionKey(hskLevel)] = version
        }
    }

    suspend fun markStepLessonCompleted(lessonId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_COMPLETED_STEP_LESSONS] ?: emptySet()
            prefs[KEY_COMPLETED_STEP_LESSONS] = current + lessonId
        }
    }

    suspend fun clearCompletedStepLessons() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_COMPLETED_STEP_LESSONS)
            prefs.remove(KEY_LEGACY_COMPLETED_UNIT_LESSONS)
        }
    }

    suspend fun recordSessionMetrics(
        todayEpochDay: Long,
        accuracyPercent: Int,
    ) {
        val cutoffDay = todayEpochDay - MAX_RETAINED_STUDY_DAYS
        val boundedAccuracy = accuracyPercent.coerceIn(0, 100)
        dataStore.edit { prefs ->
            val retainedDays = (prefs[KEY_STUDY_EPOCH_DAYS] ?: emptySet())
                .mapNotNull { it.toLongOrNull() }
                .filter { it >= cutoffDay }
                .toMutableSet()
            retainedDays += todayEpochDay
            prefs[KEY_STUDY_EPOCH_DAYS] = retainedDays.mapTo(HashSet()) { it.toString() }

            val prevSum = prefs[KEY_SESSION_ACCURACY_SUM] ?: 0
            val prevCount = prefs[KEY_SESSION_ACCURACY_COUNT] ?: 0
            prefs[KEY_SESSION_ACCURACY_SUM] = prevSum + boundedAccuracy
            prefs[KEY_SESSION_ACCURACY_COUNT] = prevCount + 1
        }
    }

    val cloudLastSyncedAtMillis: Flow<Long?> = dataStore.data
        .map { prefs -> prefs[KEY_CLOUD_LAST_SYNCED_AT] }
        .distinctUntilChanged()

    suspend fun clearSessionMetrics() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_STUDY_EPOCH_DAYS)
            prefs.remove(KEY_SESSION_ACCURACY_SUM)
            prefs.remove(KEY_SESSION_ACCURACY_COUNT)
        }
    }

    suspend fun setCloudLastSyncedAtMillis(epochMillis: Long?) {
        dataStore.edit { prefs ->
            if (epochMillis == null) {
                prefs.remove(KEY_CLOUD_LAST_SYNCED_AT)
            } else {
                prefs[KEY_CLOUD_LAST_SYNCED_AT] = epochMillis
            }
        }
    }

    suspend fun getBackupPreferences(): BackupPreferences {
        val prefs = dataStore.data.first()
        return BackupPreferences(
            completedStepLessons = prefs[KEY_COMPLETED_STEP_LESSONS]?.toSet() ?: emptySet(),
            studyEpochDays = (prefs[KEY_STUDY_EPOCH_DAYS] ?: emptySet())
                .filterTo(HashSet()) { it.toLongOrNull() != null },
            sessionAccuracySum = (prefs[KEY_SESSION_ACCURACY_SUM] ?: 0).coerceAtLeast(0),
            sessionAccuracyCount = (prefs[KEY_SESSION_ACCURACY_COUNT] ?: 0).coerceAtLeast(0),
        )
    }

    suspend fun restoreBackupPreferences(backupPrefs: BackupPreferences) {
        val sanitizedStudyDays = backupPrefs.studyEpochDays
            .filterTo(HashSet()) { it.toLongOrNull() != null }
        dataStore.edit { prefs ->
            if (backupPrefs.completedStepLessons.isEmpty()) {
                prefs.remove(KEY_COMPLETED_STEP_LESSONS)
            } else {
                prefs[KEY_COMPLETED_STEP_LESSONS] = backupPrefs.completedStepLessons
            }
            prefs.remove(KEY_LEGACY_COMPLETED_UNIT_LESSONS)

            if (sanitizedStudyDays.isEmpty()) {
                prefs.remove(KEY_STUDY_EPOCH_DAYS)
            } else {
                prefs[KEY_STUDY_EPOCH_DAYS] = sanitizedStudyDays
            }

            prefs[KEY_SESSION_ACCURACY_SUM] = backupPrefs.sessionAccuracySum.coerceAtLeast(0)
            prefs[KEY_SESSION_ACCURACY_COUNT] = backupPrefs.sessionAccuracyCount.coerceAtLeast(0)
        }
    }

    private companion object {
        const val MIN_HSK_LEVEL = 1
        const val MAX_HSK_LEVEL = 6
        const val DEFAULT_HSK_LEVEL = 1
        const val MAX_RETAINED_STUDY_DAYS = 30L

        val KEY_SHOW_PINYIN_BY_DEFAULT = booleanPreferencesKey("show_pinyin_by_default")
        val KEY_TONE_COLORS_ENABLED = booleanPreferencesKey("tone_colors_enabled")
        val KEY_SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val KEY_SELECTED_HSK_LEVEL = intPreferencesKey("selected_hsk_level")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LEXICON_VIEW_MODE = stringPreferencesKey("lexicon_view_mode")
        val KEY_COMPLETED_STEP_LESSONS = stringSetPreferencesKey("completed_step_lessons")
        val KEY_LEGACY_COMPLETED_UNIT_LESSONS = stringSetPreferencesKey("completed_unit_lessons")
        val KEY_LEGACY_SEEDED_CATALOG_VERSION = intPreferencesKey("seeded_catalog_version")
        val KEY_STUDY_EPOCH_DAYS = stringSetPreferencesKey("study_epoch_days")
        val KEY_SESSION_ACCURACY_SUM = intPreferencesKey("session_accuracy_sum")
        val KEY_SESSION_ACCURACY_COUNT = intPreferencesKey("session_accuracy_count")
        val KEY_CLOUD_LAST_SYNCED_AT = longPreferencesKey("cloud_last_synced_at_millis")

        fun seededCatalogVersionKey(hskLevel: Int): Preferences.Key<Int> =
            intPreferencesKey("seeded_catalog_version_hsk_$hskLevel")
    }
}
