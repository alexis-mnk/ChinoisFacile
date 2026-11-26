package com.chinoisfacile.mandarin.domain.lesson

import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.VocabWord

@Immutable
data class TokenChip(
    val id: Int,
    val text: String,
    val pinyin: String = "",
)

@Immutable
sealed interface Exercise {
    val id: String
    val word: VocabWord

    @Immutable
    data class HanziToEnglish(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val correctOption: String,
    ) : Exercise

    @Immutable
    data class AudioToEnglish(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val correctOption: String,
    ) : Exercise

    @Immutable
    data class AudioToHanzi(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val correctOption: String,
    ) : Exercise

    @Immutable
    data class EnglishToHanzi(
        override val id: String,
        override val word: VocabWord,
        val options: List<String>,
        val optionPinyins: Map<String, String> = emptyMap(),
        val correctOption: String,
    ) : Exercise

    @Immutable
    data class Cloze(
        override val id: String,
        override val word: VocabWord,
        val sentencePrefix: String,
        val sentenceSuffix: String,
        val promptEnglish: String,
        val options: List<String>,
        val optionPinyins: Map<String, String> = emptyMap(),
        val correctOption: String,
    ) : Exercise

    @Immutable
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
