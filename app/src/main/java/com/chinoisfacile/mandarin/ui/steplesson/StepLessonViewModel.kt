package com.chinoisfacile.mandarin.ui.steplesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.AudioPlayer
import com.chinoisfacile.mandarin.domain.audio.SoundEffects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StepLessonViewModel(
    private val lessonId: String,
    private val repository: MandarinRepository,
    private val audioPlayer: AudioPlayer,
    private val soundEffects: SoundEffects,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StepLessonUiState(lessonId = lessonId))
    val uiState: StateFlow<StepLessonUiState> = _uiState.asStateFlow()

    @Volatile
    private var soundEffectsEnabled: Boolean = true

    init {
        audioPlayer.status
            .onEach { status ->
                _uiState.update { it.copy(ttsStatus = status) }
            }
            .launchIn(viewModelScope)

        repository.observeSoundEffectsEnabled()
            .onEach { enabled ->
                soundEffectsEnabled = enabled
            }
            .launchIn(viewModelScope)

        repository.observeToneColorsEnabled()
            .onEach { enabled ->
                _uiState.update { it.copy(toneColorsEnabled = enabled) }
            }
            .launchIn(viewModelScope)

        loadStepLesson()
    }

    private fun loadStepLesson() {
        viewModelScope.launch {
            soundEffectsEnabled = repository.observeSoundEffectsEnabled().first()
            val payload = repository.getStepLessonPayload(lessonId)
            if (payload == null || payload.words.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isError = true,
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isError = false,
                    lessonId = payload.lessonId,
                    hskLevel = payload.hskLevel,
                    orderIndex = payload.orderIndex,
                    stepTitle = payload.stepTitle,
                    stepTheme = payload.stepTheme,
                    words = payload.words,
                    grammarNotes = payload.grammarNotes,
                    deckItems = payload.deckItems,
                    exampleTokenLookup = payload.exampleTokenLookup,
                    currentCardIndex = 0,
                    isLessonCompleted = payload.isLessonCompleted,
                    isQuizUnlocked = payload.isQuizUnlocked,
                )
            }
        }
    }

    fun onSelectDisplayMode(mode: StepLessonDisplayMode) {
        audioPlayer.stop()
        _uiState.update { it.copy(displayMode = mode) }
    }

    fun onPreviousCard() {
        audioPlayer.stop()
        _uiState.update { state ->
            state.copy(currentCardIndex = (state.safeCardIndex - 1).coerceAtLeast(0))
        }
    }

    fun onNextCard() {
        audioPlayer.stop()
        _uiState.update { state ->
            val maxIndex = state.deckItems.lastIndex.coerceAtLeast(0)
            state.copy(currentCardIndex = (state.safeCardIndex + 1).coerceAtMost(maxIndex))
        }
    }

    fun onPlayNormal(text: String) {
        audioPlayer.speakNormal(text)
    }

    fun onPlaySlow(text: String) {
        audioPlayer.speakSlow(text)
    }

    fun onCompleteLesson() {
        val state = _uiState.value
        if (state.isLoading || state.isError) return
        viewModelScope.launch {
            repository.completeStepLesson(lessonId)
            if (soundEffectsEnabled) {
                soundEffects.playSessionComplete()
            }
            _uiState.update { current ->
                current.copy(
                    isLessonCompleted = true,
                    isQuizUnlocked = true,
                    justCompletedBannerVisible = true,
                )
            }
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }
}
