package com.chinoisfacile.mandarin.data

import com.chinoisfacile.mandarin.data.local.entity.LessonEntity
import com.chinoisfacile.mandarin.data.local.entity.UserStatsEntity
import com.chinoisfacile.mandarin.data.local.entity.WordProgressEntity
import com.chinoisfacile.mandarin.data.model.toBackup
import com.chinoisfacile.mandarin.data.model.toBackupOrNull
import com.chinoisfacile.mandarin.data.model.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSnapshotMapperTest {

    @Test
    fun wordProgressRoundTrip_preservesAllFields() {
        val entity = WordProgressEntity(
            wordId = "hsk2_w042",
            repetitions = 4,
            easeFactor = 2.42,
            intervalDays = 14,
            nextReviewEpochDay = 20_450L,
            lastReviewedEpochDay = 20_436L,
            correctCount = 9,
            wrongCount = 2,
        )

        val backup = entity.toBackup()
        val restored = backup.toEntity()

        assertEquals(entity, restored)
    }

    @Test
    fun userStatsMapping_mapsLongestStreakToBestStreakAndResetsTotalXp() {
        val entity = UserStatsEntity(
            id = 1,
            totalXp = 999,
            currentStreak = 5,
            longestStreak = 12,
            lastStudyEpochDay = 20_414L,
            sessionsCompleted = 22,
        )

        val backup = entity.toBackup()
        assertEquals(5, backup.currentStreak)
        assertEquals(12, backup.bestStreak)
        assertEquals(22, backup.sessionsCompleted)
        assertEquals(20_414L, backup.lastStudyEpochDay)

        val restored = backup.toEntity()
        assertEquals(1, restored.id)
        assertEquals(0, restored.totalXp)
        assertEquals(5, restored.currentStreak)
        assertEquals(12, restored.longestStreak)
        assertEquals(22, restored.sessionsCompleted)
        assertEquals(20_414L, restored.lastStudyEpochDay)
    }

    @Test
    fun lessonToBackupOrNull_ignoresUntouchedLessonsAndPreservesProgressedLessons() {
        val untouched = LessonEntity(
            id = "hsk1_l5",
            unitId = "hsk1_u2",
            hskLevel = 1,
            orderIndex = 5,
            title = "Quiz 5",
            isUnlocked = false,
            isCompleted = false,
            bestScore = 0,
            completedCount = 0,
        )
        val completed = untouched.copy(
            id = "hsk1_l1",
            orderIndex = 1,
            isUnlocked = true,
            isCompleted = true,
            bestScore = 95,
            completedCount = 3,
        )
        val unlockedOnly = untouched.copy(
            id = "hsk1_l2",
            orderIndex = 2,
            isUnlocked = true,
        )

        assertNull(untouched.toBackupOrNull())

        val completedBackup = completed.toBackupOrNull()
        assertNotNull(completedBackup)
        assertEquals("hsk1_l1", completedBackup?.lessonId)
        assertEquals(95, completedBackup?.bestScore)
        assertEquals(true, completedBackup?.isCompleted)

        val unlockedBackup = unlockedOnly.toBackupOrNull()
        assertNotNull(unlockedBackup)
        assertEquals(true, unlockedBackup?.isUnlocked)
        assertEquals(false, unlockedBackup?.isCompleted)
    }
}
