package com.chinoisfacile.mandarin.ui.stats

import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.HskLevelMastery
import com.chinoisfacile.mandarin.data.model.UserStats

@Immutable
data class StatsUiState(
    val isLoading: Boolean = true,
    val stats: UserStats = UserStats(),
    val levels: List<HskLevelMastery> = emptyList(),
    val totalWordsCount: Int = 0,
    val totalMasteredCount: Int = 0,
    val totalLearningCount: Int = 0,
    val totalDueCount: Int = 0,
)
