package com.chinoisfacile.mandarin.data.local

import android.content.Context
import androidx.room.withTransaction
import com.chinoisfacile.mandarin.data.local.entity.UserStatsEntity
import com.chinoisfacile.mandarin.data.model.CatalogDto
import com.chinoisfacile.mandarin.data.model.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class CatalogSeeder(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: UserPreferencesDataStore,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    suspend fun seedIfNeeded() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val lessonDao = database.lessonDao()
                val wordDao = database.wordDao()
                val statsDao = database.userStatsDao()

                _isSeeding.value = true
                try {
                    for (level in HSK_LEVELS) {
                        val seededVersion = preferences.getSeededCatalogVersion(level)
                        val existingLessons = lessonDao.countLessonsByLevel(level)
                        val assetPath = "$CATALOG_DIR/hsk$level.json"

                        val headerVersion = readCatalogHeaderVersion(assetPath)
                        if (existingLessons > 0 && headerVersion != null && headerVersion <= seededVersion) {
                            continue
                        }

                        val rawJson = context.assets.open(assetPath)
                            .bufferedReader()
                            .use { it.readText() }
                        val catalog = json.decodeFromString<CatalogDto>(rawJson)

                        if (existingLessons > 0 && catalog.catalogVersion <= seededVersion) {
                            continue
                        }

                        val hskLevel = catalog.hskLevel
                        val unitEntities = catalog.units.map { it.toEntity(hskLevel) }
                        val minOrder = catalog.units
                            .flatMap { it.lessons }
                            .minOfOrNull { it.orderIndex } ?: 1
                        val lessonEntities = catalog.units.flatMap { unit ->
                            unit.lessons.map { lesson ->
                                lesson.toEntity(
                                    unitId = unit.id,
                                    hskLevel = hskLevel,
                                    unlockedByDefault = lesson.orderIndex == minOrder,
                                )
                            }
                        }
                        val wordEntities = catalog.words.map { it.toEntity(hskLevel) }

                        database.withTransaction {
                            lessonDao.upsertUnits(unitEntities)

                            lessonDao.insertLessonsIgnore(lessonEntities)
                            if (existingLessons > 0) {
                                lessonEntities.forEach { lesson ->
                                    lessonDao.updateLessonMetadata(
                                        id = lesson.id,
                                        unitId = lesson.unitId,
                                        hskLevel = lesson.hskLevel,
                                        orderIndex = lesson.orderIndex,
                                        title = lesson.title,
                                    )
                                }
                            }

                            val persistedLessons = lessonDao.getLessonsByLevel(hskLevel)
                                .sortedBy { it.orderIndex }
                            for (i in 0 until persistedLessons.lastIndex) {
                                val current = persistedLessons[i]
                                val next = persistedLessons[i + 1]
                                if (current.isCompleted && !next.isUnlocked) {
                                    lessonDao.unlockLesson(next.id)
                                }
                            }

                            wordDao.upsertWords(wordEntities)

                            if (statsDao.getStatsOnce() == null) {
                                statsDao.upsertStats(UserStatsEntity())
                            }
                        }

                        preferences.setSeededCatalogVersion(level, catalog.catalogVersion)
                    }
                } finally {
                    _isSeeding.value = false
                }
            }
        }
    }

    private fun readCatalogHeaderVersion(assetPath: String): Int? = runCatching {
        context.assets.open(assetPath).use { input ->
            val buffer = ByteArray(HEADER_PEEK_BYTES)
            val bytesRead = input.read(buffer)
            if (bytesRead <= 0) return@runCatching null
            val header = String(buffer, 0, bytesRead, Charsets.UTF_8)
            VERSION_REGEX.find(header)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }.getOrNull()

    companion object {
        const val CATALOG_DIR = "catalog"
        val HSK_LEVELS = 1..6
        private const val HEADER_PEEK_BYTES = 512
        private val VERSION_REGEX = Regex(""""catalogVersion"\s*:\s*(\d+)""")
    }
}
