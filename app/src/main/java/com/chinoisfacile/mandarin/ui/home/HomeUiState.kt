package com.chinoisfacile.mandarin.ui.home

import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.UserStats

data class HomeUiState(
    val isLoading: Boolean = true,
    val isSeeding: Boolean = false,
    val selectedHskLevel: Int = 1,
    val units: List<LearningUnit> = emptyList(),
    val stats: UserStats = UserStats(),
    val masteredCount: Int = 0,
    val totalWordsCount: Int = 0,
    val dueWordsCount: Int = 0,
)
