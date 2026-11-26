package com.chinoisfacile.mandarin.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.AudioPlayer
import com.chinoisfacile.mandarin.domain.audio.SoundEffects
import com.chinoisfacile.mandarin.domain.lesson.Exercise
import com.chinoisfacile.mandarin.domain.lesson.ExerciseGenerator
import com.chinoisfacile.mandarin.domain.lesson.LessonSessionEngine
import com.chinoisfacile.mandarin.domain.lesson.SessionAdvanceResult
import com.chinoisfacile.mandarin.domain.lesson.SessionCompletionData
import com.chinoisfacile.mandarin.domain.lesson.SessionPhase
import com.chinoisfacile.mandarin.domain.lesson.TokenChip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LessonViewModel(
    private val lessonId: String?,
    private val hskLevel: Int,
    private val repository: MandarinRepository,
    private val exerciseGenerator: ExerciseGenerator,
    private val audioPlayer: AudioPlayer,
    private val soundEffects: SoundEffects,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val sessionEngine = LessonSessionEngine()

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

        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val defaultShowPinyin = repository.observeShowPinyinByDefault().first()
            soundEffectsEnabled = repository.observeSoundEffectsEnabled().first()
            val payload = if (lessonId != null) {
                repository.getLessonPayload(lessonId)
            } else {
                repository.getQuickReviewPayload(hskLevel = hskLevel)
            }

            if (payload.targetWords.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isError = true,
                        showPinyin = defaultShowPinyin,
                    )
                }
                return@launch
            }

            val exercises = exerciseGenerator.generateSession(
                targetWords = payload.targetWords,
                distractorPool = payload.distractorPool,
                knownTokens = payload.knownTokens,
            )
            sessionEngine.start(exercises)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isError = false,
                    sessionTitle = payload.lessonTitle,
                    currentExercise = sessionEngine.currentExercise,
                    completedCount = sessionEngine.completedCount,
                    totalCount = sessionEngine.totalCount,
                    showPinyin = defaultShowPinyin,
                    showAudioPinyinHint = false,
                    sessionPhase = SessionPhase.MAIN,
                    mistakeReviewCurrent = 0,
                    mistakeReviewTotal = 0,
                )
            }
        }
    }

    fun onSelectOption(option: String) {
        if (_uiState.value.feedback != null) return
        _uiState.update { it.copy(selectedOption = option) }
    }

    fun onToggleSentenceToken(chip: TokenChip) {
        if (_uiState.value.feedback != null) return
        _uiState.update { state ->
            val updated = if (state.selectedTokens.any { it.id == chip.id }) {
                state.selectedTokens.filterNot { it.id == chip.id }
            } else {
                state.selectedTokens + chip
            }
            state.copy(selectedTokens = updated)
        }
    }

    fun onClearSentenceTokens() {
        if (_uiState.value.feedback != null) return
        _uiState.update { it.copy(selectedTokens = emptyList()) }
    }

    fun onTogglePinyin() {
        val nextValue = !_uiState.value.showPinyin
        _uiState.update { it.copy(showPinyin = nextValue) }
        viewModelScope.launch {
            repository.setShowPinyinByDefault(nextValue)
        }
    }

    fun onToggleAudioPinyinHint() {
        _uiState.update { it.copy(showAudioPinyinHint = !it.showAudioPinyinHint) }
    }

    fun onShowPinyinFallback() {
        _uiState.update { it.copy(showPinyin = true, showAudioPinyinHint = true) }
    }

    fun onPlayNormal(text: String) {
        audioPlayer.speakNormal(text)
    }

    fun onPlaySlow(text: String) {
        audioPlayer.speakSlow(text)
    }

    fun onCheckAnswer() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (state.feedback != null) return

        val isCorrect = when (exercise) {
            is Exercise.HanziToEnglish -> state.selectedOption == exercise.correctOption
            is Exercise.AudioToEnglish -> state.selectedOption == exercise.correctOption
            is Exercise.AudioToHanzi -> state.selectedOption == exercise.correctOption
            is Exercise.EnglishToHanzi -> state.selectedOption == exercise.correctOption
            is Exercise.Cloze -> state.selectedOption == exercise.correctOption
            is Exercise.SentenceOrder ->
                state.selectedTokens.map { it.text } == exercise.expectedTokens
        }

        val answeredWithoutPinyin = isCorrect &&
            (
                exercise is Exercise.HanziToEnglish ||
                    exercise is Exercise.EnglishToHanzi ||
                    exercise is Exercise.Cloze
                ) &&
            !state.showPinyin
        sessionEngine.recordAnswer(
            exercise = exercise,
            isCorrect = isCorrect,
            answeredWithoutPinyin = answeredWithoutPinyin,
        )

        if (soundEffectsEnabled) {
            if (isCorrect) {
                soundEffects.playCorrect()
            } else {
                soundEffects.playWrong()
            }
        }

        _uiState.update {
            it.copy(
                feedback = FeedbackState(
                    isCorrect = isCorrect,
                    word = exercise.word,
                ),
            )
        }
    }

    fun onContinue() {
        val feedback = _uiState.value.feedback ?: return
        audioPlayer.stop()
        val result = sessionEngine.advanceAfterFeedback(wasCorrect = feedback.isCorrect) ?: return
        applyAdvanceResult(result)
    }

    fun skipListeningExercise() {
        val state = _uiState.value
        if (state.feedback != null || state.currentExercise !is Exercise.AudioToEnglish) return
        audioPlayer.stop()
        val result = sessionEngine.skipListeningExercise() ?: return
        applyAdvanceResult(result)
    }

    private fun applyAdvanceResult(result: SessionAdvanceResult) {
        when (result) {
            is SessionAdvanceResult.NextExercise -> {
                _uiState.update {
                    it.copy(
                        currentExercise = result.exercise,
                        completedCount = result.completedCount,
                        selectedOption = null,
                        selectedTokens = emptyList(),
                        showAudioPinyinHint = false,
                        sessionPhase = result.phase,
                        mistakeReviewCurrent = result.mistakeReviewCurrent,
                        mistakeReviewTotal = result.mistakeReviewTotal,
                        feedback = null,
                    )
                }
            }

            is SessionAdvanceResult.Finished -> {
                finishSession(result.completion)
            }
        }
    }

    private fun finishSession(completion: SessionCompletionData) {
        viewModelScope.launch {
            val summary = repository.recordSessionCompletion(
                lessonId = lessonId,
                outcomes = completion.outcomes,
                earnedXp = completion.earnedXp,
                accuracyPercent = completion.accuracyPercent,
            )

            if (soundEffectsEnabled) {
                soundEffects.playSessionComplete()
            }

            _uiState.update {
                it.copy(
                    currentExercise = null,
                    completedCount = completion.completedCount,
                    selectedOption = null,
                    selectedTokens = emptyList(),
                    showAudioPinyinHint = false,
                    sessionPhase = SessionPhase.MAIN,
                    mistakeReviewCurrent = 0,
                    mistakeReviewTotal = 0,
                    feedback = null,
                    summary = summary,
                )
            }
        }
    }

    fun onRequestQuit() {
        _uiState.update { it.copy(showQuitDialog = true) }
    }

    fun onDismissQuit() {
        _uiState.update { it.copy(showQuitDialog = false) }
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }
}
