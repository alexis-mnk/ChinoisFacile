package com.chinoisfacile.mandarin.data

import com.chinoisfacile.mandarin.data.model.CatalogDto
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonParsingTest {

    @Test
    fun `all hsk1 to hsk6 catalog json files parse cleanly with unique ids and hanzi`() {
        val json = Json { ignoreUnknownKeys = true }
        val toneRegex = Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
        val neutralParticles = setOf("ma", "ne", "de", "le", "ba", "zhe", "guo", "a", "ya", "wa", "na")
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
        val allowedSimpleProperNouns = setOf("中国", "北京", "上海", "小明", "小王", "小李", "法国", "巴黎")

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
}
