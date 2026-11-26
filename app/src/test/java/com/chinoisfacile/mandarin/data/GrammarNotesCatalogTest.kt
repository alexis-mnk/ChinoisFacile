package com.chinoisfacile.mandarin.data

import com.chinoisfacile.mandarin.data.model.CatalogDto
import com.chinoisfacile.mandarin.data.model.GrammarNote
import com.chinoisfacile.mandarin.data.model.GrammarNotesCatalogDto
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.data.model.toDomain
import com.chinoisfacile.mandarin.domain.progression.StepLessonCardItem
import com.chinoisfacile.mandarin.domain.progression.StepLessonDeckBuilder
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarNotesCatalogTest {

    private fun resolveAssetFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/assets/$relativePath"),
            File("app/src/main/assets/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Missing asset $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun `grammar notes json parses cleanly with valid HSK1 to HSK3 trigger words, two complete examples and English prose`() {
        val json = Json { ignoreUnknownKeys = true }
        val frenchOnlyDiacritics = Regex("[êëçûôîœæÊËÇÛÔÎŒÆ]")
        val frenchProseWord = Regex("""\b(avec|pour|dans|utiliser|utilisé|exemple|mots|français|lequel|laquelle)\b""", RegexOption.IGNORE_CASE)
        val toneRegex = Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")

        val grammarFile = resolveAssetFile("catalog/grammar_notes.json")
        val grammarCatalog = json.decodeFromString<GrammarNotesCatalogDto>(grammarFile.readText())

        assertTrue(
            "Expected at least 45 grammar notes, found ${grammarCatalog.notes.size}",
            grammarCatalog.notes.size >= 45,
        )

        val wordsByLevel = (1..3).associateWith { level ->
            val catalog = json.decodeFromString<CatalogDto>(
                resolveAssetFile("catalog/hsk$level.json").readText(),
            )
            catalog.words.map { it.hanzi }.toSet()
        }

        val countByLevel = grammarCatalog.notes.groupingBy { it.hskLevel }.eachCount()
        assertTrue("Expected >= 15 HSK 1 notes", (countByLevel[1] ?: 0) >= 15)
        assertTrue("Expected >= 15 HSK 2 notes", (countByLevel[2] ?: 0) >= 15)
        assertTrue("Expected >= 12 HSK 3 notes", (countByLevel[3] ?: 0) >= 12)

        val seenIds = mutableSetOf<String>()
        val seenLevelTrigger = mutableSetOf<Pair<Int, String>>()

        grammarCatalog.notes.forEach { note ->
            assertTrue("Duplicate note id ${note.id}", seenIds.add(note.id))
            assertTrue("Invalid HSK level ${note.hskLevel} on ${note.id}", note.hskLevel in 1..3)
            assertTrue(
                "Duplicate (hskLevel, triggerWord) (${note.hskLevel}, ${note.triggerWord})",
                seenLevelTrigger.add(note.hskLevel to note.triggerWord),
            )
            val levelWords = wordsByLevel.getValue(note.hskLevel)
            assertTrue(
                "Trigger word '${note.triggerWord}' for ${note.id} not found in hsk${note.hskLevel}.json",
                note.triggerWord in levelWords,
            )

            assertTrue("Blank title for ${note.id}", note.title.isNotBlank())
            assertTrue("Blank pattern for ${note.id}", note.pattern.isNotBlank())
            assertTrue("Blank explanation for ${note.id}", note.explanation.isNotBlank())
            assertTrue("French character in title for ${note.id}", !frenchOnlyDiacritics.containsMatchIn(note.title))
            assertTrue("French character in pattern for ${note.id}", !frenchOnlyDiacritics.containsMatchIn(note.pattern))
            assertTrue(
                "French text in explanation for ${note.id}",
                !frenchOnlyDiacritics.containsMatchIn(note.explanation) && !frenchProseWord.containsMatchIn(note.explanation),
            )

            assertEquals(
                "Expected exactly 2 examples for ${note.id}",
                2,
                note.examples.size,
            )
            note.examples.forEachIndexed { idx, ex ->
                assertTrue("Empty chinese example #$idx for ${note.id}", ex.chinese.isNotBlank())
                assertTrue("Empty pinyin example #$idx for ${note.id}", ex.pinyin.isNotBlank())
                assertTrue("Empty english example #$idx for ${note.id}", ex.english.isNotBlank())
                assertTrue(
                    "Missing tone mark in pinyin '${ex.pinyin}' for ${note.id}",
                    toneRegex.containsMatchIn(ex.pinyin),
                )
                assertTrue(
                    "French text in english example #$idx for ${note.id}: ${ex.english}",
                    !frenchOnlyDiacritics.containsMatchIn(ex.english) && !frenchProseWord.containsMatchIn(ex.english),
                )
            }
        }
    }

    @Test
    fun `step lesson deck builder places grammar cards immediately after their trigger word without duplicates`() {
        val json = Json { ignoreUnknownKeys = true }
        val grammarCatalog = json.decodeFromString<GrammarNotesCatalogDto>(
            resolveAssetFile("catalog/grammar_notes.json").readText(),
        )
        val notesByTrigger: Map<String, GrammarNote> = grammarCatalog.notes
            .filter { it.hskLevel == 1 }
            .map { it.toDomain() }
            .associateBy { it.triggerWord }

        fun sampleWord(id: String, hanzi: String) = VocabWord(
            id = id,
            lessonId = "hsk1_u1_l3",
            hskLevel = 1,
            hanzi = hanzi,
            pinyin = "test",
            english = "test",
            exampleHanzi = hanzi,
            examplePinyin = "test",
            exampleEnglish = "test",
            exampleTokens = listOf(hanzi),
        )

        val wordsWithoutTriggers = listOf(
            sampleWord("w1", "你好"),
            sampleWord("w2", "谢谢"),
        )
        val plainDeck = StepLessonDeckBuilder.buildDeck(wordsWithoutTriggers, notesByTrigger)
        assertEquals(2, plainDeck.size)
        assertTrue(plainDeck.all { it is StepLessonCardItem.WordCard })

        val wordsWithTriggers = listOf(
            sampleWord("w1", "我"),
            sampleWord("w2", "是"),
            sampleWord("w3", "学生"),
            sampleWord("w4", "吗"),
            sampleWord("w5", "是"),
        )
        val mixedDeck = StepLessonDeckBuilder.buildDeck(wordsWithTriggers, notesByTrigger)
        assertEquals(
            listOf(
                "word_w1",
                "word_w2",
                "grammar_hsk1_shi",
                "word_w3",
                "word_w4",
                "grammar_hsk1_ma",
                "word_w5",
            ),
            mixedDeck.map { it.key },
        )
    }
}
