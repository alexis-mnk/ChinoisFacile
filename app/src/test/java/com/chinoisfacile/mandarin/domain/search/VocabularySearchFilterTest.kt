package com.chinoisfacile.mandarin.domain.search

import com.chinoisfacile.mandarin.data.model.MasteryLevel
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class VocabularySearchFilterTest {

    private val sampleWords = listOf(
        wordWithProgress(id = "1", hanzi = "你好", pinyin = "nǐ hǎo", french = "Bonjour"),
        wordWithProgress(id = "2", hanzi = "学习", pinyin = "xuéxí", french = "Étudier / Apprendre"),
        wordWithProgress(id = "3", hanzi = "茶", pinyin = "chá", french = "Thé"),
    )

    @Test
    fun `matches hanzi pinyin without tones and french without accents`() {
        assertEquals(sampleWords, VocabularySearchFilter.filter(sampleWords, "   "))

        assertEquals(listOf("1"), VocabularySearchFilter.filter(sampleWords, "你好").map { it.word.id })
        assertEquals(listOf("1"), VocabularySearchFilter.filter(sampleWords, "ni hao").map { it.word.id })
        assertEquals(listOf("1"), VocabularySearchFilter.filter(sampleWords, "nihao").map { it.word.id })
        assertEquals(listOf("2"), VocabularySearchFilter.filter(sampleWords, "etudier").map { it.word.id })
        assertEquals(listOf("3"), VocabularySearchFilter.filter(sampleWords, "THE").map { it.word.id })
    }

    @Test
    fun `precomputed index filters identically for hanzi pinyin and french`() {
        val indexed = VocabularySearchFilter.buildIndex(sampleWords)
        assertEquals(sampleWords, VocabularySearchFilter.filterIndexed(indexed, ""))
        assertEquals(listOf("1"), VocabularySearchFilter.filterIndexed(indexed, "nihao").map { it.word.id })
        assertEquals(listOf("1"), VocabularySearchFilter.filterIndexed(indexed, "ni hao").map { it.word.id })
        assertEquals(listOf("2"), VocabularySearchFilter.filterIndexed(indexed, "etudier").map { it.word.id })
        assertEquals(listOf("3"), VocabularySearchFilter.filterIndexed(indexed, "茶").map { it.word.id })
    }

    private fun wordWithProgress(
        id: String,
        hanzi: String,
        pinyin: String,
        french: String,
    ): WordWithProgress = WordWithProgress(
        word = VocabWord(
            id = id,
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = hanzi,
            pinyin = pinyin,
            french = french,
            exampleHanzi = hanzi,
            examplePinyin = pinyin,
            exampleFrench = french,
            exampleTokens = listOf(hanzi),
        ),
        repetitions = 0,
        easeFactor = 2.5,
        intervalDays = 0,
        nextReviewEpochDay = 0L,
        correctCount = 0,
        wrongCount = 0,
        masteryLevel = MasteryLevel.NEW,
    )
}
