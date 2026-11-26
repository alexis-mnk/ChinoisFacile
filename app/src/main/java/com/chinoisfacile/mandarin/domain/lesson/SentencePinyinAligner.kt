package com.chinoisfacile.mandarin.domain.lesson

import java.text.Normalizer
import java.util.Locale

/**
 * Aligne chaque token de phrase avec sa transcription pinyin en minuscules
 * (sans révéler le premier mot par une majuscule).
 */
object SentencePinyinAligner {

    private val PINYIN_SEGMENT_REGEX = Regex(
        "[A-Za-zĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙǕǗǙǛāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüÜ0-9]+",
    )
    private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")

    private val INITIALS = listOf(
        "", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
        "j", "q", "x", "zh", "ch", "sh", "r", "z", "c", "s", "y", "w",
    )
    private val FINALS = listOf(
        "a", "o", "e", "i", "u", "v",
        "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "ong", "er",
        "ia", "ie", "iao", "iu", "ian", "in", "iang", "ing", "iong",
        "ua", "uo", "ue", "uai", "ui", "uan", "un", "uang", "ueng",
        "ve", "van", "vn",
    )
    private val VALID_SYLLABLES: Set<String> = buildSet {
        for (initial in INITIALS) {
            for (final in FINALS) {
                add(initial + final)
            }
        }
        add("n")
        add("ng")
        add("m")
        add("hm")
        add("hng")
        add("r")
    }

    fun alignTokens(
        tokens: List<String>,
        examplePinyin: String,
        fallbackPinyinByHanzi: Map<String, String> = emptyMap(),
    ): List<String> {
        if (tokens.isEmpty()) return emptyList()

        val normalizedPinyin = examplePinyin
            .replace("u:", "ü")
            .replace("U:", "Ü")
        val segments = PINYIN_SEGMENT_REGEX.findAll(normalizedPinyin)
            .map { it.value }
            .toList()

        val syllables = segments.flatMap { segment ->
            if (segment.all { it in '0'..'9' }) {
                listOf(segment)
            } else {
                splitPinyinSegment(segment)
            }
        }

        val unitCounts = tokens.map(::tokenUnitCount)
        if (syllables.size == unitCounts.sum()) {
            val aligned = ArrayList<String>(tokens.size)
            var offset = 0
            for (count in unitCounts) {
                val joined = syllables.subList(offset, offset + count)
                    .joinToString(" ")
                    .lowercase(Locale.ROOT)
                aligned += joined
                offset += count
            }
            return aligned
        }

        return tokens.map { token ->
            fallbackPinyinForToken(token, fallbackPinyinByHanzi)
        }
    }

    private fun fallbackPinyinForToken(
        token: String,
        fallbackPinyinByHanzi: Map<String, String>,
    ): String {
        fallbackPinyinByHanzi[token]?.let { return it.lowercase(Locale.ROOT) }
        if (token.length <= 1) return ""
        val parts = token.map { ch ->
            fallbackPinyinByHanzi[ch.toString()]?.lowercase(Locale.ROOT) ?: return ""
        }
        return parts.joinToString(" ")
    }

    private fun tokenUnitCount(token: String): Int {
        var units = 0
        var inAsciiDigits = false
        for (ch in token) {
            if (ch in '0'..'9') {
                if (!inAsciiDigits) {
                    units += 1
                    inAsciiDigits = true
                }
            } else {
                inAsciiDigits = false
                units += 1
            }
        }
        return units.coerceAtLeast(1)
    }

    private fun splitPinyinSegment(segment: String): List<String> {
        val plainChars = CharArray(segment.length)
        for (i in segment.indices) {
            plainChars[i] = stripToneToAscii(segment[i])
        }

        val n = segment.length
        val dp = arrayOfNulls<List<String>>(n + 1)
        dp[0] = emptyList()

        for (i in 0 until n) {
            val prefix = dp[i] ?: continue
            val maxEnd = minOf(n, i + 6)
            for (j in maxEnd downTo i + 1) {
                val candidatePlain = String(plainChars, i, j - i)
                if (candidatePlain in VALID_SYLLABLES) {
                    val candidate = prefix + segment.substring(i, j)
                    val existing = dp[j]
                    if (existing == null || candidate.size < existing.size) {
                        dp[j] = candidate
                    }
                }
            }
        }

        return dp[n] ?: listOf(segment)
    }

    private fun stripToneToAscii(ch: Char): Char {
        val normalized = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
        val base = COMBINING_MARKS_REGEX.replace(normalized, "").lowercase(Locale.ROOT)
        return base.firstOrNull() ?: ch.lowercaseChar()
    }
}
