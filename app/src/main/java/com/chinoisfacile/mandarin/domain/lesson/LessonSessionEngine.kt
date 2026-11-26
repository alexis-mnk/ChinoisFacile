package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.domain.srs.StreakCalculator

data class SessionCompletionData(
    val completedCount: Int,
    val totalCount: Int,
    val accuracyPercent: Int,
    val earnedXp: Int,
    val outcomes: List<WordSessionOutcome>,
)

sealed interface SessionAdvanceResult {
    data class NextExercise(
        val exercise: Exercise,
        val completedCount: Int,
    ) : SessionAdvanceResult

    data class Finished(
        val completion: SessionCompletionData,
    ) : SessionAdvanceResult
}

/**
 * Pure session queue and scoring state machine for quizzes and quick reviews.
 */
class LessonSessionEngine {

    private val exerciseQueue = ArrayDeque<Exercise>()
    private val failedInitialIds = mutableSetOf<String>()
    private val wordStats = mutableMapOf<String, MutableWordStats>()
    private var retryCounter = 0

    var completedCount: Int = 0
        private set

    var totalCount: Int = 0
        private set

    val currentExercise: Exercise?
        get() = exerciseQueue.firstOrNull()

    fun start(exercises: List<Exercise>) {
        exerciseQueue.clear()
        exerciseQueue.addAll(exercises)
        failedInitialIds.clear()
        wordStats.clear()
        retryCounter = 0
        completedCount = 0
        totalCount = exercises.size
    }

    fun recordAnswer(
        exercise: Exercise,
        isCorrect: Boolean,
        answeredWithoutPinyin: Boolean,
    ) {
        val stats = wordStats.getOrPut(exercise.word.id) { MutableWordStats() }
        if (isCorrect) {
            stats.correctAttempts += 1
            if (answeredWithoutPinyin) {
                stats.answeredWithoutPinyin = true
            }
        } else {
            stats.wrongAttempts += 1
            failedInitialIds += exercise.id.substringBefore("_retry_")
        }
    }

    fun advanceAfterFeedback(wasCorrect: Boolean): SessionAdvanceResult? {
        val current = exerciseQueue.removeFirstOrNull() ?: return null
        if (wasCorrect) {
            completedCount += 1
        } else {
            exerciseQueue.addLast(cloneForRetry(current))
        }
        return nextOrFinish()
    }

    /**
     * Skips the current [Exercise.AudioToEnglish] without penalizing session accuracy or recording
     * either a failure or false SM-2 mastery on the word.
     */
    fun skipListeningExercise(): SessionAdvanceResult? {
        val current = exerciseQueue.firstOrNull()
        if (current !is Exercise.AudioToEnglish) return null
        exerciseQueue.removeFirst()
        completedCount += 1
        return nextOrFinish()
    }

    private fun nextOrFinish(): SessionAdvanceResult {
        val next = exerciseQueue.firstOrNull()
        return if (next != null) {
            SessionAdvanceResult.NextExercise(
                exercise = next,
                completedCount = completedCount,
            )
        } else {
            SessionAdvanceResult.Finished(buildCompletionData())
        }
    }

    private fun buildCompletionData(): SessionCompletionData {
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
        return SessionCompletionData(
            completedCount = completedCount,
            totalCount = totalCount,
            accuracyPercent = accuracyPercent,
            earnedXp = earnedXp,
            outcomes = outcomes,
        )
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

    private data class MutableWordStats(
        var correctAttempts: Int = 0,
        var wrongAttempts: Int = 0,
        var answeredWithoutPinyin: Boolean = false,
    )
}
