package com.chinoisfacile.mandarin.domain.lesson

enum class SessionPhase {
    MAIN,
    MISTAKE_REVIEW,
}

data class SessionCompletionData(
    val completedCount: Int,
    val totalCount: Int,
    val accuracyPercent: Int,
    val outcomes: List<WordSessionOutcome>,
)

sealed interface SessionAdvanceResult {
    data class NextExercise(
        val exercise: Exercise,
        val completedCount: Int,
        val phase: SessionPhase,
        val mistakeReviewCurrent: Int,
        val mistakeReviewTotal: Int,
    ) : SessionAdvanceResult

    data class Finished(
        val completion: SessionCompletionData,
    ) : SessionAdvanceResult
}

/**
 * Pure two-phase session queue and scoring state machine:
 * - [SessionPhase.MAIN]: first-attempt accuracy and SM-2 outcomes are recorded here.
 * - [SessionPhase.MISTAKE_REVIEW]: failed exercises are replayed until all are solved,
 *   without altering first-attempt score or erasing the initial SM-2 penalty.
 */
class LessonSessionEngine {

    private val mainQueue = ArrayDeque<Exercise>()
    private val mistakeReviewQueue = ArrayDeque<Exercise>()
    private val queuedMistakeBaseIds = mutableSetOf<String>()
    private val firstTryWordStats = mutableMapOf<String, MutableWordStats>()
    private var firstTryFailedCount = 0
    private var retryCounter = 0

    var phase: SessionPhase = SessionPhase.MAIN
        private set

    var completedCount: Int = 0
        private set

    var totalCount: Int = 0
        private set

    var mistakeReviewTotal: Int = 0
        private set

    private var mistakeReviewCompleted: Int = 0

    val mistakeReviewCurrent: Int
        get() = if (phase == SessionPhase.MISTAKE_REVIEW && mistakeReviewTotal > 0) {
            (mistakeReviewCompleted + 1).coerceIn(1, mistakeReviewTotal)
        } else {
            0
        }

    val currentExercise: Exercise?
        get() = when (phase) {
            SessionPhase.MAIN -> mainQueue.firstOrNull()
            SessionPhase.MISTAKE_REVIEW -> mistakeReviewQueue.firstOrNull()
        }

    fun start(exercises: List<Exercise>) {
        mainQueue.clear()
        mainQueue.addAll(exercises)
        mistakeReviewQueue.clear()
        queuedMistakeBaseIds.clear()
        firstTryWordStats.clear()
        firstTryFailedCount = 0
        retryCounter = 0
        phase = SessionPhase.MAIN
        completedCount = 0
        totalCount = exercises.size
        mistakeReviewTotal = 0
        mistakeReviewCompleted = 0
    }

    fun recordAnswer(
        exercise: Exercise,
        isCorrect: Boolean,
        answeredWithoutPinyin: Boolean,
    ) {
        if (phase != SessionPhase.MAIN) {
            // Mistakes reviewed in the final pass never alter first-attempt score or SM-2 outcomes.
            return
        }

        val stats = firstTryWordStats.getOrPut(exercise.word.id) { MutableWordStats() }
        if (isCorrect) {
            stats.correctAttempts += 1
            if (answeredWithoutPinyin) {
                stats.answeredWithoutPinyin = true
            }
        } else {
            stats.wrongAttempts += 1
            firstTryFailedCount += 1
            val baseId = baseExerciseId(exercise.id)
            if (queuedMistakeBaseIds.add(baseId)) {
                mistakeReviewQueue.addLast(cloneForReview(exercise))
            }
        }
    }

    fun advanceAfterFeedback(wasCorrect: Boolean): SessionAdvanceResult? {
        return when (phase) {
            SessionPhase.MAIN -> {
                mainQueue.removeFirstOrNull() ?: return null
                completedCount += 1
                nextOrTransitionOrFinish()
            }

            SessionPhase.MISTAKE_REVIEW -> {
                val current = mistakeReviewQueue.removeFirstOrNull() ?: return null
                if (wasCorrect) {
                    mistakeReviewCompleted += 1
                } else {
                    mistakeReviewQueue.addLast(cloneForReview(current))
                }
                nextOrTransitionOrFinish()
            }
        }
    }

    /**
     * Skips the current [Exercise.AudioToEnglish] without penalizing session accuracy or recording
     * either a failure or false SM-2 mastery on the word.
     */
    fun skipListeningExercise(): SessionAdvanceResult? {
        val current = currentExercise
        if (current !is Exercise.AudioToEnglish) return null
        when (phase) {
            SessionPhase.MAIN -> {
                mainQueue.removeFirst()
                completedCount += 1
            }

            SessionPhase.MISTAKE_REVIEW -> {
                mistakeReviewQueue.removeFirst()
                mistakeReviewCompleted += 1
            }
        }
        return nextOrTransitionOrFinish()
    }

    private fun nextOrTransitionOrFinish(): SessionAdvanceResult {
        if (phase == SessionPhase.MAIN) {
            val nextMain = mainQueue.firstOrNull()
            if (nextMain != null) {
                return SessionAdvanceResult.NextExercise(
                    exercise = nextMain,
                    completedCount = completedCount,
                    phase = SessionPhase.MAIN,
                    mistakeReviewCurrent = 0,
                    mistakeReviewTotal = 0,
                )
            }

            if (mistakeReviewQueue.isNotEmpty()) {
                phase = SessionPhase.MISTAKE_REVIEW
                mistakeReviewTotal = mistakeReviewQueue.size
                mistakeReviewCompleted = 0
                return SessionAdvanceResult.NextExercise(
                    exercise = mistakeReviewQueue.first(),
                    completedCount = completedCount,
                    phase = SessionPhase.MISTAKE_REVIEW,
                    mistakeReviewCurrent = mistakeReviewCurrent,
                    mistakeReviewTotal = mistakeReviewTotal,
                )
            }

            return SessionAdvanceResult.Finished(buildCompletionData())
        }

        val nextReview = mistakeReviewQueue.firstOrNull()
        return if (nextReview != null) {
            SessionAdvanceResult.NextExercise(
                exercise = nextReview,
                completedCount = completedCount,
                phase = SessionPhase.MISTAKE_REVIEW,
                mistakeReviewCurrent = mistakeReviewCurrent,
                mistakeReviewTotal = mistakeReviewTotal,
            )
        } else {
            SessionAdvanceResult.Finished(buildCompletionData())
        }
    }

    private fun buildCompletionData(): SessionCompletionData {
        val firstTryCorrect = (totalCount - firstTryFailedCount).coerceAtLeast(0)
        val accuracyPercent = if (totalCount > 0) {
            (firstTryCorrect * 100) / totalCount
        } else {
            100
        }
        val outcomes = firstTryWordStats.map { (wordId, stats) ->
            WordSessionOutcome(
                wordId = wordId,
                correctAttempts = stats.correctAttempts,
                wrongAttempts = stats.wrongAttempts,
                answeredWithoutPinyin = stats.answeredWithoutPinyin,
            )
        }
        return SessionCompletionData(
            completedCount = completedCount,
            totalCount = totalCount,
            accuracyPercent = accuracyPercent,
            outcomes = outcomes,
        )
    }

    private fun baseExerciseId(id: String): String = id.substringBefore(REVIEW_ID_MARKER)

    private fun cloneForReview(exercise: Exercise): Exercise {
        retryCounter += 1
        val reviewId = "${baseExerciseId(exercise.id)}$REVIEW_ID_MARKER$retryCounter"
        return when (exercise) {
            is Exercise.HanziToEnglish -> exercise.copy(id = reviewId)
            is Exercise.AudioToEnglish -> exercise.copy(id = reviewId)
            is Exercise.AudioToHanzi -> exercise.copy(id = reviewId)
            is Exercise.EnglishToHanzi -> exercise.copy(id = reviewId)
            is Exercise.Cloze -> exercise.copy(id = reviewId)
            is Exercise.SentenceOrder -> exercise.copy(
                id = reviewId,
                shuffledChips = exercise.shuffledChips.shuffled(),
            )
        }
    }

    private data class MutableWordStats(
        var correctAttempts: Int = 0,
        var wrongAttempts: Int = 0,
        var answeredWithoutPinyin: Boolean = false,
    )

    private companion object {
        const val REVIEW_ID_MARKER = "_rev_"
    }
}
