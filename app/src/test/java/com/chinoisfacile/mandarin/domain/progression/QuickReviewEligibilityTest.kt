package com.chinoisfacile.mandarin.domain.progression

import com.chinoisfacile.mandarin.data.model.VocabWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReviewEligibilityTest {

    private fun word(id: String, lessonId: String, hskLevel: Int = 1) = VocabWord(
        id = id,
        lessonId = lessonId,
        hskLevel = hskLevel,
        hanzi = "字$id",
        pinyin = "zì",
        english = "word $id",
        exampleHanzi = "字$id。",
        examplePinyin = "zì.",
        exampleEnglish = "word $id.",
        exampleTokens = listOf("字$id"),
    )

    @Test
    fun quickReviewStaysHiddenBelowTenSeenWordsAndShowsAtTenOrMore() {
        assertFalse(QuickReviewEligibility.isAvailable(0))
        assertFalse(QuickReviewEligibility.isAvailable(5))
        assertFalse(QuickReviewEligibility.isAvailable(9))
        assertTrue(QuickReviewEligibility.isAvailable(10))
        assertTrue(QuickReviewEligibility.isAvailable(25))
    }

    @Test
    fun collectSeenWordIdsCombinesLessonsQuizzesAndProgressPerHskLevelIndependently() {
        val hsk1WordToLesson = buildMap {
            for (i in 1..5) put("hsk1_w$i", "hsk1_l1")
            for (i in 6..10) put("hsk1_w$i", "hsk1_l2")
            for (i in 11..15) put("hsk1_w$i", "hsk1_l3")
        }
        val hsk2WordToLesson = buildMap {
            for (i in 1..10) put("hsk2_w$i", "hsk2_l1")
        }

        // 1 validated step lesson (5 words) + 1 completed quiz (5 words) + 1 progressed word = 10 unique (overlap deduplicated)
        val hsk1Seen = QuickReviewEligibility.collectSeenWordIds(
            wordIdToLessonId = hsk1WordToLesson,
            completedStepLessonIds = setOf("hsk1_l1"),
            completedQuizLessonIds = setOf("hsk1_l1", "hsk1_l2"),
            progressedWordIds = setOf("hsk1_w1"),
        )
        assertEquals(10, hsk1Seen.size)
        assertTrue(QuickReviewEligibility.isAvailable(hsk1Seen.size))

        // Unstarted HSK 2 must remain hidden even though HSK 1 has >= 10 seen words
        val hsk2Seen = QuickReviewEligibility.collectSeenWordIds(
            wordIdToLessonId = hsk2WordToLesson,
            completedStepLessonIds = setOf("hsk1_l1"),
            completedQuizLessonIds = setOf("hsk1_l1", "hsk1_l2"),
            progressedWordIds = setOf("hsk1_w1"),
        )
        assertEquals(0, hsk2Seen.size)
        assertFalse(QuickReviewEligibility.isAvailable(hsk2Seen.size))

        // Only 1 lesson validated in HSK 1 (5 words) -> still hidden (< 10)
        val partialHsk1Seen = QuickReviewEligibility.collectSeenWordIds(
            wordIdToLessonId = hsk1WordToLesson,
            completedStepLessonIds = setOf("hsk1_l1"),
            completedQuizLessonIds = emptySet(),
            progressedWordIds = setOf("hsk1_w6", "hsk1_w7"),
        )
        assertEquals(7, partialHsk1Seen.size)
        assertFalse(QuickReviewEligibility.isAvailable(partialHsk1Seen.size))
    }

    @Test
    fun selectTargetWordsDrawsOnlyFromSeenWordsPrioritizingDueThenWeak() {
        val seenWords = (1..10).map { word("hsk1_w$it", if (it <= 5) "hsk1_l1" else "hsk1_l2") }
        val selected = QuickReviewEligibility.selectTargetWords(
            seenWords = seenWords,
            dueWordIds = listOf("hsk1_w8", "hsk1_unseen_99"),
            weakOrderedWordIds = listOf("hsk1_w3", "hsk1_w8", "hsk1_unseen_100"),
            maxCount = 8,
        )

        assertEquals(8, selected.size)
        assertEquals("hsk1_w8", selected[0].id)
        assertEquals("hsk1_w3", selected[1].id)
        val seenIds = seenWords.mapTo(HashSet()) { it.id }
        assertTrue(selected.all { it.id in seenIds })
    }
}
