package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.domain.srs.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun cloze(id: String, word: VocabWord) = Exercise.Cloze(
        id = id,
        word = word,
        sentencePrefix = "我",
        sentenceSuffix = "你。",
        promptEnglish = "I ${word.english} you.",
        options = listOf(word.hanzi, "看", "吃", "喝"),
        optionPinyins = mapOf(word.hanzi to word.pinyin, "看" to "kàn", "吃" to "chī", "喝" to "hē"),
        correctOption = word.hanzi,
    )

    @Test
    fun perfectSessionWithoutMistakesFinishesDirectlyWithoutEnteringReview() {
        val w1 = word("w1", "你", "nǐ", "you")
        val w2 = word("w2", "爱", "ài", "love")
        val engine = LessonSessionEngine()
        engine.start(
            listOf(
                hanziToEnglish("ex1", w1),
                cloze("ex2", w2),
            ),
        )

        val ex1 = engine.currentExercise!!
        engine.recordAnswer(ex1, isCorrect = true, answeredWithoutPinyin = true)
        val afterEx1 = engine.advanceAfterFeedback(wasCorrect = true) as SessionAdvanceResult.NextExercise
        assertEquals(SessionPhase.MAIN, afterEx1.phase)
        assertEquals(1, afterEx1.completedCount)

        val ex2 = engine.currentExercise!!
        engine.recordAnswer(ex2, isCorrect = true, answeredWithoutPinyin = false)
        val finish = engine.advanceAfterFeedback(wasCorrect = true)
        assertTrue(finish is SessionAdvanceResult.Finished)
        val completion = (finish as SessionAdvanceResult.Finished).completion
        assertEquals(2, completion.completedCount)
        assertEquals(100, completion.accuracyPercent)
        assertEquals(listOf("w1", "w2"), completion.outcomes.map { it.wordId })
    }

    @Test
    fun mistakesEnterDedicatedEndReviewLoopWithUniqueIdsAndKeepFirstTryScoreAndSm2() {
        val w1 = word("w1", "你好", "nǐ hǎo", "hello")
        val w2 = word("w2", "爱", "ài", "love")
        val w3 = word("w3", "水", "shuǐ", "water")
        val engine = LessonSessionEngine()
        engine.start(
            listOf(
                hanziToEnglish("ex1_hte", w1),
                cloze("ex2_cloze", w2),
                hanziToEnglish("ex3_hte", w3),
            ),
        )

        // Question 1: WRONG on first try -> progress must still advance (1 / 3)
        engine.recordAnswer(engine.currentExercise!!, isCorrect = false, answeredWithoutPinyin = false)
        val after1 = engine.advanceAfterFeedback(wasCorrect = false) as SessionAdvanceResult.NextExercise
        assertEquals(SessionPhase.MAIN, after1.phase)
        assertEquals(1, after1.completedCount)
        assertEquals("ex2_cloze", after1.exercise.id)

        // Question 2: RIGHT on first try (2 / 3)
        engine.recordAnswer(engine.currentExercise!!, isCorrect = true, answeredWithoutPinyin = true)
        val after2 = engine.advanceAfterFeedback(wasCorrect = true) as SessionAdvanceResult.NextExercise
        assertEquals(SessionPhase.MAIN, after2.phase)
        assertEquals(2, after2.completedCount)
        assertEquals("ex3_hte", after2.exercise.id)

        // Question 3: WRONG on first try -> main pass finishes (3 / 3), enters MISTAKE_REVIEW (1 / 2)
        engine.recordAnswer(engine.currentExercise!!, isCorrect = false, answeredWithoutPinyin = false)
        val enterReview = engine.advanceAfterFeedback(wasCorrect = false) as SessionAdvanceResult.NextExercise
        assertEquals(SessionPhase.MISTAKE_REVIEW, enterReview.phase)
        assertEquals(3, enterReview.completedCount)
        assertEquals(1, enterReview.mistakeReviewCurrent)
        assertEquals(2, enterReview.mistakeReviewTotal)
        val firstReviewId = enterReview.exercise.id
        assertEquals("w1", enterReview.exercise.word.id)

        // Fail Question 1 AGAIN in mistake review -> re-queued at tail with a brand-new unique ID
        engine.recordAnswer(enterReview.exercise, isCorrect = false, answeredWithoutPinyin = false)
        val nextReview = engine.advanceAfterFeedback(wasCorrect = false) as SessionAdvanceResult.NextExercise
        assertEquals(SessionPhase.MISTAKE_REVIEW, nextReview.phase)
        assertEquals(3, nextReview.completedCount)
        assertEquals(1, nextReview.mistakeReviewCurrent)
        assertEquals(2, nextReview.mistakeReviewTotal)
        assertEquals("w3", nextReview.exercise.word.id)

        // Pass Question 3 in mistake review -> back to Question 1 (now 2 / 2) with fresh ID
        engine.recordAnswer(nextReview.exercise, isCorrect = true, answeredWithoutPinyin = false)
        val lastReview = engine.advanceAfterFeedback(wasCorrect = true) as SessionAdvanceResult.NextExercise
        assertEquals(SessionPhase.MISTAKE_REVIEW, lastReview.phase)
        assertEquals(2, lastReview.mistakeReviewCurrent)
        assertEquals(2, lastReview.mistakeReviewTotal)
        assertEquals("w1", lastReview.exercise.word.id)
        assertNotEquals(firstReviewId, lastReview.exercise.id)

        // Fail last remaining review item twice in a row -> ID changes each time & counter stays clamped 2 / 2
        engine.recordAnswer(lastReview.exercise, isCorrect = false, answeredWithoutPinyin = false)
        val repeatLast = engine.advanceAfterFeedback(wasCorrect = false) as SessionAdvanceResult.NextExercise
        assertEquals(2, repeatLast.mistakeReviewCurrent)
        assertEquals(2, repeatLast.mistakeReviewTotal)
        assertNotEquals(lastReview.exercise.id, repeatLast.exercise.id)

        // Finally pass it -> session completes with STRICT first-try accuracy (1/3 = 33%) and SM-2 wrongAttempts = 1
        engine.recordAnswer(repeatLast.exercise, isCorrect = true, answeredWithoutPinyin = false)
        val finish = engine.advanceAfterFeedback(wasCorrect = true)
        assertTrue(finish is SessionAdvanceResult.Finished)
        val completion = (finish as SessionAdvanceResult.Finished).completion

        assertEquals(3, completion.completedCount)
        assertEquals(3, completion.totalCount)
        assertEquals(33, completion.accuracyPercent)
        assertEquals(
            3 * StreakCalculator.XP_PER_EXERCISE + StreakCalculator.XP_SESSION_BONUS,
            completion.earnedXp,
        )

        val outcomesById = completion.outcomes.associateBy { it.wordId }
        assertEquals(0, outcomesById.getValue("w1").correctAttempts)
        assertEquals(1, outcomesById.getValue("w1").wrongAttempts)
        assertEquals(1, outcomesById.getValue("w2").correctAttempts)
        assertEquals(0, outcomesById.getValue("w2").wrongAttempts)
        assertTrue(outcomesById.getValue("w2").answeredWithoutPinyin)
        assertEquals(0, outcomesById.getValue("w3").correctAttempts)
        assertEquals(1, outcomesById.getValue("w3").wrongAttempts)
    }

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

        val midResult = engine.skipListeningExercise() as SessionAdvanceResult.NextExercise
        assertEquals("ex2", midResult.exercise.id)
        assertEquals(1, midResult.completedCount)
        assertEquals(SessionPhase.MAIN, midResult.phase)

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
    fun skippingAudioToEnglishDuringMistakeReviewFinishesWithoutAlteringFirstTryPenalty() {
        val w1 = word("w1", "喂", "wèi", "hello (on the phone)")
        val engine = LessonSessionEngine()
        engine.start(listOf(audioToEnglish("ex1", w1)))

        engine.recordAnswer(engine.currentExercise!!, isCorrect = false, answeredWithoutPinyin = false)
        val enterReview = engine.advanceAfterFeedback(wasCorrect = false) as SessionAdvanceResult.NextExercise
        assertEquals(SessionPhase.MISTAKE_REVIEW, enterReview.phase)
        assertEquals(1, enterReview.mistakeReviewCurrent)
        assertEquals(1, enterReview.mistakeReviewTotal)

        val finish = engine.skipListeningExercise() as SessionAdvanceResult.Finished
        assertEquals(0, finish.completion.accuracyPercent)
        val outcome = finish.completion.outcomes.single()
        assertEquals(0, outcome.correctAttempts)
        assertEquals(1, outcome.wrongAttempts)
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
