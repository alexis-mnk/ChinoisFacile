package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord

data class TokenChip(
    val id: Int,
    val text: String,
)

sealed interface Exercise {
    val id: String
    val word: VocabWord

    data class HanziToFrench(
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

    data class SentenceOrder(
        override val id: String,
        override val word: VocabWord,
        val promptFrench: String,
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
