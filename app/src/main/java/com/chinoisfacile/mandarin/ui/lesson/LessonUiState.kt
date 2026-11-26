package com.chinoisfacile.mandarin.ui.lesson

import com.chinoisfacile.mandarin.data.model.SessionCompletionSummary
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.lesson.Exercise
import com.chinoisfacile.mandarin.domain.lesson.TokenChip

data class FeedbackState(
    val isCorrect: Boolean,
    val word: VocabWord,
)

data class LessonUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val sessionTitle: String? = null,
    val currentExercise: Exercise? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val selectedOption: String? = null,
    val selectedTokens: List<TokenChip> = emptyList(),
    val showPinyin: Boolean = true,
    val feedback: FeedbackState? = null,
    val ttsStatus: TtsStatus = TtsStatus.INITIALIZING,
    val summary: SessionCompletionSummary? = null,
    val showQuitDialog: Boolean = false,
) {
    val canCheckAnswer: Boolean
        get() = feedback == null && when (currentExercise) {
            is Exercise.HanziToEnglish,
            is Exercise.AudioToEnglish,
            is Exercise.AudioToHanzi,
            is Exercise.EnglishToHanzi,
            -> selectedOption != null
            is Exercise.SentenceOrder -> selectedTokens.isNotEmpty()
            null -> false
        }
}
