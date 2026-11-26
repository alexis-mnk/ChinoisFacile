package com.chinoisfacile.mandarin.domain.pinyin

import com.chinoisfacile.mandarin.data.model.CatalogDto
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinToneParserTest {

    @Test
    fun `detects tones 1 to 5 across lowercase and uppercase vowels including u umlaut`() {
        assertEquals(1, PinyinToneParser.detectTone("mā"))
        assertEquals(2, PinyinToneParser.detectTone("má"))
        assertEquals(3, PinyinToneParser.detectTone("mǎ"))
        assertEquals(4, PinyinToneParser.detectTone("mà"))
        assertEquals(5, PinyinToneParser.detectTone("ma"))

        assertEquals(1, PinyinToneParser.detectTone("Ā"))
        assertEquals(2, PinyinToneParser.detectTone("É"))
        assertEquals(3, PinyinToneParser.detectTone("Ǐ"))
        assertEquals(4, PinyinToneParser.detectTone("Ò"))
        assertEquals(1, PinyinToneParser.detectTone("jū"))
        assertEquals(1, PinyinToneParser.detectTone("Ǖ"))
        assertEquals(2, PinyinToneParser.detectTone("ǘ"))
        assertEquals(3, PinyinToneParser.detectTone("nǚ"))
        assertEquals(4, PinyinToneParser.detectTone("lǜ"))
    }

    @Test
    fun `splits spaced and glued multi-syllable words with Maximal Onset`() {
        val meiguo = PinyinToneParser.parse("Měiguó")
        assertEquals(listOf("Měi", "guó"), meiguo.map { it.text })
        assertEquals(listOf(3, 2), meiguo.map { it.tone })

        val tamen = PinyinToneParser.parse("Tāmen")
        assertEquals(listOf("Tā", "men"), tamen.map { it.text })
        assertEquals(listOf(1, 5), tamen.map { it.tone })

        val zhongguoren = PinyinToneParser.parse("Zhōngguórén")
        assertEquals(listOf("Zhōng", "guó", "rén"), zhongguoren.map { it.text })
        assertEquals(listOf(1, 2, 2), zhongguoren.map { it.tone })

        val keneng = PinyinToneParser.parse("kěnéng")
        assertEquals(listOf("kě", "néng"), keneng.map { it.text })
        assertEquals(listOf(3, 2), keneng.map { it.tone })

        val tizuqiu = PinyinToneParser.parse("tīzúqiú")
        assertEquals(listOf("tī", "zú", "qiú"), tizuqiu.map { it.text })
        assertEquals(listOf(1, 2, 2), tizuqiu.map { it.tone })

        val mingshengguji = PinyinToneParser.parse("míngshènggǔjì")
        assertEquals(listOf("míng", "shèng", "gǔ", "jì"), mingshengguji.map { it.text })
        assertEquals(listOf(2, 4, 3, 4), mingshengguji.map { it.tone })
    }

    @Test
    fun `handles u-colon notation, apostrophes, erhua, and sentence punctuation losslessly`() {
        val uColon = PinyinToneParser.parse("shěng lu:è")
        val uColonSyllables = uColon.filter { it.isSyllable }
        assertEquals(listOf("shěng", "lu:è"), uColonSyllables.map { it.text })
        assertEquals(listOf(3, 4), uColonSyllables.map { it.tone })
        assertEquals("shěng lu:è", uColon.joinToString("") { it.text })

        val apostrophe = PinyinToneParser.parse("kǎnkǎn'értán")
        assertEquals(listOf("kǎn", "kǎn", "ér", "tán"), apostrophe.filter { it.isSyllable }.map { it.text })
        assertEquals(listOf(3, 3, 2, 2), apostrophe.filter { it.isSyllable }.map { it.tone })
        assertEquals("kǎnkǎn'értán", apostrophe.joinToString("") { it.text })

        val erhuaSpaced = PinyinToneParser.parse("yī diǎn r")
        assertEquals(listOf("yī", "diǎn", "r"), erhuaSpaced.filter { it.isSyllable }.map { it.text })
        assertEquals(listOf(1, 3, 5), erhuaSpaced.filter { it.isSyllable }.map { it.tone })

        val erhuaGlued = PinyinToneParser.parse("yìdiǎnr")
        assertEquals(listOf("yì", "diǎn", "r"), erhuaGlued.map { it.text })
        assertEquals(listOf(4, 3, 5), erhuaGlued.map { it.tone })

        val singleEr = PinyinToneParser.parse("èr")
        assertEquals(listOf("èr"), singleEr.map { it.text })
        assertEquals(listOf(4), singleEr.map { it.tone })

        val sentence = "Nǐ shì Zhōngguórén ma?"
        val parsedSentence = PinyinToneParser.parse(sentence)
        assertEquals(sentence, parsedSentence.joinToString("") { it.text })
        assertEquals(
            listOf(3, 4, 1, 2, 2, 5),
            parsedSentence.filter { it.isSyllable }.map { it.tone },
        )
    }

    @Test
    fun `alignHanziTones matches CJK characters when syllable count equals length and returns null otherwise`() {
        assertEquals(listOf(3, 3), PinyinToneParser.alignHanziTones("你好", "nǐ hǎo"))
        assertEquals(listOf(3, 2), PinyinToneParser.alignHanziTones("美国", "Měiguó"))
        assertEquals(listOf(1, 3, 5), PinyinToneParser.alignHanziTones("一点儿", "yī diǎn r"))
        assertEquals(listOf(3, 4), PinyinToneParser.alignHanziTones("省略", "shěng lu:è"))
        assertEquals(listOf(3, 3, 2, 2), PinyinToneParser.alignHanziTones("侃侃而谈", "kǎnkǎn'értán"))
        assertEquals(listOf(1, 5), PinyinToneParser.alignHanziTones("花儿", "huār"))

        assertNull(PinyinToneParser.alignHanziTones("一点儿", "yī diǎn"))
        assertNull(PinyinToneParser.alignHanziTones("你好！", "nǐ hǎo!"))
        assertNull(PinyinToneParser.alignHanziTones("T恤", "T xù"))
        assertNull(PinyinToneParser.alignHanziTones("你好", "nǐ"))
        assertNull(PinyinToneParser.alignHanziTones("", "nǐ"))
    }

    @Test
    fun `round-trips all 4995 catalog words and examples without losing characters or syllables`() {
        val json = Json { ignoreUnknownKeys = true }
        for (level in 1..6) {
            val candidates = listOf(
                File("src/main/assets/catalog/hsk$level.json"),
                File("app/src/main/assets/catalog/hsk$level.json"),
            )
            val jsonFile = candidates.firstOrNull { it.exists() }
                ?: error("Missing catalog/hsk$level.json from ${File(".").absolutePath}")

            val catalog = json.decodeFromString<CatalogDto>(jsonFile.readText())
            for (word in catalog.words) {
                val parsedWord = PinyinToneParser.parse(word.pinyin)
                assertEquals(
                    "Round-trip failed for word ${word.id} pinyin=${word.pinyin}",
                    word.pinyin,
                    parsedWord.joinToString("") { it.text },
                )
                assertTrue(
                    "Expected aligned tones for CJK word ${word.hanzi} (${word.pinyin})",
                    PinyinToneParser.alignHanziTones(word.hanzi, word.pinyin) != null,
                )

                val parsedExample = PinyinToneParser.parse(word.example.pinyin)
                assertEquals(
                    "Round-trip failed for example ${word.id}",
                    word.example.pinyin,
                    parsedExample.joinToString("") { it.text },
                )
            }
        }
    }
}
