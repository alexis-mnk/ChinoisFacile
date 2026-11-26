package com.chinoisfacile.mandarin.data.repository

import androidx.room.withTransaction
import com.chinoisfacile.mandarin.data.local.AppDatabase
import com.chinoisfacile.mandarin.data.local.CatalogSeeder
import com.chinoisfacile.mandarin.data.local.GrammarNotesLoader
import com.chinoisfacile.mandarin.data.local.UserPreferencesDataStore
import com.chinoisfacile.mandarin.data.local.entity.UserStatsEntity
import com.chinoisfacile.mandarin.data.local.entity.WordProgressEntity
import com.chinoisfacile.mandarin.data.model.GlobalStatsOverview
import com.chinoisfacile.mandarin.data.model.GrammarNote
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
import com.chinoisfacile.mandarin.domain.progression.QuickReviewEligibility
import com.chinoisfacile.mandarin.domain.progression.StepLessonCardItem
import com.chinoisfacile.mandarin.domain.progression.StepLessonDeckBuilder
import com.chinoisfacile.mandarin.domain.progression.UnitProgressionResolver
import com.chinoisfacile.mandarin.domain.srs.Sm2Scheduler
import com.chinoisfacile.mandarin.domain.srs.Sm2State
import com.chinoisfacile.mandarin.domain.srs.StreakCalculator
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

data class HomeData(
    val hskLevel: Int,
    val units: List<LearningUnit>,
    val stats: UserStats,
    val masteredCount: Int,
    val totalWordsCount: Int,
    val dueWordsCount: Int,
    val seenWordsCount: Int = 0,
)

private data class RoomHomeSnapshot(
    val homeData: HomeData,
    val wordIdToLessonId: Map<String, String>,
    val completedQuizLessonIds: Set<String>,
    val progressedWordIds: Set<String>,
)

data class SessionPayload(
    val lessonTitle: String?,
    val targetWords: List<VocabWord>,
    val distractorPool: List<VocabWord>,
    val knownTokens: Set<String> = emptySet(),
)

data class StepLessonPayload(
    val lessonId: String,
    val unitId: String,
    val hskLevel: Int,
    val orderIndex: Int,
    val stepTitle: String,
    val stepTheme: String,
    val isLessonCompleted: Boolean,
    val isQuizUnlocked: Boolean,
    val words: List<VocabWord>,
    val grammarNotes: List<GrammarNote>,
    val deckItems: List<StepLessonCardItem>,
    val exampleTokenLookup: Map<String, VocabWord> = emptyMap(),
)

class MandarinRepository(
    private val database: AppDatabase,
    private val seeder: CatalogSeeder,
    private val preferences: UserPreferencesDataStore,
    private val grammarNotesLoader: GrammarNotesLoader,
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

    fun observeToneColorsEnabled(): Flow<Boolean> = preferences.toneColorsEnabled

    suspend fun setToneColorsEnabled(enabled: Boolean) {
        preferences.setToneColorsEnabled(enabled)
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
    ): Flow<HomeData> {
        val roomHomeFlow = combine(
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
            val wordIdToLessonId = words.associate { it.id to it.lessonId }
            val completedQuizLessonIds = lessons.filter { it.isCompleted }.mapTo(HashSet()) { it.id }
            val progressedWordIds = levelProgress.mapTo(HashSet()) { it.wordId }

            RoomHomeSnapshot(
                homeData = HomeData(
                    hskLevel = hskLevel,
                    units = domainUnits,
                    stats = statsEntity?.toDomain() ?: UserStats(),
                    masteredCount = masteredCount,
                    totalWordsCount = words.size,
                    dueWordsCount = dueCount,
                ),
                wordIdToLessonId = wordIdToLessonId,
                completedQuizLessonIds = completedQuizLessonIds,
                progressedWordIds = progressedWordIds,
            )
        }

        return combine(roomHomeFlow, preferences.completedStepLessons) { snapshot, completedStepLessons ->
            val seenWordsCount = QuickReviewEligibility.collectSeenWordIds(
                wordIdToLessonId = snapshot.wordIdToLessonId,
                completedStepLessonIds = completedStepLessons,
                completedQuizLessonIds = snapshot.completedQuizLessonIds,
                progressedWordIds = snapshot.progressedWordIds,
            ).size
            snapshot.homeData.copy(
                units = UnitProgressionResolver.resolveUnits(
                    units = snapshot.homeData.units,
                    completedStepLessonIds = completedStepLessons,
                ),
                seenWordsCount = seenWordsCount,
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
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
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

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
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    suspend fun getLessonPayload(lessonId: String): SessionPayload {
        ensureSeeded()
        val lesson = lessonDao.getLessonById(lessonId)
        val targetWords = wordDao.getWordsForLesson(lessonId).map { it.toDomain() }
        val level = lesson?.hskLevel ?: targetWords.firstOrNull()?.hskLevel ?: 1
        val levelPool = wordDao.getWordsByLevel(level).map { it.toDomain() }
        val knownBelow = wordDao.getHanziBelowLevel(level)
        val knownInLevel = if (lesson != null) {
            wordDao.getHanziUpToLessonOrder(level, lesson.orderIndex)
        } else {
            emptyList()
        }
        val knownTokens = buildSet {
            addAll(knownBelow)
            addAll(knownInLevel)
            targetWords.forEach { add(it.hanzi) }
        }
        return SessionPayload(
            lessonTitle = lesson?.title?.let(UnitProgressionResolver::formatQuizTitle),
            targetWords = targetWords,
            distractorPool = levelPool,
            knownTokens = knownTokens,
        )
    }

    suspend fun getStepLessonPayload(lessonId: String): StepLessonPayload? {
        ensureSeeded()
        val lesson = lessonDao.getLessonById(lessonId) ?: return null
        val words = wordDao.getWordsForLesson(lessonId).map { it.toDomain() }
        val notesByTrigger = grammarNotesLoader.getNotesByTrigger(lesson.hskLevel)
        val deckItems = StepLessonDeckBuilder.buildDeck(
            words = words,
            notesByTrigger = notesByTrigger,
        )
        val grammarNotes = deckItems
            .filterIsInstance<StepLessonCardItem.GrammarCard>()
            .map { it.note }
        val completedStepLessons = preferences.getCompletedStepLessons()
        val isLessonCompleted = lesson.id in completedStepLessons
        val exampleTokens = words.flatMap { it.exampleTokens }.distinct()
        val exampleTokenLookup = getWordsByHanzi(exampleTokens)

        return StepLessonPayload(
            lessonId = lesson.id,
            unitId = lesson.unitId,
            hskLevel = lesson.hskLevel,
            orderIndex = lesson.orderIndex,
            stepTitle = UnitProgressionResolver.formatStepLessonTitle(lesson.orderIndex, lesson.title),
            stepTheme = UnitProgressionResolver.extractStepTheme(lesson.title),
            isLessonCompleted = isLessonCompleted,
            isQuizUnlocked = lesson.isUnlocked || lesson.isCompleted || isLessonCompleted,
            words = words,
            grammarNotes = grammarNotes,
            deckItems = deckItems,
            exampleTokenLookup = exampleTokenLookup,
        )
    }

    suspend fun getWordsByHanzi(hanziList: Collection<String>): Map<String, VocabWord> {
        val distinct = hanziList.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        if (distinct.isEmpty()) return emptyMap()

        val entities = distinct.chunked(500).flatMap { chunk ->
            wordDao.getWordsByHanzi(chunk)
        }
        return entities
            .sortedBy { it.hskLevel }
            .associate { it.hanzi to it.toDomain() }
    }

    suspend fun completeStepLesson(lessonId: String) {
        ensureSeeded()
        preferences.markStepLessonCompleted(lessonId)
        val lesson = lessonDao.getLessonById(lessonId)
        if (lesson != null && !lesson.isUnlocked) {
            lessonDao.unlockLesson(lesson.id)
        }
    }

    suspend fun getQuickReviewPayload(
        hskLevel: Int,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): SessionPayload {
        ensureSeeded()
        val levelWords = wordDao.getWordsByLevel(hskLevel).map { it.toDomain() }
        val levelLessons = lessonDao.getLessonsByLevel(hskLevel)
        val completedStepLessons = preferences.getCompletedStepLessons()
        val allProgress = progressDao.getAllProgress()

        val seenWordIds = QuickReviewEligibility.collectSeenWordIds(
            wordIdToLessonId = levelWords.associate { it.id to it.lessonId },
            completedStepLessonIds = completedStepLessons,
            completedQuizLessonIds = levelLessons.filter { it.isCompleted }.mapTo(HashSet()) { it.id },
            progressedWordIds = allProgress.mapTo(HashSet()) { it.wordId },
        )
        val seenWords = levelWords.filter { it.id in seenWordIds }
        val reviewPool = seenWords.ifEmpty {
            wordDao.getUnlockedWordsByLevel(hskLevel).map { it.toDomain() }.ifEmpty { levelWords }
        }

        val dueWordIds = progressDao.getDueProgress(todayEpochDay).map { it.wordId }
        val weakOrderedWordIds = allProgress
            .sortedWith(compareBy<WordProgressEntity> { it.easeFactor }.thenBy { it.repetitions })
            .map { it.wordId }
        val targetWords = QuickReviewEligibility.selectTargetWords(
            seenWords = reviewPool,
            dueWordIds = dueWordIds,
            weakOrderedWordIds = weakOrderedWordIds,
            maxCount = QUICK_REVIEW_TARGET_WORDS,
        )

        val knownBelow = wordDao.getHanziBelowLevel(hskLevel)
        val knownTokens = buildSet {
            addAll(knownBelow)
            reviewPool.forEach { add(it.hanzi) }
            targetWords.forEach { add(it.hanzi) }
        }

        return SessionPayload(
            lessonTitle = null,
            targetWords = targetWords,
            distractorPool = if (reviewPool.size >= 4) reviewPool else levelWords,
            knownTokens = knownTokens,
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
        val completedStepLessons = preferences.getCompletedStepLessons()

        return database.withTransaction {
            val wordIds = outcomes.map { it.wordId }.distinct()
            val existingProgress = if (wordIds.isEmpty()) {
                emptyMap()
            } else {
                progressDao.getProgressByWordIds(wordIds).associateBy { it.wordId }
            }

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

            var unlockedNextStepTitle: String? = null
            if (lessonId != null) {
                val completedLesson = lessonDao.getLessonById(lessonId)
                val wasAlreadyCompleted = completedLesson?.isCompleted == true
                lessonDao.markLessonCompleted(lessonId = lessonId, score = accuracyPercent)
                if (completedLesson != null && !wasAlreadyCompleted) {
                    val nextLesson = lessonDao.getNextLessonAfterOrder(
                        hskLevel = completedLesson.hskLevel,
                        currentOrder = completedLesson.orderIndex,
                    )
                    if (nextLesson != null) {
                        val nextAlreadyAccessible = nextLesson.id in completedStepLessons ||
                            nextLesson.isUnlocked ||
                            nextLesson.isCompleted
                        if (!nextAlreadyAccessible) {
                            unlockedNextStepTitle = UnitProgressionResolver.formatStepLessonLabel(
                                nextLesson.orderIndex,
                            )
                        }
                    }
                }
            }

            SessionCompletionSummary(
                earnedXp = earnedXp,
                accuracyPercent = accuracyPercent,
                currentStreak = streakResult.currentStreak,
                unlockedNextStepLessonTitle = unlockedNextStepTitle,
                reviewedWords = reviewSummaries,
            )
        }
    }

    suspend fun resetAllProgress() {
        seeder.invalidateVerification()
        ensureSeeded()
        database.withTransaction {
            progressDao.clearAllProgress()
            statsDao.upsertStats(UserStatsEntity())
            lessonDao.resetAllLessonsProgress()
        }
        preferences.clearCompletedStepLessons()
    }

    companion object {
        private const val QUICK_REVIEW_TARGET_WORDS = 8
    }
}
