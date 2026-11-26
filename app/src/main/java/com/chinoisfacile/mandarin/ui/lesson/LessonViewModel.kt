package com.chinoisfacile.mandarin.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinoisfacile.mandarin.data.repository.MandarinRepository
import com.chinoisfacile.mandarin.domain.audio.AudioPlayer
import com.chinoisfacile.mandarin.domain.audio.SoundEffects
import com.chinoisfacile.mandarin.domain.lesson.Exercise
import com.chinoisfacile.mandarin.domain.lesson.ExerciseGenerator
import com.chinoisfacile.mandarin.domain.lesson.TokenChip
import com.chinoisfacile.mandarin.domain.lesson.WordSessionOutcome
import com.chinoisfacile.mandarin.domain.srs.StreakCalculator
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

    private val exerciseQueue = ArrayDeque<Exercise>()
    private val failedInitialIds = mutableSetOf<String>()
    private val wordStats = mutableMapOf<String, MutableWordStats>()
    private var retryCounter = 0

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

            exerciseQueue.clear()
            exerciseQueue.addAll(exercises)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isError = false,
                    sessionTitle = payload.lessonTitle,
                    currentExercise = exerciseQueue.firstOrNull(),
                    completedCount = 0,
                    totalCount = exercises.size,
                    showPinyin = defaultShowPinyin,
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

    fun onShowPinyinFallback() {
        _uiState.update { it.copy(showPinyin = true) }
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
            is Exercise.SentenceOrder ->
                state.selectedTokens.map { it.text } == exercise.expectedTokens
        }

        val stats = wordStats.getOrPut(exercise.word.id) { MutableWordStats() }
        if (isCorrect) {
            stats.correctAttempts += 1
            if ((exercise is Exercise.HanziToEnglish || exercise is Exercise.EnglishToHanzi) &&
                !state.showPinyin
            ) {
                stats.answeredWithoutPinyin = true
            }
            if (soundEffectsEnabled) {
                soundEffects.playCorrect()
            }
        } else {
            stats.wrongAttempts += 1
            val baseId = exercise.id.substringBefore("_retry_")
            failedInitialIds += baseId
            if (soundEffectsEnabled) {
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
        val state = _uiState.value
        val feedback = state.feedback ?: return
        val current = exerciseQueue.removeFirstOrNull() ?: return

        audioPlayer.stop()

        val newCompletedCount = if (feedback.isCorrect) {
            state.completedCount + 1
        } else {
            exerciseQueue.addLast(cloneForRetry(current))
            state.completedCount
        }

        val nextExercise = exerciseQueue.firstOrNull()
        if (nextExercise != null) {
            _uiState.update {
                it.copy(
                    currentExercise = nextExercise,
                    completedCount = newCompletedCount,
                    selectedOption = null,
                    selectedTokens = emptyList(),
                    feedback = null,
                )
            }
        } else {
            finishSession(completedCount = newCompletedCount, totalCount = state.totalCount)
        }
    }

    private fun cloneForRetry(exercise: Exercise): Exercise {
        retryCounter += 1
        val retryId = "${exercise.id.substringBefore("_retry_")}_retry_$retryCounter"
        return when (exercise) {
            is Exercise.HanziToEnglish -> exercise.copy(id = retryId)
            is Exercise.AudioToEnglish -> exercise.copy(id = retryId)
            is Exercise.AudioToHanzi -> exercise.copy(id = retryId)
            is Exercise.EnglishToHanzi -> exercise.copy(id = retryId)
            is Exercise.SentenceOrder -> exercise.copy(
                id = retryId,
                shuffledChips = exercise.shuffledChips.shuffled(),
            )
        }
    }

    private fun finishSession(completedCount: Int, totalCount: Int) {
        viewModelScope.launch {
            val firstTryCorrect = (totalCount - failedInitialIds.size).coerceAtLeast(0)
            val accuracyPercent = if (totalCount > 0) {
                (firstTryCorrect * 100) / totalCount
            } else {
                100
            }
            val earnedXp = completedCount * StreakCalculator.XP_PER_EXERCISE +
                StreakCalculator.XP_SESSION_BONUS

            val outcomes = wordStats.map { (wordId, stats) ->
                WordSessionOutcome(
                    wordId = wordId,
                    correctAttempts = stats.correctAttempts,
                    wrongAttempts = stats.wrongAttempts,
                    answeredWithoutPinyin = stats.answeredWithoutPinyin,
                )
            }

            val summary = repository.recordSessionCompletion(
                lessonId = lessonId,
                outcomes = outcomes,
                earnedXp = earnedXp,
                accuracyPercent = accuracyPercent,
            )

            if (soundEffectsEnabled) {
                soundEffects.playSessionComplete()
            }

            _uiState.update {
                it.copy(
                    currentExercise = null,
                    completedCount = completedCount,
                    selectedOption = null,
                    selectedTokens = emptyList(),
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

    private data class MutableWordStats(
        var correctAttempts: Int = 0,
        var wrongAttempts: Int = 0,
        var answeredWithoutPinyin: Boolean = false,
    )
}
