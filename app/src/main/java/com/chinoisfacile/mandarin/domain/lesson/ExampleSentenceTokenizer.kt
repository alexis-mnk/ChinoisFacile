package com.chinoisfacile.mandarin.domain.lesson

import androidx.compose.runtime.Immutable

enum class ExampleSegmentKind {
    TARGET,
    CLICKABLE,
    PLAIN,
}

@Immutable
data class ExampleSentenceSegment(
    val text: String,
    val leadingPunctuation: String = "",
    val trailingPunctuation: String = "",
    val kind: ExampleSegmentKind,
)

object ExampleSentenceTokenizer {

    private val TRAILING_PUNCTUATION: Set<Char> = setOf(
        '。', '？', '！', '，', '、', '；', '：', '”', '’', '）', '》', '】', '」', '』',
        '…', '—', '.', ',', '?', '!', ';', ':', ')', ']', '"', '\'', '·',
    )

    private val LEADING_PUNCTUATION: Set<Char> = setOf(
        '“', '‘', '（', '《', '【', '「', '『', '(', '[',
    )

    fun tokenize(
        exampleHanzi: String,
        exampleTokens: List<String>,
        targetHanzi: String,
        knownTokenKeys: Set<String>,
    ): List<ExampleSentenceSegment> {
        if (exampleHanzi.isBlank()) return emptyList()

        val cleanTokens = exampleTokens.filter { it.isNotBlank() }
        if (cleanTokens.isEmpty()) {
            return fallbackFromRawSentence(exampleHanzi, targetHanzi)
        }

        val segments = ArrayList<ExampleSentenceSegment>(cleanTokens.size)
        val pendingLeading = StringBuilder()
        var cursor = 0

        for (token in cleanTokens) {
            val idx = exampleHanzi.indexOf(token, startIndex = cursor)
            if (idx == -1) {
                return fallbackFromRawSentence(exampleHanzi, targetHanzi)
            }

            if (idx > cursor) {
                val gap = exampleHanzi.substring(cursor, idx)
                for (ch in gap) {
                    when {
                        ch.isWhitespace() -> Unit
                        ch in TRAILING_PUNCTUATION && segments.isNotEmpty() && pendingLeading.isEmpty() -> {
                            val lastIdx = segments.lastIndex
                            val last = segments[lastIdx]
                            segments[lastIdx] = last.copy(trailingPunctuation = last.trailingPunctuation + ch)
                        }
                        ch in LEADING_PUNCTUATION || ch in TRAILING_PUNCTUATION -> {
                            pendingLeading.append(ch)
                        }
                        else -> {
                            return fallbackFromRawSentence(exampleHanzi, targetHanzi)
                        }
                    }
                }
            }

            val kind = when {
                token == targetHanzi -> ExampleSegmentKind.TARGET
                token in knownTokenKeys -> ExampleSegmentKind.CLICKABLE
                else -> ExampleSegmentKind.PLAIN
            }
            segments += ExampleSentenceSegment(
                text = token,
                leadingPunctuation = pendingLeading.toString(),
                kind = kind,
            )
            pendingLeading.setLength(0)
            cursor = idx + token.length
        }

        if (cursor < exampleHanzi.length) {
            val tail = exampleHanzi.substring(cursor)
            for (ch in tail) {
                when {
                    ch.isWhitespace() -> Unit
                    ch in TRAILING_PUNCTUATION || ch in LEADING_PUNCTUATION -> {
                        if (segments.isNotEmpty()) {
                            val lastIdx = segments.lastIndex
                            val last = segments[lastIdx]
                            segments[lastIdx] = last.copy(trailingPunctuation = last.trailingPunctuation + ch)
                        }
                    }
                    else -> {
                        return fallbackFromRawSentence(exampleHanzi, targetHanzi)
                    }
                }
            }
        }

        return segments
    }

    private fun fallbackFromRawSentence(
        exampleHanzi: String,
        targetHanzi: String,
    ): List<ExampleSentenceSegment> {
        if (targetHanzi.isBlank() || !exampleHanzi.contains(targetHanzi)) {
            return listOf(
                ExampleSentenceSegment(
                    text = exampleHanzi,
                    kind = ExampleSegmentKind.PLAIN,
                ),
            )
        }

        val segments = mutableListOf<ExampleSentenceSegment>()
        var cursor = 0
        while (cursor < exampleHanzi.length) {
            val matchIdx = exampleHanzi.indexOf(targetHanzi, startIndex = cursor)
            if (matchIdx == -1) {
                val remainder = exampleHanzi.substring(cursor)
                if (remainder.isNotEmpty()) {
                    segments += ExampleSentenceSegment(
                        text = remainder,
                        kind = ExampleSegmentKind.PLAIN,
                    )
                }
                break
            }

            if (matchIdx > cursor) {
                segments += ExampleSentenceSegment(
                    text = exampleHanzi.substring(cursor, matchIdx),
                    kind = ExampleSegmentKind.PLAIN,
                )
            }

            var endAfterTarget = matchIdx + targetHanzi.length
            val trailing = StringBuilder()
            while (endAfterTarget < exampleHanzi.length && exampleHanzi[endAfterTarget] in TRAILING_PUNCTUATION) {
                trailing.append(exampleHanzi[endAfterTarget])
                endAfterTarget += 1
            }

            segments += ExampleSentenceSegment(
                text = targetHanzi,
                trailingPunctuation = trailing.toString(),
                kind = ExampleSegmentKind.TARGET,
            )
            cursor = endAfterTarget
        }

        return segments
    }
}
