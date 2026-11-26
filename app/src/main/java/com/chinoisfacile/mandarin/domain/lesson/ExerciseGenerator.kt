package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord
import kotlin.random.Random

/**
 * Generates a progressive 3-tier session:
 * 1. Simple recognition (Hanzi -> English, Audio -> English)
 * 2. Reverse recall (English -> Hanzi)
 * 3. Sentence ordering (SentenceOrder), only when 100% of tokens are already known.
 */
class ExerciseGenerator {

    fun isSentenceOrderEligible(
        word: VocabWord,
        knownTokens: Set<String>,
    ): Boolean {
        if (word.exampleTokens.size < MIN_SENTENCE_TOKENS) return false
        return word.exampleTokens.all { token -> isTokenAllowed(token, knownTokens) }
    }

    fun generateSession(
        targetWords: List<VocabWord>,
        distractorPool: List<VocabWord>,
        knownTokens: Set<String> = targetWords.mapTo(HashSet()) { it.hanzi },
        sessionSize: Int = DEFAULT_SESSION_SIZE,
        random: Random = Random.Default,
    ): List<Exercise> {
        require(targetWords.isNotEmpty()) { "Target word list cannot be empty." }
        if (sessionSize <= 0) return emptyList()

        val maxTargetLevel = targetWords.maxOf { it.hskLevel }
        val safeDistractorPool = distractorPool.filter { it.hskLevel <= maxTargetLevel }
        val pool = (targetWords + safeDistractorPool).distinctBy { it.id }
        val pinyinByHanzi = pool.associate { it.hanzi to it.pinyin }

        val eligibleSentenceWords = targetWords.filter { isSentenceOrderEligible(it, knownTokens) }
        val baseSentenceCount = if (sessionSize >= 3) (sessionSize * 3) / 10 else 0
        val sentenceCount = minOf(baseSentenceCount, eligibleSentenceWords.size)

        val remaining = sessionSize - sentenceCount
        val tier1Count = if (remaining <= 1) remaining else (remaining * 6 + 5) / 10
        val tier2Count = remaining - tier1Count

        val hanziToEnglishCount = (tier1Count + 1) / 2

        // Tier 1: simple recognition shuffled within tier
        val tier1Targets = targetWords.shuffled(random)
        val tier1Raw = ArrayList<Pair<Boolean, VocabWord>>(tier1Count)
        repeat(tier1Count) { slot ->
            val word = tier1Targets[slot % tier1Targets.size]
            val useHanziToEnglish = slot < hanziToEnglishCount
            tier1Raw += (useHanziToEnglish to word)
        }
        val tier1Shuffled = tier1Raw.shuffled(random)

        // Tier 2: reverse recall shuffled within tier
        val tier2Targets = targetWords.shuffled(random)
        val tier2Raw = List(tier2Count) { slot ->
            tier2Targets[slot % tier2Targets.size]
        }.shuffled(random)

        // Tier 3: eligible sentence ordering at the end of session
        val tier3Raw = eligibleSentenceWords
            .shuffled(random)
            .take(sentenceCount)
            .shuffled(random)

        val exercises = ArrayList<Exercise>(sessionSize)
        var exerciseIndex = 0

        for ((isHanziToEnglish, word) in tier1Shuffled) {
            exercises += if (isHanziToEnglish) {
                createHanziToEnglish(exerciseIndex++, word, pool, random)
            } else {
                createAudioToEnglish(exerciseIndex++, word, pool, random)
            }
        }

        for (word in tier2Raw) {
            exercises += createEnglishToHanzi(exerciseIndex++, word, pool, random)
        }

        for (word in tier3Raw) {
            exercises += createSentenceOrder(exerciseIndex++, word, pinyinByHanzi, random)
        }

        return exercises
    }

    private fun isTokenAllowed(token: String, knownTokens: Set<String>): Boolean =
        token in knownTokens ||
            token in ALLOWED_PROPER_NOUNS ||
            SIMPLE_DIGITS_REGEX.matches(token)

    private fun pickEnglishOptions(
        word: VocabWord,
        pool: List<VocabWord>,
        random: Random,
    ): List<String> {
        val distractors = pool
            .asSequence()
            .filter { it.id != word.id && it.english != word.english }
            .map { it.english }
            .distinct()
            .shuffled(random)
            .take(OPTION_COUNT - 1)
            .toList()
        return (distractors + word.english).shuffled(random)
    }

    private fun createHanziToEnglish(
        index: Int,
        word: VocabWord,
        pool: List<VocabWord>,
        random: Random,
    ): Exercise.HanziToEnglish = Exercise.HanziToEnglish(
        id = "ex_${index}_${word.id}_hte",
        word = word,
        options = pickEnglishOptions(word, pool, random),
        correctOption = word.english,
    )

    private fun createAudioToEnglish(
        index: Int,
        word: VocabWord,
        pool: List<VocabWord>,
        random: Random,
    ): Exercise.AudioToEnglish = Exercise.AudioToEnglish(
        id = "ex_${index}_${word.id}_ate",
        word = word,
        options = pickEnglishOptions(word, pool, random),
        correctOption = word.english,
    )

    private fun createEnglishToHanzi(
        index: Int,
        word: VocabWord,
        pool: List<VocabWord>,
        random: Random,
    ): Exercise.EnglishToHanzi {
        val distractorWords = pool
            .asSequence()
            .filter { it.id != word.id && it.hanzi != word.hanzi }
            .distinctBy { it.hanzi }
            .shuffled(random)
            .take(OPTION_COUNT - 1)
            .toList()

        val optionWords = (distractorWords + word).shuffled(random)
        val options = optionWords.map { it.hanzi }
        val optionPinyins = optionWords.associate { it.hanzi to it.pinyin }

        return Exercise.EnglishToHanzi(
            id = "ex_${index}_${word.id}_eth",
            word = word,
            options = options,
            optionPinyins = optionPinyins,
            correctOption = word.hanzi,
        )
    }

    private fun createSentenceOrder(
        index: Int,
        word: VocabWord,
        fallbackPinyinByHanzi: Map<String, String>,
        random: Random,
    ): Exercise.SentenceOrder {
        val expectedTokens = word.exampleTokens
        val alignedPinyins = SentencePinyinAligner.alignTokens(
            tokens = expectedTokens,
            examplePinyin = word.examplePinyin,
            fallbackPinyinByHanzi = fallbackPinyinByHanzi,
        )
        val chips = expectedTokens.mapIndexed { chipIndex, token ->
            TokenChip(
                id = chipIndex,
                text = token,
                pinyin = alignedPinyins.getOrElse(chipIndex) { "" },
            )
        }

        var shuffled = chips.shuffled(random)
        if (chips.size > 1 && shuffled.map { it.text } == expectedTokens) {
            shuffled = chips.drop(1) + chips.first()
        }

        return Exercise.SentenceOrder(
            id = "ex_${index}_${word.id}_ord",
            word = word,
            promptEnglish = word.exampleEnglish,
            expectedTokens = expectedTokens,
            shuffledChips = shuffled,
        )
    }

    companion object {
        const val DEFAULT_SESSION_SIZE = 10
        const val OPTION_COUNT = 4
        const val MIN_SENTENCE_TOKENS = 2

        private val SIMPLE_DIGITS_REGEX = Regex("^[零一二两三四五六七八九十百千万0-9]+$")
        private val ALLOWED_PROPER_NOUNS = setOf(
            "中国",
            "北京",
            "上海",
            "小明",
            "小王",
            "小李",
            "法国",
            "巴黎",
            "黄河",
        )
    }
}
