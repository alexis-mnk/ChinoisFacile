package com.chinoisfacile.mandarin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.chinoisfacile.mandarin.data.local.AppDatabase
import com.chinoisfacile.mandarin.data.local.CatalogSeeder
import com.chinoisfacile.mandarin.data.local.UserPreferencesDataStore
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.AudioPlayer
import com.chinoisfacile.mandarin.domain.audio.SoundEffectPlayer
import com.chinoisfacile.mandarin.domain.audio.SoundEffects
import com.chinoisfacile.mandarin.domain.audio.TtsAudioPlayer
import com.chinoisfacile.mandarin.domain.lesson.ExerciseGenerator
import com.chinoisfacile.mandarin.domain.srs.Sm2Scheduler
import com.chinoisfacile.mandarin.domain.srs.StreakCalculator
import com.chinoisfacile.mandarin.ui.home.HomeViewModel
import com.chinoisfacile.mandarin.ui.lesson.LessonViewModel
import com.chinoisfacile.mandarin.ui.lexicon.LexiconViewModel
import com.chinoisfacile.mandarin.ui.settings.SettingsViewModel
import com.chinoisfacile.mandarin.ui.stats.StatsViewModel
import com.chinoisfacile.mandarin.ui.unitlesson.UnitLessonViewModel

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "chinois_facile.db",
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    private val preferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(appContext)
    }

    private val catalogSeeder: CatalogSeeder by lazy {
        CatalogSeeder(
            context = appContext,
            database = database,
            preferences = preferencesDataStore,
        )
    }

    val sm2Scheduler: Sm2Scheduler by lazy { Sm2Scheduler() }
    val streakCalculator: StreakCalculator by lazy { StreakCalculator() }
    val exerciseGenerator: ExerciseGenerator by lazy { ExerciseGenerator() }

    val repository: MandarinRepository by lazy {
        MandarinRepository(
            database = database,
            seeder = catalogSeeder,
            preferences = preferencesDataStore,
            sm2Scheduler = sm2Scheduler,
            streakCalculator = streakCalculator,
        )
    }

    val audioPlayer: AudioPlayer by lazy {
        TtsAudioPlayer(appContext)
    }

    val soundEffects: SoundEffects by lazy {
        SoundEffectPlayer(appContext)
    }

    fun homeViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }

    fun lexiconViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LexiconViewModel(repository, audioPlayer) as T
        }
    }

    fun statsViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(repository) as T
        }
    }

    fun settingsViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository, soundEffects) as T
        }
    }

    fun lessonViewModelFactory(
        lessonId: String?,
        hskLevel: Int,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LessonViewModel(
                lessonId = lessonId,
                hskLevel = hskLevel,
                repository = repository,
                exerciseGenerator = exerciseGenerator,
                audioPlayer = audioPlayer,
                soundEffects = soundEffects,
            ) as T
        }
    }

    fun unitLessonViewModelFactory(unitId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UnitLessonViewModel(
                    unitId = unitId,
                    repository = repository,
                    audioPlayer = audioPlayer,
                    soundEffects = soundEffects,
                ) as T
            }
        }
}
