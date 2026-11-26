package com.chinoisfacile.mandarin.domain.search

import com.chinoisfacile.mandarin.data.model.WordWithProgress
import java.text.Normalizer
import java.util.Locale

data class IndexedVocabWord(
    val item: WordWithProgress,
    val normalizedPinyin: String,
    val compactPinyin: String,
    val normalizedEnglish: String,
)

/**
 * Filters lexicon words by hanzi, pinyin (with or without tone marks/spaces),
 * and English translation (case- and accent-insensitive).
 */
object VocabularySearchFilter {

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val whitespaceRegex = Regex("\\s+")

    fun buildIndex(words: List<WordWithProgress>): List<IndexedVocabWord> =
        words.map { entry ->
            val normalizedPinyin = normalizeLatin(entry.word.pinyin)
            IndexedVocabWord(
                item = entry,
                normalizedPinyin = normalizedPinyin,
                compactPinyin = normalizedPinyin.replace(whitespaceRegex, ""),
                normalizedEnglish = normalizeLatin(entry.word.english),
            )
        }

    fun filterIndexed(
        indexed: List<IndexedVocabWord>,
        rawQuery: String,
    ): List<WordWithProgress> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return indexed.map { it.item }

        val normalizedQuery = normalizeLatin(trimmed)
        val compactQuery = normalizedQuery.replace(whitespaceRegex, "")

        val results = ArrayList<WordWithProgress>(indexed.size.coerceAtMost(64))
        for (entry in indexed) {
            val matched = entry.item.word.hanzi.contains(trimmed, ignoreCase = true) ||
                entry.normalizedPinyin.contains(normalizedQuery) ||
                (compactQuery.isNotEmpty() && entry.compactPinyin.contains(compactQuery)) ||
                entry.normalizedEnglish.contains(normalizedQuery)
            if (matched) {
                results += entry.item
            }
        }
        return results
    }

    fun filter(
        words: List<WordWithProgress>,
        rawQuery: String,
    ): List<WordWithProgress> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return words
        return filterIndexed(buildIndex(words), trimmed)
    }

    internal fun normalizeLatin(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return diacriticsRegex.replace(decomposed, "").lowercase(Locale.ROOT)
    }
}
