package com.chinoisfacile.mandarin.domain.lesson

import com.chinoisfacile.mandarin.data.model.VocabWord
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGeneratorTest {

    private val generator = ExerciseGenerator()

    private val fullSentenceWords = listOf(
        VocabWord(
            id = "w1",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "你好",
            pinyin = "nǐ hǎo",
            english = "Hello",
            exampleHanzi = "你好吗？",
            examplePinyin = "Nǐ hǎo ma?",
            exampleEnglish = "How are you?",
            exampleTokens = listOf("你", "好", "吗"),
        ),
        VocabWord(
            id = "w2",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "谢谢",
            pinyin = "xièxie",
            english = "Thank you",
            exampleHanzi = "谢谢你的茶。",
            examplePinyin = "Xièxie nǐ de chá.",
            exampleEnglish = "Thank you for your tea.",
            exampleTokens = listOf("谢谢", "你", "的", "茶"),
        ),
        VocabWord(
            id = "w3",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "再见",
            pinyin = "zàijiàn",
            english = "Goodbye",
            exampleHanzi = "我们明天再见。",
            examplePinyin = "Wǒmen míngtiān zàijiàn.",
            exampleEnglish = "Goodbye, see you tomorrow.",
            exampleTokens = listOf("我们", "明天", "再见"),
        ),
        VocabWord(
            id = "w4",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "茶",
            pinyin = "chá",
            english = "Tea",
            exampleHanzi = "我喜欢喝茶。",
            examplePinyin = "Wǒ xǐhuan hē chá.",
            exampleEnglish = "I like drinking tea.",
            exampleTokens = listOf("我", "喜欢", "喝", "茶"),
        ),
        VocabWord(
            id = "w5",
            lessonId = "u1_l1",
            hskLevel = 1,
            hanzi = "水",
            pinyin = "shuǐ",
            english = "Water",
            exampleHanzi = "你喝水吗？",
            examplePinyin = "Nǐ hē shuǐ ma?",
            exampleEnglish = "Do you drink water?",
            exampleTokens = listOf("你", "喝", "水", "吗"),
        ),
    )

    private val quiz1Hsk1Words = listOf(
        VocabWord(
            id = "h1_001",
            lessonId = "hsk1_u1_l1",
            hskLevel = 1,
            hanzi = "你",
            pinyin = "nǐ",
            english = "You",
            exampleHanzi = "你好我很高兴。",
            examplePinyin = "Nǐ hǎo wǒ hěn gāo xìng.",
            exampleEnglish = "Hello, I am very happy.",
            exampleTokens = listOf("你", "好", "我", "很", "高兴"),
        ),
        VocabWord(
            id = "h1_002",
            lessonId = "hsk1_u1_l1",
            hskLevel = 1,
            hanzi = "好",
            pinyin = "hǎo",
            english = "Good / Well",
            exampleHanzi = "你好认识很高兴！",
            examplePinyin = "Nǐ hǎo rèn shi hěn gāo xìng!",
            exampleEnglish = "Hello, nice to meet you!",
            exampleTokens = listOf("你", "好", "认识", "很", "高兴"),
        ),
        VocabWord(
            id = "h1_003",
            lessonId = "hsk1_u1_l1",
            hskLevel = 1,
            hanzi = "谢谢",
            pinyin = "xièxie",
            english = "Thank you",
            exampleHanzi = "老师谢谢你！",
            examplePinyin = "Lǎo shī xiè xie nǐ!",
            exampleEnglish = "Thank you, teacher!",
            exampleTokens = listOf("老师", "谢谢", "你"),
        ),
        VocabWord(
            id = "h1_004",
            lessonId = "hsk1_u1_l1",
            hskLevel = 1,
            hanzi = "再见",
            pinyin = "zàijiàn",
            english = "Goodbye",
            exampleHanzi = "朋友明天再见！",
            examplePinyin = "Péng you míng tiān zài jiàn!",
            exampleEnglish = "Goodbye my friend, see you tomorrow!",
            exampleTokens = listOf("朋友", "明天", "再见"),
        ),
        VocabWord(
            id = "h1_005",
            lessonId = "hsk1_u1_l1",
            hskLevel = 1,
            hanzi = "喂",
            pinyin = "wèi",
            english = "Hello (phone)",
            exampleHanzi = "喂你好在家吗？",
            examplePinyin = "Wèi nǐ hǎo zài jiā ma?",
            exampleEnglish = "Hello, are you at home?",
            exampleTokens = listOf("喂", "你", "好", "在", "家", "吗"),
        ),
    )

    private fun tierRank(exercise: Exercise): Int = when (exercise) {
        is Exercise.HanziToEnglish,
        is Exercise.AudioToEnglish,
        -> 1
        is Exercise.AudioToHanzi,
        is Exercise.EnglishToHanzi,
        -> 2
        is Exercise.SentenceOrder -> 3
    }

    @Test
    fun `generates 10 progressive exercises ordered in 3 tiers with pinyin on sentence tiles`() {
        val allKnown = fullSentenceWords.flatMap { it.exampleTokens }.toSet() +
            fullSentenceWords.map { it.hanzi }

        val exercises = generator.generateSession(
            targetWords = fullSentenceWords,
            distractorPool = fullSentenceWords,
            knownTokens = allKnown,
            sessionSize = 10,
            random = Random(42),
        )

        assertEquals(10, exercises.size)
        assertEquals(4, exercises.count { it is Exercise.HanziToEnglish || it is Exercise.AudioToEnglish })
        assertEquals(3, exercises.count { it is Exercise.EnglishToHanzi })
        assertEquals(3, exercises.count { it is Exercise.SentenceOrder })

        val ranks = exercises.map(::tierRank)
        assertEquals(ranks.sorted(), ranks)
        assertTrue(exercises.take(4).all { tierRank(it) == 1 })
        assertTrue(exercises.subList(4, 7).all { it is Exercise.EnglishToHanzi })
        assertTrue(exercises.takeLast(3).all { it is Exercise.SentenceOrder })

        exercises.forEach { exercise ->
            when (exercise) {
                is Exercise.HanziToEnglish -> {
                    assertEquals(4, exercise.options.size)
                    assertEquals(4, exercise.options.distinct().size)
                    assertTrue(exercise.correctOption in exercise.options)
                }

                is Exercise.AudioToEnglish -> {
                    assertEquals(4, exercise.options.size)
                    assertEquals(4, exercise.options.distinct().size)
                    assertTrue(exercise.correctOption in exercise.options)
                }

                is Exercise.AudioToHanzi -> {
                    assertEquals(4, exercise.options.size)
                    assertTrue(exercise.correctOption in exercise.options)
                }

                is Exercise.EnglishToHanzi -> {
                    assertEquals(4, exercise.options.size)
                    assertEquals(4, exercise.options.distinct().size)
                    assertTrue(exercise.correctOption in exercise.options)
                    assertTrue(exercise.options.all { !exercise.optionPinyins[it].isNullOrBlank() })
                }

                is Exercise.SentenceOrder -> {
                    assertTrue(exercise.expectedTokens.all { it in allKnown })
                    assertEquals(
                        exercise.expectedTokens.sorted(),
                        exercise.shuffledChips.map { it.text }.sorted(),
                    )
                    assertTrue(exercise.shuffledChips.all { it.pinyin.isNotBlank() })
                    assertTrue(exercise.shuffledChips.all { it.pinyin == it.pinyin.lowercase() })
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

    @Test
    fun `never generates SentenceOrder when any sentence token is outside knownTokens on HSK1 Quiz 1`() {
        val quiz1Known = quiz1Hsk1Words.map { it.hanzi }.toSet()

        val exercises = generator.generateSession(
            targetWords = quiz1Hsk1Words,
            distractorPool = quiz1Hsk1Words,
            knownTokens = quiz1Known,
            sessionSize = 10,
            random = Random(13),
        )

        assertEquals(10, exercises.size)
        assertEquals(0, exercises.count { it is Exercise.SentenceOrder })

        val ranks = exercises.map(::tierRank)
        assertEquals(ranks.sorted(), ranks)
        assertEquals(6, exercises.count { it is Exercise.HanziToEnglish || it is Exercise.AudioToEnglish })
        assertEquals(4, exercises.count { it is Exercise.EnglishToHanzi })
    }

    @Test
    fun `partial eligibility caps SentenceOrder to eligible words and places them only at the end`() {
        val eligibleWord = VocabWord(
            id = "h1_016",
            lessonId = "hsk1_u1_l4",
            hskLevel = 1,
            hanzi = "不",
            pinyin = "bù",
            english = "No / Not",
            exampleHanzi = "我不喝茶。",
            examplePinyin = "Wǒ bù hē chá.",
            exampleEnglish = "I don't drink tea.",
            exampleTokens = listOf("我", "不", "喝", "茶"),
        )
        val singleTokenWord = VocabWord(
            id = "h1_017",
            lessonId = "hsk1_u1_l4",
            hskLevel = 1,
            hanzi = "吗",
            pinyin = "ma",
            english = "Question particle",
            exampleHanzi = "好吗？",
            examplePinyin = "Hǎo ma?",
            exampleEnglish = "Okay?",
            exampleTokens = listOf("好吗"),
        )

        val targets = listOf(eligibleWord, singleTokenWord) + quiz1Hsk1Words.take(3)
        val knownTokens = setOf("我", "不", "喝", "茶", "好吗", "你", "好", "谢谢")

        val exercises = generator.generateSession(
            targetWords = targets,
            distractorPool = targets,
            knownTokens = knownTokens,
            sessionSize = 10,
            random = Random(99),
        )

        assertEquals(10, exercises.size)
        val sentenceExercises = exercises.filterIsInstance<Exercise.SentenceOrder>()
        assertEquals(1, sentenceExercises.size)
        assertTrue(exercises.last() is Exercise.SentenceOrder)
        assertEquals("h1_016", sentenceExercises.first().word.id)

        val ranks = exercises.map(::tierRank)
        assertEquals(ranks.sorted(), ranks)
    }

    @Test
    fun `distractors never include words from a higher hsk level`() {
        val higherLevelWords = listOf(
            VocabWord(
                id = "h2_001",
                lessonId = "hsk2_u1_l1",
                hskLevel = 2,
                hanzi = "但是",
                pinyin = "dànshì",
                english = "But",
                exampleHanzi = "今天下雨但是我很高兴。",
                examplePinyin = "Jīntiān xiàyǔ dànshì wǒ hěn gāoxìng.",
                exampleEnglish = "It's raining today, but I'm very happy.",
                exampleTokens = listOf("今天", "下雨", "但是", "我", "很", "高兴"),
            ),
            VocabWord(
                id = "h4_001",
                lessonId = "hsk4_u1_l1",
                hskLevel = 4,
                hanzi = "经验",
                pinyin = "jīngyàn",
                english = "Experience",
                exampleHanzi = "她有丰富的教学经验。",
                examplePinyin = "Tā yǒu fēngfù de jiàoxué jīngyàn.",
                exampleEnglish = "She has rich teaching experience.",
                exampleTokens = listOf("她", "有", "丰富", "的", "教学", "经验"),
            ),
        )

        val allKnown = fullSentenceWords.flatMap { it.exampleTokens }.toSet() +
            fullSentenceWords.map { it.hanzi }

        val exercises = generator.generateSession(
            targetWords = fullSentenceWords,
            distractorPool = fullSentenceWords + higherLevelWords,
            knownTokens = allKnown,
            sessionSize = 10,
            random = Random(7),
        )

        val forbiddenHanzi = higherLevelWords.map { it.hanzi }.toSet()
        val forbiddenEnglish = higherLevelWords.map { it.english }.toSet()

        exercises.forEach { exercise ->
            when (exercise) {
                is Exercise.HanziToEnglish -> assertTrue(exercise.options.none { it in forbiddenEnglish })
                is Exercise.AudioToEnglish -> assertTrue(exercise.options.none { it in forbiddenEnglish })
                is Exercise.AudioToHanzi -> assertTrue(exercise.options.none { it in forbiddenHanzi })
                is Exercise.EnglishToHanzi -> assertTrue(exercise.options.none { it in forbiddenHanzi })
                is Exercise.SentenceOrder -> Unit
            }
        }
    }

    @Test
    fun `SentencePinyinAligner aligns spaced and joined pinyin syllables in lowercase`() {
        val spaced = SentencePinyinAligner.alignTokens(
            tokens = listOf("你", "好", "我", "很", "高兴"),
            examplePinyin = "Nǐ hǎo, wǒ hěn gāo xìng.",
        )
        assertEquals(listOf("nǐ", "hǎo", "wǒ", "hěn", "gāo xìng"), spaced)

        val joined = SentencePinyinAligner.alignTokens(
            tokens = listOf("法国", "人", "是", "我们", "的", "朋友"),
            examplePinyin = "Fǎguó rén shì wǒ men de péng you.",
        )
        assertEquals(listOf("fǎ guó", "rén", "shì", "wǒ men", "de", "péng you"), joined)
    }
}
