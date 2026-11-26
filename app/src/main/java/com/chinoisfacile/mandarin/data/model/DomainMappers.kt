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
    english = english,
    exampleHanzi = example.hanzi,
    examplePinyin = example.pinyin,
    exampleEnglish = example.english,
    exampleTokensJson = tokensJson.encodeToString(example.tokens),
)

fun WordEntity.toDomain(): VocabWord = VocabWord(
    id = id,
    lessonId = lessonId,
    hskLevel = hskLevel,
    hanzi = hanzi,
    pinyin = pinyin,
    english = english,
    exampleHanzi = exampleHanzi,
    examplePinyin = examplePinyin,
    exampleEnglish = exampleEnglish,
    exampleTokens = tokensJson.decodeFromString<List<String>>(exampleTokensJson),
)

fun LessonEntity.toSummary(
    wordCount: Int,
    previewHanzi: List<String> = emptyList(),
): LessonSummary = LessonSummary(
    id = id,
    unitId = unitId,
    hskLevel = hskLevel,
    orderIndex = orderIndex,
    title = UnitProgressionResolver.formatQuizTitle(title),
    stepTheme = UnitProgressionResolver.extractStepTheme(title),
    isUnlocked = isUnlocked,
    isCompleted = isCompleted,
    bestScore = bestScore,
    completedCount = completedCount,
    wordCount = wordCount,
    previewHanzi = previewHanzi,
)

fun GrammarExampleDto.toDomain(): GrammarExample = GrammarExample(
    chinese = chinese,
    pinyin = pinyin,
    english = english,
)

fun GrammarNoteDto.toDomain(): GrammarNote = GrammarNote(
    id = id,
    hskLevel = hskLevel,
    triggerWord = triggerWord,
    title = title,
    pattern = pattern,
    explanation = explanation,
    examples = examples.map { it.toDomain() },
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
