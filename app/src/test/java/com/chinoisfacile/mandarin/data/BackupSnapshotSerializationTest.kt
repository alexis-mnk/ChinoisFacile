package com.chinoisfacile.mandarin.data

import com.chinoisfacile.mandarin.data.cloud.UserBackupRow
import com.chinoisfacile.mandarin.data.model.BackupJson
import com.chinoisfacile.mandarin.data.model.BackupPreferences
import com.chinoisfacile.mandarin.data.model.BackupSnapshot
import com.chinoisfacile.mandarin.data.model.LessonProgressBackup
import com.chinoisfacile.mandarin.data.model.UserStatsBackup
import com.chinoisfacile.mandarin.data.model.WordProgressBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSnapshotSerializationTest {

    @Test
    fun fullRoundTrip_preservesAllFieldsAndSchemaVersion() {
        val original = BackupSnapshot(
            schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
            updatedAtEpochMillis = 1_764_300_000_123L,
            wordProgress = listOf(
                WordProgressBackup(
                    wordId = "hsk1_w001",
                    easeFactor = 2.65,
                    intervalDays = 6,
                    repetitions = 2,
                    nextReviewEpochDay = 20_420L,
                    lastReviewedEpochDay = 20_414L,
                    correctCount = 5,
                    wrongCount = 1,
                ),
                WordProgressBackup(
                    wordId = "hsk1_w002",
                    easeFactor = 2.5,
                    intervalDays = 0,
                    repetitions = 0,
                    nextReviewEpochDay = 20_414L,
                    lastReviewedEpochDay = null,
                    correctCount = 0,
                    wrongCount = 2,
                ),
            ),
            userStats = UserStatsBackup(
                currentStreak = 4,
                bestStreak = 9,
                sessionsCompleted = 14,
                lastStudyEpochDay = 20_414L,
            ),
            preferences = BackupPreferences(
                completedStepLessons = setOf("hsk1_l1", "hsk1_l2"),
                studyEpochDays = setOf("20411", "20412", "20413", "20414"),
                sessionAccuracySum = 1240,
                sessionAccuracyCount = 14,
            ),
            lessonProgress = listOf(
                LessonProgressBackup(
                    lessonId = "hsk1_l1",
                    isUnlocked = true,
                    isCompleted = true,
                    bestScore = 100,
                    completedCount = 2,
                ),
                LessonProgressBackup(
                    lessonId = "hsk1_l2",
                    isUnlocked = true,
                    isCompleted = false,
                    bestScore = 0,
                    completedCount = 0,
                ),
            ),
        )

        val encoded = BackupJson.encodeToString(BackupSnapshot.serializer(), original)
        val decoded = BackupJson.decodeFromString(BackupSnapshot.serializer(), encoded)

        assertTrue(encoded.contains("\"schemaVersion\":1"))
        assertEquals(original, decoded)
    }

    @Test
    fun decodeWithMissingOptionalFields_usesDefaults() {
        val minimalJson = """
            {
              "updatedAtEpochMillis": 1700000000000,
              "wordProgress": [
                { "wordId": "hsk1_w010" }
              ],
              "userStats": {
                "currentStreak": 2
              },
              "preferences": {
                "completedStepLessons": ["hsk1_l1"]
              }
            }
        """.trimIndent()

        val decoded = BackupJson.decodeFromString(BackupSnapshot.serializer(), minimalJson)

        assertEquals(BackupSnapshot.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1_700_000_000_000L, decoded.updatedAtEpochMillis)
        assertEquals(1, decoded.wordProgress.size)
        assertEquals(2.5, decoded.wordProgress.first().easeFactor, 0.0001)
        assertEquals(null, decoded.wordProgress.first().lastReviewedEpochDay)
        assertEquals(2, decoded.userStats.currentStreak)
        assertEquals(0, decoded.userStats.bestStreak)
        assertEquals(setOf("hsk1_l1"), decoded.preferences.completedStepLessons)
        assertEquals(emptySet<String>(), decoded.preferences.studyEpochDays)
        assertEquals(emptyList<LessonProgressBackup>(), decoded.lessonProgress)
    }

    @Test
    fun decodeWithUnknownFields_ignoresUnknownKeys() {
        val futureCompatibleJson = """
            {
              "schemaVersion": 1,
              "updatedAtEpochMillis": 1700000005000,
              "extraFutureFlag": true,
              "userStats": {
                "currentStreak": 3,
                "bestStreak": 5,
                "sessionsCompleted": 7,
                "unusedMetric": 99
              }
            }
        """.trimIndent()

        val decoded = BackupJson.decodeFromString(BackupSnapshot.serializer(), futureCompatibleJson)

        assertEquals(1, decoded.schemaVersion)
        assertEquals(3, decoded.userStats.currentStreak)
        assertEquals(5, decoded.userStats.bestStreak)
        assertEquals(7, decoded.userStats.sessionsCompleted)
    }

    @Test
    fun emptySnapshotRoundTrip_succeeds() {
        val empty = BackupSnapshot(updatedAtEpochMillis = 0L)
        val encoded = BackupJson.encodeToString(BackupSnapshot.serializer(), empty)
        val decoded = BackupJson.decodeFromString(BackupSnapshot.serializer(), encoded)
        assertEquals(empty, decoded)
    }

    @Test
    fun userBackupRow_serializesExpectedSnakeCaseKeys() {
        val snapshot = BackupSnapshot(
            schemaVersion = 1,
            updatedAtEpochMillis = 123456L,
            userStats = UserStatsBackup(currentStreak = 1, bestStreak = 1, sessionsCompleted = 1),
        )
        val row = UserBackupRow(
            userId = "user-uuid-123",
            schemaVersion = 1,
            payload = snapshot,
            updatedAt = "2026-11-27T21:00:00Z",
        )

        val json = BackupJson.encodeToString(UserBackupRow.serializer(), row)
        val decoded = BackupJson.decodeFromString(UserBackupRow.serializer(), json)

        assertTrue(json.contains("\"user_id\":\"user-uuid-123\""))
        assertTrue(json.contains("\"schema_version\":1"))
        assertTrue(json.contains("\"updated_at\":\"2026-11-27T21:00:00Z\""))
        assertEquals(row, decoded)
    }
}
