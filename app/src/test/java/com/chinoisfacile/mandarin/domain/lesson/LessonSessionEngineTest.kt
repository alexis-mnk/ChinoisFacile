package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.domain.srs.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSessionEngineTest {

    private fun word(id: String, hanzi: String, pinyin: String, english: String) = VocabWord(
        id = id,
        lessonId = "hsk1_u1_l1",
        hskLevel = 1,
        hanzi = hanzi,
        pinyin = pinyin,
        english = english,
        exampleHanzi = hanzi,
        examplePinyin = pinyin,
        exampleEnglish = english,
        exampleTokens = listOf(hanzi),
    )

    private fun audioToEnglish(id: String, word: VocabWord) = Exercise.AudioToEnglish(
        id = id,
        word = word,
        options = listOf(word.english, "other 1", "other 2", "other 3"),
        correctOption = word.english,
    )

    private fun hanziToEnglish(id: String, word: VocabWord) = Exercise.HanziToEnglish(
        id = id,
        word = word,
        options = listOf(word.english, "other 1", "other 2", "other 3"),
        correctOption = word.english,
    )

    @Test
    fun skippingMidQuizAudioToEnglishCountsAsValidatedWithoutSm2PenaltyOrFalseMastery() {
        val w1 = word("w1", "喂", "wèi", "hello (on the phone)")
        val w2 = word("w2", "谢谢", "xièxie", "thanks")
        val engine = LessonSessionEngine()
        engine.start(
            listOf(
                audioToEnglish("ex1", w1),
                hanziToEnglish("ex2", w2),
            ),
        )

        val midResult = engine.skipListeningExercise()
        assertTrue(midResult is SessionAdvanceResult.NextExercise)
        midResult as SessionAdvanceResult.NextExercise
        assertEquals("ex2", midResult.exercise.id)
        assertEquals(1, midResult.completedCount)

        engine.recordAnswer(midResult.exercise, isCorrect = true, answeredWithoutPinyin = false)
        val finishResult = engine.advanceAfterFeedback(wasCorrect = true)
        assertTrue(finishResult is SessionAdvanceResult.Finished)
        val completion = (finishResult as SessionAdvanceResult.Finished).completion

        assertEquals(2, completion.completedCount)
        assertEquals(2, completion.totalCount)
        assertEquals(100, completion.accuracyPercent)
        assertEquals(
            2 * StreakCalculator.XP_PER_EXERCISE + StreakCalculator.XP_SESSION_BONUS,
            completion.earnedXp,
        )
        assertEquals(1, completion.outcomes.size)
        assertEquals("w2", completion.outcomes.single().wordId)
        assertTrue(completion.outcomes.none { it.wordId == "w1" })
    }

    @Test
    fun skippingLastAudioToEnglishProperlyFinishesSession() {
        val w1 = word("w1", "你好", "nǐ hǎo", "hello")
        val w2 = word("w2", "再见", "zàijiàn", "goodbye")
        val engine = LessonSessionEngine()
        engine.start(
            listOf(
                hanziToEnglish("ex1", w1),
                audioToEnglish("ex2", w2),
            ),
        )

        val first = engine.currentExercise!!
        engine.recordAnswer(first, isCorrect = true, answeredWithoutPinyin = true)
        val afterFirst = engine.advanceAfterFeedback(wasCorrect = true)
        assertTrue(afterFirst is SessionAdvanceResult.NextExercise)

        val finishResult = engine.skipListeningExercise()
        assertTrue(finishResult is SessionAdvanceResult.Finished)
        val completion = (finishResult as SessionAdvanceResult.Finished).completion

        assertEquals(2, completion.completedCount)
        assertEquals(2, completion.totalCount)
        assertEquals(100, completion.accuracyPercent)
        assertEquals(listOf("w1"), completion.outcomes.map { it.wordId })
        assertNull(engine.currentExercise)
    }

    @Test
    fun skippingSameWordAudioDoesNotAddExtraAttemptAndNonAudioCannotBeSkipped() {
        val w1 = word("w1", "喂", "wèi", "hello")
        val engine = LessonSessionEngine()
        engine.start(
            listOf(
                hanziToEnglish("ex1", w1),
                audioToEnglish("ex2", w1),
            ),
        )

        assertNull(engine.skipListeningExercise())
        val first = engine.currentExercise!!
        engine.recordAnswer(first, isCorrect = true, answeredWithoutPinyin = false)
        engine.advanceAfterFeedback(wasCorrect = true)

        val finish = engine.skipListeningExercise() as SessionAdvanceResult.Finished
        val outcome = finish.completion.outcomes.single()
        assertEquals("w1", outcome.wordId)
        assertEquals(1, outcome.correctAttempts)
        assertEquals(0, outcome.wrongAttempts)
    }
}
