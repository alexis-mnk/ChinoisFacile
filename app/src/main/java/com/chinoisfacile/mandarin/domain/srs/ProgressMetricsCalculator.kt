package com.chinoisfacile.mandarin.domain.srs

import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.HskLevelMastery
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

@Immutable
data class StudyDayStatus(
    val epochDay: Long,
    val shortLabel: String,
    val isActive: Boolean,
    val isToday: Boolean,
)

object ProgressMetricsCalculator {

    private const val DAYS_IN_WEEK = 7

    fun dayInitial(epochDay: Long): String = when (LocalDate.ofEpochDay(epochDay).dayOfWeek) {
        DayOfWeek.MONDAY -> "M"
        DayOfWeek.TUESDAY -> "T"
        DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "T"
        DayOfWeek.FRIDAY -> "F"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "S"
    }

    fun buildLastSevenDays(
        todayEpochDay: Long,
        recordedStudyDays: Set<Long>,
        progressReviewedDays: Set<Long>,
        lastStudyEpochDay: Long?,
        currentStreak: Int,
    ): List<StudyDayStatus> {
        val activeDays = HashSet<Long>(recordedStudyDays.size + progressReviewedDays.size + currentStreak.coerceAtLeast(0))
        activeDays.addAll(recordedStudyDays)
        activeDays.addAll(progressReviewedDays)
        if (lastStudyEpochDay != null && currentStreak > 0) {
            val streakStart = lastStudyEpochDay - (currentStreak.toLong() - 1L)
            for (day in streakStart..lastStudyEpochDay) {
                activeDays += day
            }
        }

        val startDay = todayEpochDay - (DAYS_IN_WEEK - 1L)
        return (0 until DAYS_IN_WEEK).map { offset ->
            val day = startDay + offset
            StudyDayStatus(
                epochDay = day,
                shortLabel = dayInitial(day),
                isActive = day in activeDays,
                isToday = day == todayEpochDay,
            )
        }
    }

    fun computeAverageAccuracy(
        accuracySum: Int,
        accuracyCount: Int,
        totalCorrect: Int,
        totalWrong: Int,
    ): Int? {
        if (accuracyCount > 0) {
            return (accuracySum.toDouble() / accuracyCount).roundToInt().coerceIn(0, 100)
        }
        val totalAttempts = totalCorrect + totalWrong
        if (totalAttempts > 0) {
            return ((totalCorrect * 100.0) / totalAttempts).roundToInt().coerceIn(0, 100)
        }
        return null
    }

    fun resolveFocusLevel(
        levels: List<HskLevelMastery>,
        selectedHskLevel: Int,
    ): HskLevelMastery? {
        val selected = levels.firstOrNull { it.hskLevel == selectedHskLevel }
        if (selected != null && (selected.masteredCount + selected.learningCount) > 0) {
            return selected
        }
        val highestStarted = levels
            .filter { (it.masteredCount + it.learningCount) > 0 }
            .maxByOrNull { it.hskLevel }
        return highestStarted ?: selected ?: levels.firstOrNull()
    }

    fun resolveQuickReviewTargetLevel(
        levels: List<HskLevelMastery>,
        selectedHskLevel: Int,
    ): Int? {
        val selected = levels.firstOrNull { it.hskLevel == selectedHskLevel }
        if (selected != null && selected.dueCount > 0) {
            return selected.hskLevel
        }
        return levels.firstOrNull { it.dueCount > 0 }?.hskLevel
    }
}
