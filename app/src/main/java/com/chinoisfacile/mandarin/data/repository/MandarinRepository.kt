package com.chinoisfacile.mandarin.data.repository

import androidx.room.withTransaction
import com.chinoisfacile.mandarin.data.local.AppDatabase
import com.chinoisfacile.mandarin.data.local.CatalogSeeder
import com.chinoisfacile.mandarin.data.local.UserPreferencesDataStore
import com.chinoisfacile.mandarin.data.local.entity.UserStatsEntity
import com.chinoisfacile.mandarin.data.local.entity.WordProgressEntity
import com.chinoisfacile.mandarin.data.model.GlobalStatsOverview
import com.chinoisfacile.mandarin.data.model.HskLevelMastery
import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LexiconViewMode
import com.chinoisfacile.mandarin.data.model.MasteryLevel
import com.chinoisfacile.mandarin.data.model.SessionCompletionSummary
import com.chinoisfacile.mandarin.data.model.ThemeMode
import com.chinoisfacile.mandarin.data.model.UserStats
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.data.model.WordReviewSummary
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import com.chinoisfacile.mandarin.data.model.computeMasteryLevel
import com.chinoisfacile.mandarin.data.model.toDomain
import com.chinoisfacile.mandarin.data.model.toSummary
import com.chinoisfacile.mandarin.domain.lesson.WordSessionOutcome
import com.chinoisfacile.mandarin.domain.srs.Sm2Scheduler
import com.chinoisfacile.mandarin.domain.srs.Sm2State
import com.chinoisfacile.mandarin.domain.srs.StreakCalculator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class HomeData(
    val hskLevel: Int,
    val units: List<LearningUnit>,
    val stats: UserStats,
    val masteredCount: Int,
    val totalWordsCount: Int,
    val dueWordsCount: Int,
)

data class SessionPayload(
    val lessonTitle: String?,
    val targetWords: List<VocabWord>,
    val distractorPool: List<VocabWord>,
)

class MandarinRepository(
    private val database: AppDatabase,
    private val seeder: CatalogSeeder,
    private val preferences: UserPreferencesDataStore,
    private val sm2Scheduler: Sm2Scheduler,
    private val streakCalculator: StreakCalculator,
) {
    private val lessonDao = database.lessonDao()
    private val wordDao = database.wordDao()
    private val progressDao = database.progressDao()
    private val statsDao = database.userStatsDao()

    suspend fun ensureSeeded() {
        seeder.seedIfNeeded()
    }

    fun observeIsSeeding(): Flow<Boolean> = seeder.isSeeding

    fun observeShowPinyinByDefault(): Flow<Boolean> = preferences.showPinyinByDefault

    suspend fun setShowPinyinByDefault(enabled: Boolean) {
        preferences.setShowPinyinByDefault(enabled)
    }

    fun observeSoundEffectsEnabled(): Flow<Boolean> = preferences.soundEffectsEnabled

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        preferences.setSoundEffectsEnabled(enabled)
    }

    fun observeSelectedHskLevel(): Flow<Int> = preferences.selectedHskLevel

    suspend fun setSelectedHskLevel(level: Int) {
        preferences.setSelectedHskLevel(level)
    }

    fun observeThemeMode(): Flow<ThemeMode> = preferences.themeMode

    suspend fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun observeLexiconViewMode(): Flow<LexiconViewMode> = preferences.lexiconViewMode

    suspend fun setLexiconViewMode(mode: LexiconViewMode) {
        preferences.setLexiconViewMode(mode)
    }

    fun observeHomeData(
        hskLevel: Int,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): Flow<HomeData> = combine(
        lessonDao.observeUnitsByLevel(hskLevel),
        lessonDao.observeLessonsByLevel(hskLevel),
        wordDao.observeWordsByLevel(hskLevel),
        progressDao.observeAllProgress(),
        statsDao.observeStats(),
    ) { units, lessons, words, progressList, statsEntity ->
        val wordsByLesson = words.groupBy { it.lessonId }
        val levelWordIds = words.mapTo(HashSet()) { it.id }
        val lessonsByUnit = lessons.groupBy { it.unitId }

        val domainUnits = units.map { unit ->
            val unitLessons = lessonsByUnit[unit.id]
                .orEmpty()
                .sortedBy { it.orderIndex }
                .map { lesson ->
                    val lessonWords = wordsByLesson[lesson.id].orEmpty()
                    lesson.toSummary(
                        wordCount = lessonWords.size,
                        previewHanzi = lessonWords.map { it.hanzi },
                    )
                }
            LearningUnit(
                id = unit.id,
                hskLevel = unit.hskLevel,
                orderIndex = unit.orderIndex,
                title = unit.title,
                description = unit.description,
                lessons = unitLessons,
            )
        }

        val levelProgress = progressList.filter { it.wordId in levelWordIds }
        val masteredCount = levelProgress.count {
            Sm2Scheduler.isMastered(it.repetitions, it.intervalDays)
        }
        val dueCount = levelProgress.count { it.nextReviewEpochDay <= todayEpochDay }

        HomeData(
            hskLevel = hskLevel,
            units = domainUnits,
            stats = statsEntity?.toDomain() ?: UserStats(),
            masteredCount = masteredCount,
            totalWordsCount = words.size,
            dueWordsCount = dueCount,
        )
    }

    fun observeLexiconWords(hskLevel: Int): Flow<List<WordWithProgress>> = combine(
        wordDao.observeWordsByLevel(hskLevel),
        progressDao.observeAllProgress(),
    ) { words, progressList ->
        val progressById = progressList.associateBy { it.wordId }
        words.map { wordEntity ->
            val progress = progressById[wordEntity.id]
            WordWithProgress(
                word = wordEntity.toDomain(),
                repetitions = progress?.repetitions ?: 0,
                easeFactor = progress?.easeFactor ?: Sm2State.DEFAULT_EASE_FACTOR,
                intervalDays = progress?.intervalDays ?: 0,
                nextReviewEpochDay = progress?.nextReviewEpochDay ?: 0L,
                correctCount = progress?.correctCount ?: 0,
                wrongCount = progress?.wrongCount ?: 0,
                masteryLevel = computeMasteryLevel(progress),
            )
        }
    }

    fun observeGlobalStatsOverview(
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): Flow<GlobalStatsOverview> = combine(
        wordDao.observeAllWordLevelRefs(),
        progressDao.observeAllProgress(),
        statsDao.observeStats(),
    ) { wordRefs, progressList, statsEntity ->
        val progressById = progressList.associateBy { it.wordId }
        val refsByLevel = wordRefs.groupBy { it.hskLevel }

        val levelSummaries = (1..6).map { level ->
            val levelRefs = refsByLevel[level].orEmpty()
            var mastered = 0
            var learning = 0
            var due = 0
            var newCount = 0

            for (ref in levelRefs) {
                val progress = progressById[ref.id]
                when (computeMasteryLevel(progress)) {
                    MasteryLevel.MASTERED -> {
                        mastered += 1
                        if (progress != null && progress.nextReviewEpochDay <= todayEpochDay) {
                            due += 1
                        }
                    }
                    MasteryLevel.LEARNING -> {
                        learning += 1
                        if (progress != null && progress.nextReviewEpochDay <= todayEpochDay) {
                            due += 1
                        }
                    }
                    MasteryLevel.NEW -> newCount += 1
                }
            }

            HskLevelMastery(
                hskLevel = level,
                totalCount = levelRefs.size,
                masteredCount = mastered,
                learningCount = learning,
                dueCount = due,
                newCount = newCount,
            )
        }

        GlobalStatsOverview(
            stats = statsEntity?.toDomain() ?: UserStats(),
            levels = levelSummaries,
            totalWordsCount = levelSummaries.sumOf { it.totalCount },
            totalMasteredCount = levelSummaries.sumOf { it.masteredCount },
            totalLearningCount = levelSummaries.sumOf { it.learningCount },
            totalDueCount = levelSummaries.sumOf { it.dueCount },
        )
    }

    suspend fun getLessonPayload(lessonId: String): SessionPayload {
        ensureSeeded()
        val lesson = lessonDao.getLessonById(lessonId)
        val targetWords = wordDao.getWordsForLesson(lessonId).map { it.toDomain() }
        val level = lesson?.hskLevel ?: targetWords.firstOrNull()?.hskLevel ?: 1
        val levelPool = wordDao.getWordsByLevel(level).map { it.toDomain() }
        return SessionPayload(
            lessonTitle = lesson?.title,
            targetWords = targetWords,
            distractorPool = levelPool,
        )
    }

    suspend fun getQuickReviewPayload(
        hskLevel: Int,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): SessionPayload {
        ensureSeeded()
        val levelWords = wordDao.getWordsByLevel(hskLevel).map { it.toDomain() }
        val unlockedWords = wordDao.getUnlockedWordsByLevel(hskLevel).map { it.toDomain() }
        val wordById = levelWords.associateBy { it.id }

        val dueWords = progressDao.getDueProgress(todayEpochDay)
            .mapNotNull { wordById[it.wordId] }

        val seenWeakWords = progressDao.getAllProgress()
            .sortedWith(compareBy<WordProgressEntity> { it.easeFactor }.thenBy { it.repetitions })
            .mapNotNull { wordById[it.wordId] }

        val fallbackPool = unlockedWords.ifEmpty { levelWords }
        val combined = (dueWords + seenWeakWords + fallbackPool)
            .distinctBy { it.id }
            .take(QUICK_REVIEW_TARGET_WORDS)

        return SessionPayload(
            lessonTitle = null,
            targetWords = combined,
            distractorPool = levelWords,
        )
    }

    suspend fun recordSessionCompletion(
        lessonId: String?,
        outcomes: List<WordSessionOutcome>,
        earnedXp: Int,
        accuracyPercent: Int,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): SessionCompletionSummary {
        ensureSeeded()
        val allWordsById = wordDao.getAllWords().associateBy { it.id }

        return database.withTransaction {
            val wordIds = outcomes.map { it.wordId }.distinct()
            val existingProgress = progressDao.getProgressByWordIds(wordIds).associateBy { it.wordId }

            val updatedEntities = mutableListOf<WordProgressEntity>()
            val reviewSummaries = mutableListOf<WordReviewSummary>()

            for (outcome in outcomes) {
                val previous = existingProgress[outcome.wordId]
                val currentState = Sm2State(
                    repetitions = previous?.repetitions ?: 0,
                    easeFactor = previous?.easeFactor ?: Sm2State.DEFAULT_EASE_FACTOR,
                    intervalDays = previous?.intervalDays ?: 0,
                    nextReviewEpochDay = previous?.nextReviewEpochDay ?: todayEpochDay,
                )
                val quality = Sm2Scheduler.qualityForOutcome(
                    hadMistake = outcome.wrongAttempts > 0,
                    answeredWithoutPinyin = outcome.answeredWithoutPinyin,
                )
                val nextState = sm2Scheduler.calculateNextReview(
                    current = currentState,
                    quality = quality,
                    todayEpochDay = todayEpochDay,
                )

                val updated = WordProgressEntity(
                    wordId = outcome.wordId,
                    repetitions = nextState.repetitions,
                    easeFactor = nextState.easeFactor,
                    intervalDays = nextState.intervalDays,
                    nextReviewEpochDay = nextState.nextReviewEpochDay,
                    lastReviewedEpochDay = todayEpochDay,
                    correctCount = (previous?.correctCount ?: 0) + outcome.correctAttempts,
                    wrongCount = (previous?.wrongCount ?: 0) + outcome.wrongAttempts,
                )
                updatedEntities += updated

                allWordsById[outcome.wordId]?.let { wordEntity ->
                    reviewSummaries += WordReviewSummary(
                        word = wordEntity.toDomain(),
                        intervalDays = updated.intervalDays,
                        masteryLevel = computeMasteryLevel(updated),
                    )
                }
            }

            if (updatedEntities.isNotEmpty()) {
                progressDao.upsertProgress(updatedEntities)
            }

            val currentStats = statsDao.getStatsOnce() ?: UserStatsEntity()
            val streakResult = streakCalculator.updateStreak(
                currentStreak = currentStats.currentStreak,
                longestStreak = currentStats.longestStreak,
                lastStudyEpochDay = currentStats.lastStudyEpochDay,
                todayEpochDay = todayEpochDay,
            )
            statsDao.upsertStats(
                currentStats.copy(
                    totalXp = currentStats.totalXp + earnedXp,
                    currentStreak = streakResult.currentStreak,
                    longestStreak = streakResult.longestStreak,
                    lastStudyEpochDay = streakResult.lastStudyEpochDay,
                    sessionsCompleted = currentStats.sessionsCompleted + 1,
                ),
            )

            var unlockedNextTitle: String? = null
            if (lessonId != null) {
                val completedLesson = lessonDao.getLessonById(lessonId)
                lessonDao.markLessonCompleted(lessonId = lessonId, score = accuracyPercent)
                if (completedLesson != null) {
                    val nextLesson = lessonDao.getNextLessonAfterOrder(
                        hskLevel = completedLesson.hskLevel,
                        currentOrder = completedLesson.orderIndex,
                    )
                    if (nextLesson != null && !nextLesson.isUnlocked) {
                        lessonDao.unlockLesson(nextLesson.id)
                        unlockedNextTitle = nextLesson.title
                    }
                }
            }

            SessionCompletionSummary(
                earnedXp = earnedXp,
                accuracyPercent = accuracyPercent,
                currentStreak = streakResult.currentStreak,
                unlockedNextLessonTitle = unlockedNextTitle,
                reviewedWords = reviewSummaries,
            )
        }
    }

    suspend fun resetAllProgress() {
        ensureSeeded()
        database.withTransaction {
            progressDao.clearAllProgress()
            statsDao.upsertStats(UserStatsEntity())
            lessonDao.resetAllLessonsProgress()
        }
    }

    companion object {
        private const val QUICK_REVIEW_TARGET_WORDS = 8
    }
}
