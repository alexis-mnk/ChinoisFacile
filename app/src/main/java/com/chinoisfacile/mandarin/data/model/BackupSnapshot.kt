package com.chinoisfacile.mandarin.data.model

import com.chinoisfacile.mandarin.data.local.entity.LessonEntity
import com.chinoisfacile.mandarin.data.local.entity.UserStatsEntity
import com.chinoisfacile.mandarin.data.local.entity.WordProgressEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val updatedAtEpochMillis: Long = 0L,
    val wordProgress: List<WordProgressBackup> = emptyList(),
    val userStats: UserStatsBackup = UserStatsBackup(),
    val preferences: BackupPreferences = BackupPreferences(),
    val lessonProgress: List<LessonProgressBackup> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class WordProgressBackup(
    val wordId: String,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextReviewEpochDay: Long = 0L,
    val lastReviewedEpochDay: Long? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
)

@Serializable
data class UserStatsBackup(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val sessionsCompleted: Int = 0,
    val lastStudyEpochDay: Long? = null,
)

@Serializable
data class BackupPreferences(
    val completedStepLessons: Set<String> = emptySet(),
    val studyEpochDays: Set<String> = emptySet(),
    val sessionAccuracySum: Int = 0,
    val sessionAccuracyCount: Int = 0,
)

@Serializable
data class LessonProgressBackup(
    val lessonId: String,
    val isUnlocked: Boolean = false,
    val isCompleted: Boolean = false,
    val bestScore: Int = 0,
    val completedCount: Int = 0,
)

val BackupJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun WordProgressEntity.toBackup(): WordProgressBackup = WordProgressBackup(
    wordId = wordId,
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    repetitions = repetitions,
    nextReviewEpochDay = nextReviewEpochDay,
    lastReviewedEpochDay = lastReviewedEpochDay,
    correctCount = correctCount,
    wrongCount = wrongCount,
)

fun WordProgressBackup.toEntity(): WordProgressEntity = WordProgressEntity(
    wordId = wordId,
    repetitions = repetitions,
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    nextReviewEpochDay = nextReviewEpochDay,
    lastReviewedEpochDay = lastReviewedEpochDay,
    correctCount = correctCount,
    wrongCount = wrongCount,
)

fun UserStatsEntity.toBackup(): UserStatsBackup = UserStatsBackup(
    currentStreak = currentStreak,
    bestStreak = longestStreak,
    sessionsCompleted = sessionsCompleted,
    lastStudyEpochDay = lastStudyEpochDay,
)

fun UserStatsBackup.toEntity(): UserStatsEntity = UserStatsEntity(
    id = 1,
    totalXp = 0,
    currentStreak = currentStreak.coerceAtLeast(0),
    longestStreak = bestStreak.coerceAtLeast(0),
    lastStudyEpochDay = lastStudyEpochDay,
    sessionsCompleted = sessionsCompleted.coerceAtLeast(0),
)

fun LessonEntity.toBackupOrNull(): LessonProgressBackup? {
    if (!isUnlocked && !isCompleted && bestScore <= 0 && completedCount <= 0) {
        return null
    }
    return LessonProgressBackup(
        lessonId = id,
        isUnlocked = isUnlocked,
        isCompleted = isCompleted,
        bestScore = bestScore,
        completedCount = completedCount,
    )
}
