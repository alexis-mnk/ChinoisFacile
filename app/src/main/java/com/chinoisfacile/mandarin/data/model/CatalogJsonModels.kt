package com.chinoisfacile.mandarin.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
data class CatalogDto(
    val hskLevel: Int,
    val catalogVersion: Int,
    val units: List<UnitDto>,
    val words: List<WordDto>,
)

@Serializable
data class UnitDto(
    val id: String,
    val orderIndex: Int,
    val title: String,
    val description: String,
    val lessons: List<LessonDto>,
)

@Serializable
data class LessonDto(
    val id: String,
    val orderIndex: Int,
    val title: String,
)

@Serializable
data class WordDto(
    val id: String,
    val lessonId: String,
    val hanzi: String,
    val pinyin: String,
    val english: String,
    val example: ExampleSentenceDto,
)

@Serializable
data class ExampleSentenceDto(
    val hanzi: String,
    val pinyin: String,
    val english: String,
    val tokens: List<String>,
)

@Serializable
data class GrammarNotesCatalogDto(
    val notes: List<GrammarNoteDto>,
)

@Serializable
data class GrammarNoteDto(
    val id: String,
    val hskLevel: Int,
    val triggerWord: String,
    val title: String,
    val pattern: String,
    val explanation: String,
    val examples: List<GrammarExampleDto>,
)

@Serializable
data class GrammarExampleDto(
    val chinese: String,
    val pinyin: String,
    val english: String,
)

@Immutable
data class GrammarExample(
    val chinese: String,
    val pinyin: String,
    val english: String,
)

@Immutable
data class GrammarNote(
    val id: String,
    val hskLevel: Int,
    val triggerWord: String,
    val title: String,
    val pattern: String,
    val explanation: String,
    val examples: List<GrammarExample>,
)

enum class MasteryLevel {
    NEW,
    LEARNING,
    MASTERED,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class LexiconViewMode {
    GRID,
    COMPACT_LIST,
}

@Immutable
data class HskLevelMastery(
    val hskLevel: Int,
    val totalCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val dueCount: Int,
    val newCount: Int,
)

@Immutable
data class GlobalStatsOverview(
    val stats: UserStats,
    val levels: List<HskLevelMastery>,
    val totalWordsCount: Int,
    val totalMasteredCount: Int,
    val totalLearningCount: Int,
    val totalDueCount: Int,
)

@Immutable
data class VocabWord(
    val id: String,
    val lessonId: String,
    val hskLevel: Int,
    val hanzi: String,
    val pinyin: String,
    val english: String,
    val exampleHanzi: String,
    val examplePinyin: String,
    val exampleEnglish: String,
    val exampleTokens: List<String>,
)

@Immutable
data class LessonSummary(
    val id: String,
    val unitId: String,
    val hskLevel: Int,
    val orderIndex: Int,
    val title: String,
    val stepTheme: String = "",
    val isUnlocked: Boolean,
    val isCompleted: Boolean,
    val isStepLessonUnlocked: Boolean = false,
    val isStepLessonCompleted: Boolean = false,
    val bestScore: Int,
    val completedCount: Int,
    val wordCount: Int,
    val previewHanzi: List<String> = emptyList(),
)

@Immutable
data class LearningUnit(
    val id: String,
    val hskLevel: Int,
    val orderIndex: Int,
    val title: String,
    val description: String,
    val lessons: List<LessonSummary>,
    val totalWordCount: Int = lessons.sumOf { it.wordCount },
)

@Immutable
data class WordWithProgress(
    val word: VocabWord,
    val repetitions: Int,
    val easeFactor: Double,
    val intervalDays: Int,
    val nextReviewEpochDay: Long,
    val correctCount: Int,
    val wrongCount: Int,
    val masteryLevel: MasteryLevel,
)

@Immutable
data class UserStats(
    val totalXp: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastStudyEpochDay: Long? = null,
    val sessionsCompleted: Int = 0,
)

@Immutable
data class WordReviewSummary(
    val word: VocabWord,
    val intervalDays: Int,
    val masteryLevel: MasteryLevel,
)

@Immutable
data class SessionCompletionSummary(
    val earnedXp: Int,
    val accuracyPercent: Int,
    val currentStreak: Int,
    val unlockedNextStepLessonTitle: String?,
    val reviewedWords: List<WordReviewSummary>,
)
