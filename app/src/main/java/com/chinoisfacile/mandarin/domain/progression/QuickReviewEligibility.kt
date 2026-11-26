package com.chinoisfacile.mandarin.domain.progression

import com.chinoisfacile.mandarin.data.model.VocabWord

object QuickReviewEligibility {

    const val MIN_SEEN_WORDS = 10

    fun isAvailable(seenWordsCount: Int): Boolean = seenWordsCount >= MIN_SEEN_WORDS

    /**
     * Collects word IDs belonging strictly to one HSK level (`wordIdToLessonId`) that the user has
     * already seen via a validated step Lesson, a completed Quiz, or an existing Room progress row.
     */
    fun collectSeenWordIds(
        wordIdToLessonId: Map<String, String>,
        completedStepLessonIds: Set<String>,
        completedQuizLessonIds: Set<String>,
        progressedWordIds: Set<String>,
    ): Set<String> {
        if (wordIdToLessonId.isEmpty()) return emptySet()
        val seenIds = HashSet<String>()
        for ((wordId, lessonId) in wordIdToLessonId) {
            if (lessonId in completedStepLessonIds ||
                lessonId in completedQuizLessonIds ||
                wordId in progressedWordIds
            ) {
                seenIds += wordId
            }
        }
        return seenIds
    }

    /**
     * Builds the target words for Quick Review strictly from already-seen words of the level,
     * prioritizing SM-2 due words, then weak words, then remaining seen words.
     */
    fun selectTargetWords(
        seenWords: List<VocabWord>,
        dueWordIds: List<String>,
        weakOrderedWordIds: List<String>,
        maxCount: Int,
    ): List<VocabWord> {
        if (seenWords.isEmpty() || maxCount <= 0) return emptyList()
        val seenWordById = seenWords.associateBy { it.id }
        val dueSeen = dueWordIds.mapNotNull { seenWordById[it] }
        val weakSeen = weakOrderedWordIds.mapNotNull { seenWordById[it] }
        return (dueSeen + weakSeen + seenWords)
            .distinctBy { it.id }
            .take(maxCount)
    }
}
