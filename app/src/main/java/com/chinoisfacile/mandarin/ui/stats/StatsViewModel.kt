package com.chinoisfacile.mandarin.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(
    private val repository: MandarinRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    val uiState: StateFlow<StatsUiState> = repository.observeGlobalStatsOverview()
        .map { overview ->
            StatsUiState(
                isLoading = false,
                stats = overview.stats,
                levels = overview.levels,
                totalWordsCount = overview.totalWordsCount,
                totalMasteredCount = overview.totalMasteredCount,
                totalLearningCount = overview.totalLearningCount,
                totalDueCount = overview.totalDueCount,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    fun onSelectHskLevel(level: Int) {
        viewModelScope.launch {
            repository.setSelectedHskLevel(level)
        }
    }
}
