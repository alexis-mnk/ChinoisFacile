package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord
import kotlin.random.Random

/**
 * Generates a progressive 3-tier session:
 * 1. Simple recognition (Hanzi -> English, Audio -> English)
 * 2. Reverse & contextual recall (English -> Hanzi, Cloze fill-in-the-blank)
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

    fun isClozeEligible(word: VocabWord): Boolean {
        if (word.hanzi.isBlank() || word.exampleHanzi.isBlank() || word.exampleEnglish.isBlank()) {
            return false
        }
        if (word.exampleHanzi.length <= word.hanzi.length) return false
        if (word.exampleTokens.count { it == word.hanzi } != 1) return false
        return countOccurrences(word.exampleHanzi, word.hanzi) == 1
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

        // Tier 2: mix of Cloze (contextual recall) and EnglishToHanzi
        val desiredClozeCount = tier2Count / 2
        val eligibleClozeWords = targetWords.filter(::isClozeEligible)
        val clozeTargets = eligibleClozeWords.shuffled(random).take(desiredClozeCount)
        val clozeWordIds = clozeTargets.mapTo(HashSet()) { it.id }

        val remainingTier2Count = tier2Count - clozeTargets.size
        val englishToHanziPool = targetWords
            .filter { it.id !in clozeWordIds }
            .ifEmpty { targetWords }
            .shuffled(random)
        val englishToHanziTargets = List(remainingTier2Count) { slot ->
            englishToHanziPool[slot % englishToHanziPool.size]
        }

        val tier2Items = (clozeTargets.map { true to it } + englishToHanziTargets.map { false to it })
            .shuffled(random)

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

        for ((isCloze, word) in tier2Items) {
            exercises += if (isCloze) {
                createCloze(exerciseIndex++, word, targetWords, pool, random)
            } else {
                createEnglishToHanzi(exerciseIndex++, word, targetWords, pool, random)
            }
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

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var searchFrom = 0
        while (searchFrom <= haystack.length - needle.length) {
            val found = haystack.indexOf(needle, startIndex = searchFrom)
            if (found < 0) break
            count += 1
            searchFrom = found + needle.length
        }
        return count
    }

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

    private fun pickHanziOptionWords(
        word: VocabWord,
        targetWords: List<VocabWord>,
        pool: List<VocabWord>,
        random: Random,
    ): List<VocabWord> {
        val needed = OPTION_COUNT - 1
        val sameLessonDistractors = (targetWords + pool)
            .asSequence()
            .filter { it.id != word.id && it.hanzi != word.hanzi && it.lessonId == word.lessonId }
            .distinctBy { it.hanzi }
            .shuffled(random)
            .take(needed)
            .toList()

        val selectedHanzi = sameLessonDistractors.mapTo(HashSet()) { it.hanzi }
        selectedHanzi += word.hanzi

        val fallbackDistractors = if (sameLessonDistractors.size < needed) {
            val remaining = needed - sameLessonDistractors.size
            val sameLevelPool = pool
                .filter { it.hanzi !in selectedHanzi && it.hskLevel == word.hskLevel }
                .distinctBy { it.hanzi }
                .shuffled(random)
            val lowerLevelPool = pool
                .filter { it.hanzi !in selectedHanzi && it.hskLevel < word.hskLevel }
                .distinctBy { it.hanzi }
                .shuffled(random)
            (sameLevelPool + lowerLevelPool)
                .distinctBy { it.hanzi }
                .take(remaining)
        } else {
            emptyList()
        }

        return (sameLessonDistractors + fallbackDistractors + word).shuffled(random)
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
        targetWords: List<VocabWord>,
        pool: List<VocabWord>,
        random: Random,
    ): Exercise.EnglishToHanzi {
        val optionWords = pickHanziOptionWords(word, targetWords, pool, random)
        return Exercise.EnglishToHanzi(
            id = "ex_${index}_${word.id}_eth",
            word = word,
            options = optionWords.map { it.hanzi },
            optionPinyins = optionWords.associate { it.hanzi to it.pinyin },
            correctOption = word.hanzi,
        )
    }

    private fun createCloze(
        index: Int,
        word: VocabWord,
        targetWords: List<VocabWord>,
        pool: List<VocabWord>,
        random: Random,
    ): Exercise.Cloze {
        val hitIndex = word.exampleHanzi.indexOf(word.hanzi)
        val prefix = word.exampleHanzi.substring(0, hitIndex)
        val suffix = word.exampleHanzi.substring(hitIndex + word.hanzi.length)
        val optionWords = pickHanziOptionWords(word, targetWords, pool, random)
        return Exercise.Cloze(
            id = "ex_${index}_${word.id}_cloze",
            word = word,
            sentencePrefix = prefix,
            sentenceSuffix = suffix,
            promptEnglish = word.exampleEnglish,
            options = optionWords.map { it.hanzi },
            optionPinyins = optionWords.associate { it.hanzi to it.pinyin },
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
