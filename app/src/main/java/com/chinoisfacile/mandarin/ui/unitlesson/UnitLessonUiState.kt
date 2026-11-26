package com.chinoisfacile.mandarin.ui.unitlesson

import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.progression.UnitLessonFilterSelection
import com.chinoisfacile.mandarin.domain.progression.UnitLessonQuizGroup
import com.chinoisfacile.mandarin.domain.progression.UnitLessonWordItem
import com.chinoisfacile.mandarin.domain.progression.UnitProgressionResolver

enum class UnitLessonDisplayMode {
    CARDS,
    SHEET,
}

data class UnitLessonUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val unitId: String = "",
    val hskLevel: Int = 1,
    val unitTitle: String = "",
    val unitDescription: String = "",
    val quizGroups: List<UnitLessonQuizGroup> = emptyList(),
    val selectedFilter: UnitLessonFilterSelection = UnitLessonFilterSelection.All,
    val displayMode: UnitLessonDisplayMode = UnitLessonDisplayMode.CARDS,
    val currentCardIndex: Int = 0,
    val isLessonCompleted: Boolean = false,
    val justCompletedBannerVisible: Boolean = false,
    val ttsStatus: TtsStatus = TtsStatus.INITIALIZING,
) {
    val visibleWords: List<UnitLessonWordItem>
        get() = UnitProgressionResolver.filterWords(quizGroups, selectedFilter)

    val safeCardIndex: Int
        get() = if (visibleWords.isEmpty()) 0 else currentCardIndex.coerceIn(0, visibleWords.lastIndex)

    val currentWordItem: UnitLessonWordItem?
        get() = visibleWords.getOrNull(safeCardIndex)

    val totalUnitWords: Int
        get() = quizGroups.sumOf { it.words.size }

    val canGoPrevious: Boolean
        get() = safeCardIndex > 0

    val canGoNext: Boolean
        get() = visibleWords.isNotEmpty() && safeCardIndex < visibleWords.lastIndex

    val isOnLastCard: Boolean
        get() = visibleWords.isNotEmpty() && safeCardIndex == visibleWords.lastIndex

    val launchQuizTarget: UnitLessonQuizGroup?
        get() = UnitProgressionResolver.resolveLaunchQuizTarget(
            groups = quizGroups,
            selection = selectedFilter,
            isUnitLessonCompleted = isLessonCompleted,
        )
}
