package com.chinoisfacile.mandarin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chinoisfacile.mandarin.data.model.LexiconViewMode
import com.chinoisfacile.mandarin.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesDataStore(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val showPinyinByDefault: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_PINYIN_BY_DEFAULT] ?: true
    }

    val soundEffectsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SOUND_EFFECTS_ENABLED] ?: true
    }

    val selectedHskLevel: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_SELECTED_HSK_LEVEL] ?: DEFAULT_HSK_LEVEL).coerceIn(MIN_HSK_LEVEL, MAX_HSK_LEVEL)
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val lexiconViewMode: Flow<LexiconViewMode> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_LEXICON_VIEW_MODE]
        LexiconViewMode.entries.firstOrNull { it.name == raw } ?: LexiconViewMode.GRID
    }

    val completedUnitLessons: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_COMPLETED_UNIT_LESSONS] ?: emptySet()
    }

    suspend fun getCompletedUnitLessons(): Set<String> = completedUnitLessons.first()

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

    suspend fun markUnitLessonCompleted(unitId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_COMPLETED_UNIT_LESSONS] ?: emptySet()
            prefs[KEY_COMPLETED_UNIT_LESSONS] = current + unitId
        }
    }

    suspend fun clearCompletedUnitLessons() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_COMPLETED_UNIT_LESSONS)
        }
    }

    private companion object {
        const val MIN_HSK_LEVEL = 1
        const val MAX_HSK_LEVEL = 6
        const val DEFAULT_HSK_LEVEL = 1

        val KEY_SHOW_PINYIN_BY_DEFAULT = booleanPreferencesKey("show_pinyin_by_default")
        val KEY_SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val KEY_SELECTED_HSK_LEVEL = intPreferencesKey("selected_hsk_level")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LEXICON_VIEW_MODE = stringPreferencesKey("lexicon_view_mode")
        val KEY_COMPLETED_UNIT_LESSONS = stringSetPreferencesKey("completed_unit_lessons")
        val KEY_LEGACY_SEEDED_CATALOG_VERSION = intPreferencesKey("seeded_catalog_version")

        fun seededCatalogVersionKey(hskLevel: Int): Preferences.Key<Int> =
            intPreferencesKey("seeded_catalog_version_hsk_$hskLevel")
    }
}
