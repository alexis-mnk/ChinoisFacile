package com.chinoisfacile.mandarin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: MandarinRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    private val homeDataFlow = repository.observeSelectedHskLevel()
        .flatMapLatest { level ->
            repository.observeHomeData(hskLevel = level)
        }

    val uiState: StateFlow<HomeUiState> = combine(
        homeDataFlow,
        repository.observeIsSeeding(),
    ) { data, isSeeding ->
        HomeUiState(
            isLoading = false,
            isSeeding = isSeeding,
            selectedHskLevel = data.hskLevel,
            units = data.units,
            stats = data.stats,
            masteredCount = data.masteredCount,
            totalWordsCount = data.totalWordsCount,
            dueWordsCount = data.dueWordsCount,
            seenWordsCount = data.seenWordsCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun onSelectHskLevel(level: Int) {
        viewModelScope.launch {
            repository.setSelectedHskLevel(level)
        }
    }
}
