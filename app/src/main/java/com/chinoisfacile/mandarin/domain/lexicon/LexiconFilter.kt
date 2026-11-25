package com.chinoisfacile.mandarin.domain.lexicon

import com.chinoisfacile.mandarin.data.model.MasteryLevel
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import com.chinoisfacile.mandarin.domain.search.IndexedVocabWord
import com.chinoisfacile.mandarin.domain.search.VocabularySearchFilter

enum class LexiconStatusFilter {
    ALL,
    DUE,
    LEARNING,
    MASTERED,
    NEW,
}

data class LexiconStatusCounts(
    val all: Int = 0,
    val due: Int = 0,
    val learning: Int = 0,
    val mastered: Int = 0,
    val newCount: Int = 0,
)

object LexiconFilter {

    private val pinyinIndexedComparator: Comparator<IndexedVocabWord> = compareBy(
        { it.compactPinyin.replace("'", "") },
        { it.item.word.pinyin },
        { it.item.word.hanzi },
    )

    fun buildSortedIndex(words: List<WordWithProgress>): List<IndexedVocabWord> =
        VocabularySearchFilter.buildIndex(words).sortedWith(pinyinIndexedComparator)

    fun isDueToday(item: WordWithProgress, todayEpochDay: Long): Boolean =
        item.masteryLevel != MasteryLevel.NEW && item.nextReviewEpochDay <= todayEpochDay

    fun computeCounts(
        words: List<WordWithProgress>,
        todayEpochDay: Long,
    ): LexiconStatusCounts {
        var due = 0
        var learning = 0
        var mastered = 0
        var newCount = 0

        for (item in words) {
            if (isDueToday(item, todayEpochDay)) {
                due += 1
            }
            when (item.masteryLevel) {
                MasteryLevel.MASTERED -> mastered += 1
                MasteryLevel.LEARNING -> learning += 1
                MasteryLevel.NEW -> newCount += 1
            }
        }

        return LexiconStatusCounts(
            all = words.size,
            due = due,
            learning = learning,
            mastered = mastered,
            newCount = newCount,
        )
    }

    fun filterIndexed(
        indexed: List<IndexedVocabWord>,
        statusFilter: LexiconStatusFilter,
        searchQuery: String,
        todayEpochDay: Long,
    ): List<WordWithProgress> {
        val statusMatched = when (statusFilter) {
            LexiconStatusFilter.ALL -> indexed
            LexiconStatusFilter.DUE -> indexed.filter { isDueToday(it.item, todayEpochDay) }
            LexiconStatusFilter.LEARNING -> indexed.filter { it.item.masteryLevel == MasteryLevel.LEARNING }
            LexiconStatusFilter.MASTERED -> indexed.filter { it.item.masteryLevel == MasteryLevel.MASTERED }
            LexiconStatusFilter.NEW -> indexed.filter { it.item.masteryLevel == MasteryLevel.NEW }
        }
        return VocabularySearchFilter.filterIndexed(statusMatched, searchQuery)
    }

    fun filter(
        words: List<WordWithProgress>,
        statusFilter: LexiconStatusFilter,
        searchQuery: String,
        todayEpochDay: Long,
    ): List<WordWithProgress> = filterIndexed(
        indexed = buildSortedIndex(words),
        statusFilter = statusFilter,
        searchQuery = searchQuery,
        todayEpochDay = todayEpochDay,
    )
}
