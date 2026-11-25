package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord
import kotlin.random.Random

/**
 * Génère une session mélangée de 10 exercices répartis entre QCM Hanzi->Français,
 * QCM Audio->Hanzi et remise en ordre de mots.
 */
class ExerciseGenerator {

    private enum class ExerciseType {
        HANZI_TO_FRENCH,
        AUDIO_TO_HANZI,
        SENTENCE_ORDER,
    }

    fun generateSession(
        targetWords: List<VocabWord>,
        distractorPool: List<VocabWord>,
        sessionSize: Int = DEFAULT_SESSION_SIZE,
        random: Random = Random.Default,
    ): List<Exercise> {
        require(targetWords.isNotEmpty()) { "La liste des mots cibles ne peut pas être vide." }
        val pool = (targetWords + distractorPool).distinctBy { it.id }

        val typePlan = buildTypePlan(sessionSize, random)
        val shuffledTargets = targetWords.shuffled(random)

        return typePlan.mapIndexed { index, type ->
            val word = shuffledTargets[index % shuffledTargets.size]
            when (type) {
                ExerciseType.HANZI_TO_FRENCH -> createHanziToFrench(index, word, pool, random)
                ExerciseType.AUDIO_TO_HANZI -> createAudioToHanzi(index, word, pool, random)
                ExerciseType.SENTENCE_ORDER -> createSentenceOrder(index, word, random)
            }
        }
    }

    private fun buildTypePlan(sessionSize: Int, random: Random): List<ExerciseType> {
        if (sessionSize == DEFAULT_SESSION_SIZE) {
            return (
                List(4) { ExerciseType.HANZI_TO_FRENCH } +
                    List(3) { ExerciseType.AUDIO_TO_HANZI } +
                    List(3) { ExerciseType.SENTENCE_ORDER }
                ).shuffled(random)
        }
        val types = ExerciseType.entries
        return List(sessionSize) { index -> types[index % types.size] }.shuffled(random)
    }

    private fun createHanziToFrench(
        index: Int,
        word: VocabWord,
        pool: List<VocabWord>,
        random: Random,
    ): Exercise.HanziToFrench {
        val distractors = pool
            .asSequence()
            .filter { it.id != word.id && it.french != word.french }
            .map { it.french }
            .distinct()
            .shuffled(random)
            .take(OPTION_COUNT - 1)
            .toList()

        val options = (distractors + word.french).shuffled(random)
        return Exercise.HanziToFrench(
            id = "ex_${index}_${word.id}_htf",
            word = word,
            options = options,
            correctOption = word.french,
        )
    }

    private fun createAudioToHanzi(
        index: Int,
        word: VocabWord,
        pool: List<VocabWord>,
        random: Random,
    ): Exercise.AudioToHanzi {
        val distractors = pool
            .asSequence()
            .filter { it.id != word.id && it.hanzi != word.hanzi }
            .map { it.hanzi }
            .distinct()
            .shuffled(random)
            .take(OPTION_COUNT - 1)
            .toList()

        val options = (distractors + word.hanzi).shuffled(random)
        return Exercise.AudioToHanzi(
            id = "ex_${index}_${word.id}_ath",
            word = word,
            options = options,
            correctOption = word.hanzi,
        )
    }

    private fun createSentenceOrder(
        index: Int,
        word: VocabWord,
        random: Random,
    ): Exercise.SentenceOrder {
        val expectedTokens = word.exampleTokens
        val chips = expectedTokens.mapIndexed { chipIndex, token ->
            TokenChip(id = chipIndex, text = token)
        }

        var shuffled = chips.shuffled(random)
        if (chips.size > 1 && shuffled.map { it.text } == expectedTokens) {
            shuffled = chips.drop(1) + chips.first()
        }

        return Exercise.SentenceOrder(
            id = "ex_${index}_${word.id}_ord",
            word = word,
            promptFrench = word.exampleFrench,
            expectedTokens = expectedTokens,
            shuffledChips = shuffled,
        )
    }

    companion object {
        const val DEFAULT_SESSION_SIZE = 10
        const val OPTION_COUNT = 4
    }
}
