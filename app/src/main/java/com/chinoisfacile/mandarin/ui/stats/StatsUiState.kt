package com.chinoisfacile.mandarin.ui.stats

import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.HskLevelMastery
import com.chinoisfacile.mandarin.data.model.UserStats
import com.chinoisfacile.mandarin.domain.srs.StudyDayStatus

@Immutable
data class StatsUiState(
    val isLoading: Boolean = true,
    val stats: UserStats = UserStats(),
    val levels: List<HskLevelMastery> = emptyList(),
    val totalWordsCount: Int = 0,
    val totalMasteredCount: Int = 0,
    val totalLearningCount: Int = 0,
    val totalDueCount: Int = 0,
    val selectedHskLevel: Int = 1,
    val lastSevenDays: List<StudyDayStatus> = emptyList(),
    val averageAccuracyPercent: Int? = null,
    val focusLevel: HskLevelMastery? = null,
    val quickReviewTargetLevel: Int? = null,
    val levelsWithDueCount: Int = 0,
)
