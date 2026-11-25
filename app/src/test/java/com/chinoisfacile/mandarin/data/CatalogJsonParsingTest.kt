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

            if (level == 1) {
                assertTrue(catalog.catalogVersion >= 3)
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
                assertTrue(catalog.catalogVersion >= 2)
                assertTrue(
                    "Attendu >= $minExpectedWords mots pour HSK $level, trouvé ${catalog.words.size}",
                    catalog.words.size >= minExpectedWords,
                )
            }

            catalog.words.forEach { word ->
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
}
