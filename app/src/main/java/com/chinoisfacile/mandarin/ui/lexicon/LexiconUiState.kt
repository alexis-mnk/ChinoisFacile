package com.chinoisfacile.mandarin.ui.lexicon

import com.chinoisfacile.mandarin.data.model.LexiconViewMode
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusCounts
import com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusFilter

data class LexiconUiState(
    val isLoading: Boolean = true,
    val selectedHskLevel: Int = 1,
    val viewMode: LexiconViewMode = LexiconViewMode.GRID,
    val statusFilter: LexiconStatusFilter = LexiconStatusFilter.ALL,
    val counts: LexiconStatusCounts = LexiconStatusCounts(),
    val filteredWords: List<WordWithProgress> = emptyList(),
    val selectedWord: WordWithProgress? = null,
    val ttsStatus: TtsStatus = TtsStatus.INITIALIZING,
)
