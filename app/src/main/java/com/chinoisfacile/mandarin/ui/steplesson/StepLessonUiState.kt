package com.chinoisfacile.mandarin.ui.steplesson

import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.GrammarNote
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.progression.StepLessonCardItem

enum class StepLessonDisplayMode {
    CARDS,
    LIST,
}

@Immutable
data class StepLessonUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val lessonId: String = "",
    val hskLevel: Int = 1,
    val orderIndex: Int = 1,
    val stepTitle: String = "",
    val stepTheme: String = "",
    val words: List<VocabWord> = emptyList(),
    val grammarNotes: List<GrammarNote> = emptyList(),
    val deckItems: List<StepLessonCardItem> = emptyList(),
    val displayMode: StepLessonDisplayMode = StepLessonDisplayMode.CARDS,
    val currentCardIndex: Int = 0,
    val isLessonCompleted: Boolean = false,
    val isQuizUnlocked: Boolean = false,
    val justCompletedBannerVisible: Boolean = false,
    val toneColorsEnabled: Boolean = true,
    val exampleTokenLookup: Map<String, VocabWord> = emptyMap(),
    val ttsStatus: TtsStatus = TtsStatus.INITIALIZING,
) {
    val safeCardIndex: Int
        get() = if (deckItems.isEmpty()) 0 else currentCardIndex.coerceIn(0, deckItems.lastIndex)

    val currentItem: StepLessonCardItem?
        get() = deckItems.getOrNull(safeCardIndex)

    val canGoPrevious: Boolean
        get() = safeCardIndex > 0

    val canGoNext: Boolean
        get() = deckItems.isNotEmpty() && safeCardIndex < deckItems.lastIndex

    val isOnLastCard: Boolean
        get() = deckItems.isNotEmpty() && safeCardIndex == deckItems.lastIndex
}
