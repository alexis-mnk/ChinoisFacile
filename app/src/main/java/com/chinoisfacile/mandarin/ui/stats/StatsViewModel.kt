package com.chinoisfacile.mandarin.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(
    private val repository: MandarinRepository,
) : ViewModel() {

    private val overviewFocusOverride = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeGlobalStatsOverview(),
        overviewFocusOverride,
    ) { overview, overrideLevel ->
        val resolvedFocusLevel = overrideLevel
            ?.let { target -> overview.levels.firstOrNull { it.hskLevel == target } }
            ?: overview.focusLevel

        StatsUiState(
            isLoading = false,
            stats = overview.stats,
            levels = overview.levels,
            totalWordsCount = overview.totalWordsCount,
            totalMasteredCount = overview.totalMasteredCount,
            totalLearningCount = overview.totalLearningCount,
            totalDueCount = overview.totalDueCount,
            selectedHskLevel = overview.selectedHskLevel,
            lastSevenDays = overview.lastSevenDays,
            averageAccuracyPercent = overview.averageAccuracyPercent,
            focusLevel = resolvedFocusLevel,
            quickReviewTargetLevel = overview.quickReviewTargetLevel,
            levelsWithDueCount = overview.levelsWithDueCount,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    fun onSelectOverviewFocusLevel(level: Int) {
        overviewFocusOverride.value = level.coerceIn(1, 6)
    }

    fun onSelectHskLevel(level: Int) {
        viewModelScope.launch {
            repository.setSelectedHskLevel(level)
        }
    }
}
