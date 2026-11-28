package com.chinoisfacile.mandarin.domain.srs

import kotlin.math.max

data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastStudyEpochDay: Long,
)

class StreakCalculator {

    fun updateStreak(
        currentStreak: Int,
        longestStreak: Int,
        lastStudyEpochDay: Long?,
        todayEpochDay: Long,
    ): StreakResult {
        val updatedStreak = when {
            lastStudyEpochDay == null -> 1
            todayEpochDay <= lastStudyEpochDay -> max(1, currentStreak)
            todayEpochDay == lastStudyEpochDay + 1L -> currentStreak + 1
            else -> 1
        }
        val updatedLongest = max(longestStreak, updatedStreak)
        return StreakResult(
            currentStreak = updatedStreak,
            longestStreak = updatedLongest,
            lastStudyEpochDay = max(lastStudyEpochDay ?: todayEpochDay, todayEpochDay),
        )
    }
}
