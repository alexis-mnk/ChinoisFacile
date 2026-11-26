package com.chinoisfacile.mandarin.data

import com.chinoisfacile.mandarin.data.model.CatalogDto
import java.io.File
import java.text.Normalizer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonParsingTest {

    @Test
    fun `all hsk1 to hsk6 catalog json files parse cleanly with unique ids, hanzi and English titles`() {
        val json = Json { ignoreUnknownKeys = true }
        val toneRegex = Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
        val neutralParticles = setOf(
            "ma", "ne", "de", "le", "ba", "zhe", "guo", "a", "ya", "wa", "na", "la", "o", "en",
        )
        val frenchDiacritics = Regex("[éèêëàâçùûôîœæÉÈÊËÀÂÇÙÛÔÎŒÆ]")
        val globalUnitIds = mutableSetOf<String>()
        val globalLessonIds = mutableSetOf<String>()
        val globalWordIds = mutableSetOf<String>()
        val globalHanzi = mutableSetOf<String>()

        for (level in 1..6) {
            val candidates = listOf(
                File("src/main/assets/catalog/hsk$level.json"),
                File("app/src/main/assets/catalog/hsk$level.json"),
            )
            val jsonFile = candidates.firstOrNull { it.exists() }
                ?: error("Missing catalog/hsk$level.json from ${File(".").absolutePath}")

            val catalog = json.decodeFromString<CatalogDto>(jsonFile.readText())
            assertEquals(level, catalog.hskLevel)
            assertTrue(catalog.catalogVersion >= 11)
            assertTrue("Expected at least 3 units for HSK $level", catalog.units.size >= 3)

            val levelLessonIds = mutableSetOf<String>()
            val lessonOrders = mutableListOf<Int>()
            catalog.units.forEach { unit ->
                assertTrue("Duplicate unit ID ${unit.id}", globalUnitIds.add(unit.id))
                assertTrue("Unit title must start with 'Unit ': ${unit.title}", unit.title.startsWith("Unit "))
                assertTrue("French diacritic in unit title: ${unit.title}", !frenchDiacritics.containsMatchIn(unit.title))
                unit.lessons.forEach { lesson ->
                    assertTrue("Duplicate lesson ID ${lesson.id}", globalLessonIds.add(lesson.id))
                    assertTrue("Quiz title must start with 'Quiz ': ${lesson.title}", lesson.title.startsWith("Quiz "))
                    assertTrue("French diacritic in quiz title: ${lesson.title}", !frenchDiacritics.containsMatchIn(lesson.title))
                    levelLessonIds += lesson.id
                    lessonOrders += lesson.orderIndex
                }
            }

            assertTrue("Expected at least 6 quizzes for HSK $level", levelLessonIds.size >= 6)
            assertEquals((1..levelLessonIds.size).toList(), lessonOrders.sorted())
            assertTrue("Fewer than 30 words for HSK $level", catalog.words.size >= 30)

            if (level == 1) {
                assertEquals(6, catalog.units.size)
                assertEquals(30, levelLessonIds.size)
                assertEquals(150, catalog.words.size)
            } else {
                val minExpectedWords = when (level) {
                    2 -> 140
                    3 -> 270
                    4 -> 550
                    5 -> 1200
                    else -> 2300
                }
                assertTrue(
                    "Expected >= $minExpectedWords words for HSK $level, found ${catalog.words.size}",
                    catalog.words.size >= minExpectedWords,
                )
            }

            catalog.words.forEach { word ->
                assertTrue("Bad word ID prefix ${word.id}", word.id.startsWith("hsk${level}_"))
                assertTrue("Duplicate word ID ${word.id}", globalWordIds.add(word.id))
                assertTrue("Duplicate hanzi ${word.hanzi} (${word.id})", globalHanzi.add(word.hanzi))
                assertTrue("Empty hanzi for ${word.id}", word.hanzi.isNotBlank())
                assertTrue("Empty pinyin for ${word.id}", word.pinyin.isNotBlank())
                val hasTone = toneRegex.containsMatchIn(word.pinyin)
                val isNeutralParticle = word.pinyin.trim().lowercase() in neutralParticles
                assertTrue("Missing tone mark for ${word.id} (${word.pinyin})", hasTone || isNeutralParticle)
                assertTrue("Empty English translation for ${word.id}", word.english.isNotBlank())
                assertTrue("Unknown lessonId for ${word.id}", word.lessonId in levelLessonIds)
                assertTrue("Insufficient tokens for ${word.id}", word.example.tokens.size >= 3)
                assertEquals(
                    "Duplicate tokens in sentence for ${word.id}",
                    word.example.tokens.size,
                    word.example.tokens.distinct().size,
                )

                assertTrue(
                    "Comma found inside example.tokens for ${word.id}",
                    word.example.tokens.none { '，' in it },
                )

                val expectedJoined = word.example.hanzi
                    .trimEnd('。', '？', '！', '，', '、', '；', '：')
                    .replace("，", "")
                assertEquals(
                    "Inconsistent tokens for ${word.id}",
                    expectedJoined,
                    word.example.tokens.joinToString(""),
                )
            }
        }
    }

    @Test
    fun `hsk1 to hsk5 example tokens strictly stay within cumulative HSK 1 to N vocabulary`() {
        val json = Json { ignoreUnknownKeys = true }
        val digitsRegex = Regex("^[零一二两三四五六七八九十百千万0-9]+$")
        val allowedSimpleProperNouns = setOf(
            "中国", "北京", "上海", "小明", "小王", "小李", "法国", "巴黎", "天", "黄河",
        )

        val catalogsByLevel = (1..6).associateWith { level ->
            val candidates = listOf(
                File("src/main/assets/catalog/hsk$level.json"),
                File("app/src/main/assets/catalog/hsk$level.json"),
            )
            val jsonFile = candidates.firstOrNull { it.exists() }
                ?: error("Missing catalog/hsk$level.json from ${File(".").absolutePath}")
            json.decodeFromString<CatalogDto>(jsonFile.readText())
        }

        val wordToLevel = mutableMapOf<String, Int>()
        catalogsByLevel.forEach { (level, catalog) ->
            catalog.words.forEach { word ->
                wordToLevel[word.hanzi] = level
            }
        }

        val violations = mutableListOf<String>()
        for (level in 1..5) {
            val allowedVocab = wordToLevel
                .filterValues { it <= level }
                .keys + allowedSimpleProperNouns
            val words = catalogsByLevel.getValue(level).words
            for (word in words) {
                for (token in word.example.tokens) {
                    val levelOfToken = wordToLevel[token]
                    if (levelOfToken != null && levelOfToken > level) {
                        violations += "HSK$level ${word.id} (${word.hanzi}): token '$token' from HSK$levelOfToken"
                    } else if (token !in allowedVocab && !digitsRegex.matches(token)) {
                        violations += "HSK$level ${word.id} (${word.hanzi}): out-of-catalog token '$token'"
                    }
                }
            }
        }

        assertTrue(
            "Vocabulary ceiling violations (${violations.size}):\n" +
                violations.take(20).joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `all hsk1 to hsk6 words have clean English glosses, non-surname pinyin and matching example pinyin`() {
        val json = Json { ignoreUnknownKeys = true }
        val allowedProperNouns = setOf(
            "中国", "北京", "汉语", "中文", "黄河", "亚洲", "京剧", "长城", "长江",
            "元旦", "华裔", "国庆节", "礼拜天", "除夕", "互联网", "元宵节", "华侨",
            "国务院", "摄氏度", "欧洲", "正月", "端午节", "重阳节",
        )
        val surnameRegex = Regex("""\bsurname\b|\bfamily name\b|\bclan name\b""", RegexOption.IGNORE_CASE)
        val vulgarOrSlangRegex = Regex(
            """(\(slang\)|\(vulgar\)|\(dialect\)|\(taboo\)|\(derog\.\)|\bradical\b|\bfuck\w*\b|\bshit\w*\b|\bbitch\w*\b|\bcunt\w*\b|\bwhore\w*\b|\bporn\w*\b|\bpenis\b|\bvagina\b|\bbastard\w*\b|\basshole\w*\b)""",
            RegexOption.IGNORE_CASE,
        )
        val sandhiOrParticleWords = setOf(
            "一", "不", "的", "了", "吗", "呢", "吧", "着", "过", "得", "地", "啊",
            "嘛", "哇", "啦", "哦", "嗯",
        )
        val nonPinyinChars = Regex("[^a-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]")
        val combiningMarks = Regex("\\p{M}+")

        fun normPy(raw: String): String =
            raw.lowercase().replace("v", "ü").replace(nonPinyinChars, "")

        fun stripTones(norm: String): String =
            Normalizer.normalize(norm, Normalizer.Form.NFD).replace(combiningMarks, "")

        val mechanicalTemplateRegex = Regex(
            """(: |\([^)]+\)|This concept "|Everyone completes the task|She expressed her idea|""" +
                """Let us keep moving forward|Please get ready|Our environment is truly |""" +
                """Everyone needs to |Let's all .+ together!|We are going to |He will go to |""" +
                """We have started to |I think we can |Let's .+ tomorrow instead\.|""" +
                """We should seriously |I believe she can |I really want to to |""" +
                """My friend can also |to to |The teacher is introducing |""" +
                """Everyone knows about |I really like the new |Today we are looking at |""" +
                """The school has |This matter is extremely |I find this very |""" +
                """This morning is really |This place is very |He keeps working |""" +
                """She prepares to go to Beijing )""",
        )

        val issues = mutableListOf<String>()
        for (level in 1..6) {
            val candidates = listOf(
                File("src/main/assets/catalog/hsk$level.json"),
                File("app/src/main/assets/catalog/hsk$level.json"),
            )
            val jsonFile = candidates.firstOrNull { it.exists() }
                ?: error("Missing catalog/hsk$level.json")
            val catalog = json.decodeFromString<CatalogDto>(jsonFile.readText())

            for (word in catalog.words) {
                if (word.pinyin.any { it.isUpperCase() } && word.hanzi !in allowedProperNouns) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): uppercase pinyin '${word.pinyin}'"
                }
                if (surnameRegex.containsMatchIn(word.english) && word.hanzi != "姓") {
                    issues += "HSK$level ${word.id} (${word.hanzi}): surname translation '${word.english}'"
                }
                if (vulgarOrSlangRegex.containsMatchIn(word.english)) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): vulgar/slang translation '${word.english}'"
                }
                if (vulgarOrSlangRegex.containsMatchIn(word.example.english)) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): vulgar/slang example '${word.example.english}'"
                }
                if (level in 1..4 && mechanicalTemplateRegex.containsMatchIn(word.example.english)) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): mechanical template '${word.example.english}'"
                }
                if (word.hanzi !in word.example.tokens) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): word missing from example.tokens"
                }
                val nWordPy = normPy(word.pinyin)
                val nExPy = normPy(word.example.pinyin)
                if (nWordPy.isNotEmpty() && nWordPy !in nExPy) {
                    val sandhiOk = word.hanzi in sandhiOrParticleWords &&
                        stripTones(nWordPy) in stripTones(nExPy)
                    if (!sandhiOk) {
                        issues += "HSK$level ${word.id} (${word.hanzi}): pinyin '${word.pinyin}' missing from '${word.example.pinyin}'"
                    }
                }
            }
        }

        assertTrue(
            "Pinyin/polyphone or English issues (${issues.size}):\n" +
                issues.take(25).joinToString("\n"),
            issues.isEmpty(),
        )
    }
}
