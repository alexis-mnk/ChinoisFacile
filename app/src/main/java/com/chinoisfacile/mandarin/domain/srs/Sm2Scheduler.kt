package com.chinoisfacile.mandarin.domain.srs

import kotlin.math.roundToInt

/**
 * État SM-2 d'un mot mémorisé.
 */
data class Sm2State(
    val repetitions: Int = 0,
    val easeFactor: Double = DEFAULT_EASE_FACTOR,
    val intervalDays: Int = 0,
    val nextReviewEpochDay: Long = 0L,
) {
    companion object {
        const val DEFAULT_EASE_FACTOR = 2.5
        const val MIN_EASE_FACTOR = 1.3
    }
}

/**
 * Implémentation pure et déterministe de l'algorithme de répétition espacée SM-2.
 */
class Sm2Scheduler {

    fun calculateNextReview(
        current: Sm2State,
        quality: Int,
        todayEpochDay: Long,
    ): Sm2State {
        val q = quality.coerceIn(0, 5)

        val newRepetitions: Int
        val newInterval: Int
        if (q < 3) {
            newRepetitions = 0
            newInterval = 1
        } else {
            newInterval = when (current.repetitions) {
                0 -> 1
                1 -> 6
                else -> (current.intervalDays * current.easeFactor)
                    .roundToInt()
                    .coerceAtLeast(1)
            }
            newRepetitions = current.repetitions + 1
        }

        val qualityDiff = 5 - q
        val delta = 0.1 - qualityDiff * (0.08 + qualityDiff * 0.02)
        val newEaseFactor = (current.easeFactor + delta)
            .coerceAtLeast(Sm2State.MIN_EASE_FACTOR)

        return Sm2State(
            repetitions = newRepetitions,
            easeFactor = newEaseFactor,
            intervalDays = newInterval,
            nextReviewEpochDay = todayEpochDay + newInterval,
        )
    }

    companion object {
        fun isMastered(repetitions: Int, intervalDays: Int): Boolean =
            repetitions >= 3 && intervalDays >= 6

        fun qualityForOutcome(
            hadMistake: Boolean,
            answeredWithoutPinyin: Boolean,
        ): Int = when {
            hadMistake -> 1
            answeredWithoutPinyin -> 5
            else -> 4
        }
    }
}
