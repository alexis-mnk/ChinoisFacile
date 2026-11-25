package com.chinoisfacile.mandarin.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private val calculator = StreakCalculator()

    @Test
    fun `first session starts streak at 1`() {
        val result = calculator.updateStreak(
            currentStreak = 0,
            longestStreak = 0,
            lastStudyEpochDay = null,
            todayEpochDay = 20_000L,
        )
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
        assertEquals(20_000L, result.lastStudyEpochDay)
    }

    @Test
    fun `same day session keeps streak unchanged`() {
        val result = calculator.updateStreak(
            currentStreak = 4,
            longestStreak = 7,
            lastStudyEpochDay = 20_000L,
            todayEpochDay = 20_000L,
        )
        assertEquals(4, result.currentStreak)
        assertEquals(7, result.longestStreak)
    }

    @Test
    fun `consecutive day increments streak and updates longest`() {
        val result = calculator.updateStreak(
            currentStreak = 5,
            longestStreak = 5,
            lastStudyEpochDay = 20_000L,
            todayEpochDay = 20_001L,
        )
        assertEquals(6, result.currentStreak)
        assertEquals(6, result.longestStreak)
        assertEquals(20_001L, result.lastStudyEpochDay)
    }

    @Test
    fun `missed day resets current streak but keeps longest`() {
        val result = calculator.updateStreak(
            currentStreak = 5,
            longestStreak = 9,
            lastStudyEpochDay = 20_000L,
            todayEpochDay = 20_003L,
        )
        assertEquals(1, result.currentStreak)
        assertEquals(9, result.longestStreak)
    }
}
