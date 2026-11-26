package com.chinoisfacile.mandarin.domain.pinyin

import androidx.compose.runtime.Immutable

@Immutable
data class PinyinSegment(
    val text: String,
    val tone: Int?,
    val isSyllable: Boolean,
)

object PinyinToneParser {

    private val TONE_BY_CHAR: Map<Char, Int> = buildMap {
        "āēīōūǖĀĒĪŌŪǕ".forEach { put(it, 1) }
        "áéíóúǘÁÉÍÓÚǗ".forEach { put(it, 2) }
        "ǎěǐǒǔǚǍĚǏǑǓǙ".forEach { put(it, 3) }
        "àèìòùǜÀÈÌÒÙǛ".forEach { put(it, 4) }
    }

    private val PLAIN_BY_CHAR: Map<Char, Char> = buildMap {
        for (c in "āáǎàĀÁǍÀ") put(c, 'a')
        for (c in "ēéěèĒÉĚÈ") put(c, 'e')
        for (c in "īíǐìĪÍǏÌ") put(c, 'i')
        for (c in "ōóǒòŌÓǑÒ") put(c, 'o')
        for (c in "ūúǔùŪÚǓÙ") put(c, 'u')
        for (c in "ǖǘǚǜüǕǗǙǛÜ") put(c, 'v')
    }

    private val ZERO_INITIAL_SYLLABLES: Set<String> = setOf(
        "a", "o", "e", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
    )

    private val STANDALONE_ONLY_SYLLABLES: Set<String> = setOf(
        "m", "n", "ng", "hm", "hng", "r",
    )

    private val STANDARD_SYLLABLES: Set<String> = setOf(
        // Zero-initial
        "a", "o", "e", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
        // b
        "ba", "bo", "bai", "bei", "bao", "ban", "ben", "bang", "beng", "bi", "bie", "biao", "bian", "bin", "bing", "bu",
        // p
        "pa", "po", "pai", "pei", "pao", "pou", "pan", "pen", "pang", "peng", "pi", "pie", "piao", "pian", "pin", "ping", "pu",
        // m
        "ma", "mo", "me", "mai", "mei", "mao", "mou", "man", "men", "mang", "meng", "mi", "mie", "miao", "miu", "mian", "min", "ming", "mu",
        // f
        "fa", "fo", "fei", "fou", "fan", "fen", "fang", "feng", "fu",
        // d
        "da", "de", "dai", "dei", "dao", "dou", "dan", "den", "dang", "deng", "dong", "di", "dia", "die", "diao", "diu", "dian", "ding", "du", "duo", "dui", "duan", "dun",
        // t
        "ta", "te", "tai", "tei", "tao", "tou", "tan", "tang", "teng", "tong", "ti", "tie", "tiao", "tian", "ting", "tu", "tuo", "tui", "tuan", "tun",
        // n
        "na", "ne", "nai", "nei", "nao", "nou", "nan", "nen", "nang", "neng", "nong", "ni", "nie", "niao", "niu", "nian", "nin", "niang", "ning", "nu", "nuo", "nuan", "nun", "nv", "nve",
        // l
        "la", "lo", "le", "lai", "lei", "lao", "lou", "lan", "lang", "leng", "long", "li", "lia", "lie", "liao", "liu", "lian", "lin", "liang", "ling", "lu", "luo", "luan", "lun", "lv", "lve",
        // g
        "ga", "ge", "gai", "gei", "gao", "gou", "gan", "gen", "gang", "geng", "gong", "gu", "gua", "guo", "guai", "gui", "guan", "gun", "guang",
        // k
        "ka", "ke", "kai", "kei", "kao", "kou", "kan", "ken", "kang", "keng", "kong", "ku", "kua", "kuo", "kuai", "kui", "kuan", "kun", "kuang",
        // h
        "ha", "he", "hai", "hei", "hao", "hou", "han", "hen", "hang", "heng", "hong", "hu", "hua", "huo", "huai", "hui", "huan", "hun", "huang",
        // j
        "ji", "jia", "jie", "jiao", "jiu", "jian", "jin", "jiang", "jing", "jiong", "ju", "jue", "juan", "jun",
        // q
        "qi", "qia", "qie", "qiao", "qiu", "qian", "qin", "qiang", "qing", "qiong", "qu", "que", "quan", "qun",
        // x
        "xi", "xia", "xie", "xiao", "xiu", "xiang", "xian", "xin", "xing", "xiong", "xu", "xue", "xuan", "xun",
        // zh
        "zha", "zhe", "zhi", "zhai", "zhei", "zhao", "zhou", "zhan", "zhen", "zhang", "zheng", "zhong", "zhu", "zhua", "zhuo", "zhuai", "zhui", "zhuan", "zhun", "zhuang",
        // ch
        "cha", "che", "chi", "chai", "chao", "chou", "chan", "chen", "chang", "cheng", "chong", "chu", "chua", "chuo", "chuai", "chui", "chuan", "chun", "chuang",
        // sh
        "sha", "she", "shi", "shai", "shei", "shao", "shou", "shan", "shen", "shang", "sheng", "shu", "shua", "shuo", "shuai", "shui", "shuan", "shun", "shuang",
        // r
        "re", "ri", "rao", "rou", "ran", "ren", "rang", "reng", "rong", "ru", "rua", "ruo", "rui", "ruan", "run",
        // z
        "za", "ze", "zi", "zai", "zei", "zao", "zou", "zan", "zen", "zang", "zeng", "zong", "zu", "zuo", "zui", "zuan", "zun",
        // c
        "ca", "ce", "ci", "cai", "cao", "cou", "can", "cen", "cang", "ceng", "cong", "cu", "cuo", "cui", "cuan", "cun",
        // s
        "sa", "se", "si", "sai", "sao", "sou", "san", "sen", "sang", "seng", "song", "su", "suo", "sui", "suan", "sun",
        // y
        "ya", "yo", "ye", "yao", "you", "yan", "yin", "yang", "ying", "yong", "yi", "yu", "yue", "yuan", "yun",
        // w
        "wa", "wo", "wai", "wei", "wan", "wen", "wang", "weng", "wu",
    )

    fun parse(pinyin: String): List<PinyinSegment> {
        if (pinyin.isEmpty()) return emptyList()

        val result = mutableListOf<PinyinSegment>()
        var i = 0
        val n = pinyin.length

        while (i < n) {
            if (!isPinyinLetter(pinyin[i])) {
                val start = i
                while (i < n && !isPinyinLetter(pinyin[i])) {
                    i += 1
                }
                result += PinyinSegment(
                    text = pinyin.substring(start, i),
                    tone = null,
                    isSyllable = false,
                )
            } else {
                val start = i
                while (i < n) {
                    val ch = pinyin[i]
                    if (isPinyinLetter(ch)) {
                        if ((ch == 'u' || ch == 'U') && i + 1 < n && pinyin[i + 1] == ':') {
                            i += 2
                        } else {
                            i += 1
                        }
                    } else {
                        break
                    }
                }
                val wordRun = pinyin.substring(start, i)
                result += splitWordRun(wordRun)
            }
        }

        return result
    }

    fun detectTone(syllable: String): Int {
        for (ch in syllable) {
            TONE_BY_CHAR[ch]?.let { return it }
        }
        return 5
    }

    fun alignHanziTones(hanzi: String, pinyin: String): List<Int>? {
        if (hanzi.isEmpty()) return null
        for (ch in hanzi) {
            if (!isCjkIdeograph(ch)) return null
        }
        val syllables = parse(pinyin).filter { it.isSyllable }
        if (syllables.size != hanzi.length) return null
        return syllables.map { it.tone ?: 5 }
    }

    private fun splitWordRun(run: String): List<PinyinSegment> {
        val logicalChars = buildLogicalChars(run)
        val n = logicalChars.size
        if (n == 0) {
            return listOf(PinyinSegment(text = run, tone = null, isSyllable = false))
        }

        val fullPlain = buildPlainString(logicalChars, 0, n)
        if (fullPlain in STANDALONE_ONLY_SYLLABLES) {
            return listOf(
                PinyinSegment(
                    text = run,
                    tone = detectTone(run),
                    isSyllable = true,
                ),
            )
        }

        val dp = arrayOfNulls<DpState>(n + 1)
        dp[0] = DpState(
            prevIndex = -1,
            syllableCount = 0,
            interiorZeroInitialCount = 0,
        )

        for (i in 0 until n) {
            val state = dp[i] ?: continue
            val maxEnd = minOf(n, i + 6)
            for (j in maxEnd downTo i + 1) {
                if (countToneMarks(logicalChars, i, j) > 1) continue

                val plain = buildPlainString(logicalChars, i, j)
                val isValid = plain in STANDARD_SYLLABLES ||
                    (i > 0 && j == n && plain == "r")
                if (!isValid) continue

                val nextCount = state.syllableCount + 1
                val isInteriorZero = i > 0 && plain in ZERO_INITIAL_SYLLABLES
                val nextZero = state.interiorZeroInitialCount + if (isInteriorZero) 1 else 0

                val existing = dp[j]
                val better = existing == null ||
                    nextCount < existing.syllableCount ||
                    (nextCount == existing.syllableCount && nextZero < existing.interiorZeroInitialCount)
                if (better) {
                    dp[j] = DpState(
                        prevIndex = i,
                        syllableCount = nextCount,
                        interiorZeroInitialCount = nextZero,
                    )
                }
            }
        }

        val finalState = dp[n]
        if (finalState == null) {
            val hasToneMark = run.any { it in TONE_BY_CHAR }
            return listOf(
                PinyinSegment(
                    text = run,
                    tone = if (hasToneMark) detectTone(run) else null,
                    isSyllable = hasToneMark,
                ),
            )
        }

        val ranges = ArrayList<Pair<Int, Int>>(finalState.syllableCount)
        var cursor = n
        while (cursor > 0) {
            val prev = dp[cursor]?.prevIndex ?: break
            ranges += prev to cursor
            cursor = prev
        }
        ranges.reverse()

        return ranges.map { (startUnit, endUnit) ->
            val origStart = logicalChars[startUnit].origStart
            val origEnd = logicalChars[endUnit - 1].origEnd
            val syllableText = run.substring(origStart, origEnd)
            PinyinSegment(
                text = syllableText,
                tone = detectTone(syllableText),
                isSyllable = true,
            )
        }
    }

    private fun buildLogicalChars(run: String): List<LogicalChar> {
        val list = ArrayList<LogicalChar>(run.length)
        var i = 0
        while (i < run.length) {
            val ch = run[i]
            if ((ch == 'u' || ch == 'U') && i + 1 < run.length && run[i + 1] == ':') {
                list += LogicalChar(
                    plain = 'v',
                    hasToneMark = false,
                    origStart = i,
                    origEnd = i + 2,
                )
                i += 2
            } else {
                val plain = PLAIN_BY_CHAR[ch] ?: ch.lowercaseChar()
                list += LogicalChar(
                    plain = plain,
                    hasToneMark = ch in TONE_BY_CHAR,
                    origStart = i,
                    origEnd = i + 1,
                )
                i += 1
            }
        }
        return list
    }

    private fun buildPlainString(
        chars: List<LogicalChar>,
        start: Int,
        end: Int,
    ): String {
        val sb = StringBuilder(end - start)
        for (idx in start until end) {
            sb.append(chars[idx].plain)
        }
        return sb.toString()
    }

    private fun countToneMarks(
        chars: List<LogicalChar>,
        start: Int,
        end: Int,
    ): Int {
        var count = 0
        for (idx in start until end) {
            if (chars[idx].hasToneMark) count += 1
        }
        return count
    }

    private fun isPinyinLetter(ch: Char): Boolean {
        return ch in 'a'..'z' ||
            ch in 'A'..'Z' ||
            ch in PLAIN_BY_CHAR
    }

    private fun isCjkIdeograph(ch: Char): Boolean {
        return ch in '\u4E00'..'\u9FFF' || ch in '\u3400'..'\u4DBF'
    }

    private data class LogicalChar(
        val plain: Char,
        val hasToneMark: Boolean,
        val origStart: Int,
        val origEnd: Int,
    )

    private data class DpState(
        val prevIndex: Int,
        val syllableCount: Int,
        val interiorZeroInitialCount: Int,
    )
}
