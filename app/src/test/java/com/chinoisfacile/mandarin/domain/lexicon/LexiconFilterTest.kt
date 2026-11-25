package com.chinoisfacile.mandarin.domain.lexicon

import com.chinoisfacile.mandarin.data.model.MasteryLevel
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class LexiconFilterTest {

    private val today = 20_000L

    private val sampleWords = listOf(
        word(id = "1", hanzi = "你好", pinyin = "nǐ hǎo", french = "Bonjour", mastery = MasteryLevel.MASTERED, nextDay = today + 10),
        word(id = "2", hanzi = "学习", pinyin = "xuéxí", french = "Étudier", mastery = MasteryLevel.LEARNING, nextDay = today),
        word(id = "3", hanzi = "茶", pinyin = "chá", french = "Thé", mastery = MasteryLevel.MASTERED, nextDay = today - 1),
        word(id = "4", hanzi = "水", pinyin = "shuǐ", french = "Eau", mastery = MasteryLevel.NEW, nextDay = 0L),
    )

    @Test
    fun `computeCounts counts all statuses and due words accurately`() {
        val counts = LexiconFilter.computeCounts(sampleWords, today)
        assertEquals(4, counts.all)
        assertEquals(2, counts.due)
        assertEquals(1, counts.learning)
        assertEquals(2, counts.mastered)
        assertEquals(1, counts.newCount)
    }

    @Test
    fun `filter combines status filter and accent-insensitive search`() {
        assertEquals(
            listOf("3", "2"),
            LexiconFilter.filter(sampleWords, LexiconStatusFilter.DUE, "", today).map { it.word.id },
        )
        assertEquals(
            listOf("2"),
            LexiconFilter.filter(sampleWords, LexiconStatusFilter.DUE, "etudier", today).map { it.word.id },
        )
        assertEquals(
            listOf("1"),
            LexiconFilter.filter(sampleWords, LexiconStatusFilter.MASTERED, "ni hao", today).map { it.word.id },
        )
        assertEquals(
            listOf("4"),
            LexiconFilter.filter(sampleWords, LexiconStatusFilter.NEW, "", today).map { it.word.id },
        )
    }

    @Test
    fun `buildSortedIndex and filter sort alphabetically by normalized pinyin regardless of tones`() {
        val unsorted = listOf(
            word(id = "da", hanzi = "大", pinyin = "dà", french = "Grand", mastery = MasteryLevel.LEARNING, nextDay = today),
            word(id = "nv", hanzi = "女", pinyin = "nǚ", french = "Femme", mastery = MasteryLevel.MASTERED, nextDay = today + 5),
            word(id = "chi", hanzi = "吃", pinyin = "chī", french = "Manger", mastery = MasteryLevel.LEARNING, nextDay = today),
            word(id = "ma3", hanzi = "马", pinyin = "mǎ", french = "Cheval", mastery = MasteryLevel.NEW, nextDay = 0L),
            word(id = "beijing", hanzi = "北京", pinyin = "Běi jīng", french = "Pékin", mastery = MasteryLevel.MASTERED, nextDay = today + 3),
            word(id = "ai", hanzi = "哀", pinyin = "āi", french = "Tristesse", mastery = MasteryLevel.LEARNING, nextDay = today),
            word(id = "nu3", hanzi = "努", pinyin = "nǔ", french = "S'efforcer", mastery = MasteryLevel.NEW, nextDay = 0L),
            word(id = "ba", hanzi = "八", pinyin = "bā", french = "Huit", mastery = MasteryLevel.MASTERED, nextDay = today + 2),
            word(id = "ma1", hanzi = "妈", pinyin = "mā", french = "Maman", mastery = MasteryLevel.NEW, nextDay = 0L),
        )

        val expectedAll = listOf("ai", "ba", "beijing", "chi", "da", "ma1", "ma3", "nu3", "nv")
        val indexed = LexiconFilter.buildSortedIndex(unsorted)

        assertEquals(expectedAll, indexed.map { it.item.word.id })
        assertEquals(
            expectedAll,
            LexiconFilter.filterIndexed(indexed, LexiconStatusFilter.ALL, "", today).map { it.word.id },
        )
        assertEquals(
            listOf("ai", "chi", "da"),
            LexiconFilter.filterIndexed(indexed, LexiconStatusFilter.LEARNING, "", today).map { it.word.id },
        )
        assertEquals(
            listOf("ba", "beijing", "nv"),
            LexiconFilter.filterIndexed(indexed, LexiconStatusFilter.MASTERED, "", today).map { it.word.id },
        )
        assertEquals(
            listOf("nu3", "nv"),
            LexiconFilter.filterIndexed(indexed, LexiconStatusFilter.ALL, "nu", today).map { it.word.id },
        )
    }

    private fun word(
        id: String,
        hanzi: String,
        pinyin: String,
        french: String,
        mastery: MasteryLevel,
        nextDay: Long,
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
        repetitions = if (mastery == MasteryLevel.NEW) 0 else 2,
        easeFactor = 2.5,
        intervalDays = if (mastery == MasteryLevel.MASTERED) 6 else 1,
        nextReviewEpochDay = nextDay,
        correctCount = if (mastery == MasteryLevel.NEW) 0 else 2,
        wrongCount = 0,
        masteryLevel = mastery,
    )
}
