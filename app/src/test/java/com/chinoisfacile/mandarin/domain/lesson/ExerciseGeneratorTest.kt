package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGeneratorTest {

    private val generator = ExerciseGenerator()

    private val sampleWords = listOf(
        VocabWord(
            id = "w1",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "你好",
            pinyin = "nǐ hǎo",
            french = "Bonjour",
            exampleHanzi = "你好吗？",
            examplePinyin = "Nǐ hǎo ma?",
            exampleFrench = "Comment vas-tu ?",
            exampleTokens = listOf("你", "好", "吗"),
        ),
        VocabWord(
            id = "w2",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "谢谢",
            pinyin = "xièxie",
            french = "Merci",
            exampleHanzi = "谢谢你的茶。",
            examplePinyin = "Xièxie nǐ de chá.",
            exampleFrench = "Merci pour ton thé.",
            exampleTokens = listOf("谢谢", "你", "的", "茶"),
        ),
        VocabWord(
            id = "w3",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "再见",
            pinyin = "zàijiàn",
            french = "Au revoir",
            exampleHanzi = "我们明天再见。",
            examplePinyin = "Wǒmen míngtiān zàijiàn.",
            exampleFrench = "Au revoir, à demain.",
            exampleTokens = listOf("我们", "明天", "再见"),
        ),
        VocabWord(
            id = "w4",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "茶",
            pinyin = "chá",
            french = "Thé",
            exampleHanzi = "我喜欢喝茶。",
            examplePinyin = "Wǒ xǐhuan hē chá.",
            exampleFrench = "J'aime boire du thé.",
            exampleTokens = listOf("我", "喜欢", "喝", "茶"),
        ),
        VocabWord(
            id = "w5",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "水",
            pinyin = "shuǐ",
            french = "Eau",
            exampleHanzi = "你喝水吗？",
            examplePinyin = "Nǐ hē shuǐ ma?",
            exampleFrench = "Bois-tu de l'eau ?",
            exampleTokens = listOf("你", "喝", "水", "吗"),
        ),
    )

    @Test
    fun `generates 10 mixed exercises with valid distractors and tokens`() {
        val exercises = generator.generateSession(
            targetWords = sampleWords,
            distractorPool = sampleWords,
            sessionSize = 10,
            random = Random(42),
        )

        assertEquals(10, exercises.size)
        assertEquals(4, exercises.count { it is Exercise.HanziToFrench })
        assertEquals(3, exercises.count { it is Exercise.AudioToHanzi })
        assertEquals(3, exercises.count { it is Exercise.SentenceOrder })

        exercises.forEach { exercise ->
            when (exercise) {
                is Exercise.HanziToFrench -> {
                    assertEquals(4, exercise.options.size)
                    assertEquals(4, exercise.options.distinct().size)
                    assertTrue(exercise.correctOption in exercise.options)
                }

                is Exercise.AudioToHanzi -> {
                    assertEquals(4, exercise.options.size)
                    assertEquals(4, exercise.options.distinct().size)
                    assertTrue(exercise.correctOption in exercise.options)
                }

                is Exercise.SentenceOrder -> {
                    assertEquals(
                        exercise.expectedTokens.sorted(),
                        exercise.shuffledChips.map { it.text }.sorted(),
                    )
                    if (exercise.expectedTokens.size > 1) {
                        assertNotEquals(
                            exercise.expectedTokens,
                            exercise.shuffledChips.map { it.text },
                        )
                    }
                }
            }
        }
    }
}
