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
    fun `all hsk1 to hsk6 catalog json files parse cleanly with unique ids and hanzi`() {
        val json = Json { ignoreUnknownKeys = true }
        val toneRegex = Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
        val neutralParticles = setOf(
            "ma", "ne", "de", "le", "ba", "zhe", "guo", "a", "ya", "wa", "na", "la", "o", "en",
        )
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
                ?: error("Fichier catalog/hsk$level.json introuvable depuis ${File(".").absolutePath}")

            val catalog = json.decodeFromString<CatalogDto>(jsonFile.readText())
            assertEquals(level, catalog.hskLevel)
            assertTrue(catalog.catalogVersion >= 1)
            assertTrue("Au moins 3 unités attendues pour HSK $level", catalog.units.size >= 3)

            val levelLessonIds = mutableSetOf<String>()
            val lessonOrders = mutableListOf<Int>()
            catalog.units.forEach { unit ->
                assertTrue("Unit ID dupliqué ${unit.id}", globalUnitIds.add(unit.id))
                unit.lessons.forEach { lesson ->
                    assertTrue("Lesson ID dupliqué ${lesson.id}", globalLessonIds.add(lesson.id))
                    levelLessonIds += lesson.id
                    lessonOrders += lesson.orderIndex
                }
            }

            assertTrue("Au moins 6 leçons attendues pour HSK $level", levelLessonIds.size >= 6)
            assertEquals((1..levelLessonIds.size).toList(), lessonOrders.sorted())
            assertTrue("Moins de 30 mots pour HSK $level", catalog.words.size >= 30)

            assertTrue(
                "catalogVersion insuffisant pour HSK $level : ${catalog.catalogVersion}",
                catalog.catalogVersion >= 5,
            )
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
                    "Attendu >= $minExpectedWords mots pour HSK $level, trouvé ${catalog.words.size}",
                    catalog.words.size >= minExpectedWords,
                )
            }

            catalog.words.forEach { word ->
                assertTrue("Word ID mal préfixé ${word.id}", word.id.startsWith("hsk${level}_"))
                assertTrue("Word ID dupliqué ${word.id}", globalWordIds.add(word.id))
                assertTrue("Hanzi dupliqué ${word.hanzi} (${word.id})", globalHanzi.add(word.hanzi))
                assertTrue("Hanzi vide pour ${word.id}", word.hanzi.isNotBlank())
                assertTrue("Pinyin vide pour ${word.id}", word.pinyin.isNotBlank())
                val hasTone = toneRegex.containsMatchIn(word.pinyin)
                val isNeutralParticle = word.pinyin.trim().lowercase() in neutralParticles
                assertTrue(
                    "Marque de ton manquante pour ${word.id} (${word.pinyin})",
                    hasTone || isNeutralParticle,
                )
                assertTrue("Traduction vide pour ${word.id}", word.french.isNotBlank())
                assertTrue("lessonId inconnu pour ${word.id}", word.lessonId in levelLessonIds)
                assertTrue("Tokens insuffisants pour ${word.id}", word.example.tokens.size >= 3)
                assertEquals(
                    "Tokens dupliqués dans la même phrase pour ${word.id}",
                    word.example.tokens.size,
                    word.example.tokens.distinct().size,
                )

                val expectedJoined = word.example.hanzi.trimEnd('。', '？', '！', '，', '、', '；', '：')
                assertEquals(
                    "Tokens incohérents pour ${word.id}",
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
                ?: error("Fichier catalog/hsk$level.json introuvable depuis ${File(".").absolutePath}")
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
                        violations += "HSK$level ${word.id} (${word.hanzi}): token '$token' vient de HSK$levelOfToken"
                    } else if (token !in allowedVocab && !digitsRegex.matches(token)) {
                        violations += "HSK$level ${word.id} (${word.hanzi}): token hors-catalogue '$token'"
                    }
                }
            }
        }

        assertTrue(
            "Violations du plafond de vocabulaire (${violations.size}) :\n" +
                violations.take(20).joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `all hsk1 to hsk6 words have valid non-surname pinyin french and matching example pinyin`() {
        val json = Json { ignoreUnknownKeys = true }
        val allowedProperNouns = setOf(
            "中国", "北京", "汉语", "中文", "黄河", "亚洲", "京剧", "长城", "长江",
            "元旦", "华裔", "国庆节", "礼拜天", "除夕", "互联网", "元宵节", "华侨",
            "国务院", "摄氏度", "欧洲", "正月", "端午节", "重阳节",
        )
        val surnameRegex = Regex("nom de famille|patronyme", RegexOption.IGNORE_CASE)
        val vulgarOrSlangRegex = Regex(
            """(\(argot\)|\(vulg\.?\)|\(fam\.?\)|radical\)|\bputain\b|\bmerde\b|\bbordel\b|\bsalope\b|\bconnard\w*\b|\bconnasse\b|\bencul\w*\b|\bnichons?\b|\bbranl\w*\b|\bculotte\b|\bporn\w*\b|\bpénis\b|\bvagin\b|\bcouille\w*\b|\bchatte\b|\bbite\b|\bchier\b|\bchiant\w*\b|\bfoutre\b|\bfous\b|\bfout\b|\bgueule\b|\bpisse\w*\b|\bcaca\b|\bpipi\b|\bsapes\b)""",
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

        val issues = mutableListOf<String>()
        for (level in 1..6) {
            val candidates = listOf(
                File("src/main/assets/catalog/hsk$level.json"),
                File("app/src/main/assets/catalog/hsk$level.json"),
            )
            val jsonFile = candidates.firstOrNull { it.exists() }
                ?: error("Fichier catalog/hsk$level.json introuvable")
            val catalog = json.decodeFromString<CatalogDto>(jsonFile.readText())

            for (word in catalog.words) {
                if (word.pinyin.any { it.isUpperCase() } && word.hanzi !in allowedProperNouns) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): pinyin avec majuscule '${word.pinyin}'"
                }
                if (surnameRegex.containsMatchIn(word.french) && word.hanzi != "姓") {
                    issues += "HSK$level ${word.id} (${word.hanzi}): traduction nom de famille '${word.french}'"
                }
                if (vulgarOrSlangRegex.containsMatchIn(word.french)) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): traduction vulgaire/argot '${word.french}'"
                }
                if (vulgarOrSlangRegex.containsMatchIn(word.example.french)) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): exemple vulgaire/argot '${word.example.french}'"
                }
                if (level in 1..2 && word.example.french.contains(" : ")) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): gabarit mécanique '${word.example.french}'"
                }
                if (word.hanzi !in word.example.tokens) {
                    issues += "HSK$level ${word.id} (${word.hanzi}): mot absent de example.tokens"
                }
                val nWordPy = normPy(word.pinyin)
                val nExPy = normPy(word.example.pinyin)
                if (nWordPy.isNotEmpty() && nWordPy !in nExPy) {
                    val sandhiOk = word.hanzi in sandhiOrParticleWords &&
                        stripTones(nWordPy) in stripTones(nExPy)
                    if (!sandhiOk) {
                        issues += "HSK$level ${word.id} (${word.hanzi}): pinyin '${word.pinyin}' absent de '${word.example.pinyin}'"
                    }
                }
            }
        }

        assertTrue(
            "Anomalies pinyin/polyphones détectées (${issues.size}) :\n" +
                issues.take(25).joinToString("\n"),
            issues.isEmpty(),
        )
    }
}
