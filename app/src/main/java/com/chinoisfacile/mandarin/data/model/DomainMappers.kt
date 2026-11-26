package com.chinoisfacile.mandarin.data.model

import com.chinoisfacile.mandarin.data.local.entity.LessonEntity
import com.chinoisfacile.mandarin.data.local.entity.UnitEntity
import com.chinoisfacile.mandarin.data.local.entity.UserStatsEntity
import com.chinoisfacile.mandarin.data.local.entity.WordEntity
import com.chinoisfacile.mandarin.data.local.entity.WordProgressEntity
import com.chinoisfacile.mandarin.domain.progression.UnitProgressionResolver
import com.chinoisfacile.mandarin.domain.srs.Sm2Scheduler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val tokensJson = Json { ignoreUnknownKeys = true }

fun UnitDto.toEntity(hskLevel: Int): UnitEntity = UnitEntity(
    id = id,
    hskLevel = hskLevel,
    orderIndex = orderIndex,
    title = title,
    description = description,
)

fun LessonDto.toEntity(
    unitId: String,
    hskLevel: Int,
    unlockedByDefault: Boolean,
): LessonEntity = LessonEntity(
    id = id,
    unitId = unitId,
    hskLevel = hskLevel,
    orderIndex = orderIndex,
    title = title,
    isUnlocked = unlockedByDefault,
    isCompleted = false,
    bestScore = 0,
    completedCount = 0,
)

fun WordDto.toEntity(hskLevel: Int): WordEntity = WordEntity(
    id = id,
    lessonId = lessonId,
    hskLevel = hskLevel,
    hanzi = hanzi,
    pinyin = pinyin,
    french = french,
    exampleHanzi = example.hanzi,
    examplePinyin = example.pinyin,
    exampleFrench = example.french,
    exampleTokensJson = tokensJson.encodeToString(example.tokens),
)

fun WordEntity.toDomain(): VocabWord = VocabWord(
    id = id,
    lessonId = lessonId,
    hskLevel = hskLevel,
    hanzi = hanzi,
    pinyin = pinyin,
    french = french,
    exampleHanzi = exampleHanzi,
    examplePinyin = examplePinyin,
    exampleFrench = exampleFrench,
    exampleTokens = tokensJson.decodeFromString<List<String>>(exampleTokensJson),
)

fun LessonEntity.toSummary(
    wordCount: Int,
    previewHanzi: List<String> = emptyList(),
    isFirstInUnit: Boolean = false,
): LessonSummary = LessonSummary(
    id = id,
    unitId = unitId,
    hskLevel = hskLevel,
    orderIndex = orderIndex,
    title = UnitProgressionResolver.formatQuizTitle(title),
    isUnlocked = isUnlocked,
    isCompleted = isCompleted,
    bestScore = bestScore,
    completedCount = completedCount,
    wordCount = wordCount,
    previewHanzi = previewHanzi,
    isFirstInUnit = isFirstInUnit,
)

fun UserStatsEntity.toDomain(): UserStats = UserStats(
    totalXp = totalXp,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastStudyEpochDay = lastStudyEpochDay,
    sessionsCompleted = sessionsCompleted,
)

fun computeMasteryLevel(progress: WordProgressEntity?): MasteryLevel {
    if (progress == null || (progress.correctCount == 0 && progress.wrongCount == 0)) {
        return MasteryLevel.NEW
    }
    return if (Sm2Scheduler.isMastered(progress.repetitions, progress.intervalDays)) {
        MasteryLevel.MASTERED
    } else {
        MasteryLevel.LEARNING
    }
}
