package com.chinoisfacile.mandarin.domain.srs

import com.chinoisfacile.mandarin.data.model.HskLevelMastery
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMetricsCalculatorTest {

    @Test
    fun `buildLastSevenDays returns 7 chronological days merging DataStore, Room, and streak`() {
        // 2026-11-28 is Saturday -> 7-day window: Sun Nov 22 .. Sat Nov 28
        val today = LocalDate.of(2026, 11, 28).toEpochDay()
        val result = ProgressMetricsCalculator.buildLastSevenDays(
            todayEpochDay = today,
            recordedStudyDays = setOf(today - 6L, today - 10L),
            progressReviewedDays = setOf(today - 3L),
            lastStudyEpochDay = today,
            currentStreak = 2, // marks today and today - 1
        )

        assertEquals(7, result.size)
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), result.map { it.shortLabel })
        assertEquals(listOf(true, false, false, true, false, true, true), result.map { it.isActive })
        assertTrue(result.last().isToday)
        assertFalse(result.dropLast(1).any { it.isToday })
    }

    @Test
    fun `buildLastSevenDays returns all inactive when user has no history`() {
        val today = LocalDate.of(2026, 11, 28).toEpochDay()
        val result = ProgressMetricsCalculator.buildLastSevenDays(
            todayEpochDay = today,
            recordedStudyDays = emptySet(),
            progressReviewedDays = emptySet(),
            lastStudyEpochDay = null,
            currentStreak = 0,
        )

        assertEquals(7, result.size)
        assertTrue(result.none { it.isActive })
        assertTrue(result.last().isToday)
    }

    @Test
    fun `computeAverageAccuracy uses session average first, falls back to word attempts, or returns null`() {
        assertNull(
            ProgressMetricsCalculator.computeAverageAccuracy(
                accuracySum = 0,
                accuracyCount = 0,
                totalCorrect = 0,
                totalWrong = 0,
            ),
        )

        assertEquals(
            80,
            ProgressMetricsCalculator.computeAverageAccuracy(
                accuracySum = 0,
                accuracyCount = 0,
                totalCorrect = 8,
                totalWrong = 2,
            ),
        )

        assertEquals(
            93,
            ProgressMetricsCalculator.computeAverageAccuracy(
                accuracySum = 185,
                accuracyCount = 2,
                totalCorrect = 8,
                totalWrong = 2,
            ),
        )
    }

    @Test
    fun `resolveFocusLevel and resolveQuickReviewTargetLevel prefer selected level then fall back`() {
        val levels = listOf(
            HskLevelMastery(hskLevel = 1, totalCount = 150, masteredCount = 10, learningCount = 5, dueCount = 0, newCount = 135),
            HskLevelMastery(hskLevel = 2, totalCount = 151, masteredCount = 0, learningCount = 0, dueCount = 0, newCount = 151),
            HskLevelMastery(hskLevel = 3, totalCount = 297, masteredCount = 2, learningCount = 3, dueCount = 4, newCount = 292),
        )

        // Selected HSK 2 is unstarted -> falls back to highest started level (HSK 3)
        assertEquals(3, ProgressMetricsCalculator.resolveFocusLevel(levels, selectedHskLevel = 2)?.hskLevel)
        // Selected HSK 1 is started -> stays on HSK 1
        assertEquals(1, ProgressMetricsCalculator.resolveFocusLevel(levels, selectedHskLevel = 1)?.hskLevel)
        // Selected HSK 1 has 0 due -> picks HSK 3 with due words
        assertEquals(3, ProgressMetricsCalculator.resolveQuickReviewTargetLevel(levels, selectedHskLevel = 1))

        val noDueLevels = levels.map { it.copy(dueCount = 0) }
        assertNull(ProgressMetricsCalculator.resolveQuickReviewTargetLevel(noDueLevels, selectedHskLevel = 1))
    }
}
