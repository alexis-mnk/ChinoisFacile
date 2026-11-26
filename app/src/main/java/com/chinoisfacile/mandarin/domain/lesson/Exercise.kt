package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord

data class TokenChip(
    val id: Int,
    val text: String,
    val pinyin: String = "",
)

sealed interface Exercise {
    val id: String
    val word: VocabWord

    data class HanziToEnglish(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val correctOption: String,
    ) : Exercise

    data class AudioToEnglish(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val correctOption: String,
    ) : Exercise

    data class AudioToHanzi(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val correctOption: String,
    ) : Exercise

    data class EnglishToHanzi(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val optionPinyins: Map<String, String> = emptyMap(),
        val correctOption: String,
    ) : Exercise

    data class SentenceOrder(
        override val id: String,
        override val word: VocabWord,
        val promptEnglish: String,
        val expectedTokens: List<String>,
        val shuffledChips: List<TokenChip>,
    ) : Exercise
}

data class WordSessionOutcome(
    val wordId: String,
    val correctAttempts: Int,
    val wrongAttempts: Int,
    val answeredWithoutPinyin: Boolean,
)
