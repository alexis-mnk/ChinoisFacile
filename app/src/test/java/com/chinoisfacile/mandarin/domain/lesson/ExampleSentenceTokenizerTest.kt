package com.chinoisfacile.mandarin.domain.lesson

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleSentenceTokenizerTest {

    @Test
    fun `classifies target, clickable known words, and plain unknown tokens cleanly`() {
        val sentence = "我喜欢喝茶。"
        val segments = ExampleSentenceTokenizer.tokenize(
            exampleHanzi = sentence,
            exampleTokens = listOf("我", "喜欢", "喝", "茶"),
            targetHanzi = "茶",
            knownTokenKeys = setOf("我", "喜欢"),
        )

        assertEquals(4, segments.size)
        assertEquals(
            ExampleSentenceSegment(text = "我", kind = ExampleSegmentKind.CLICKABLE),
            segments[0],
        )
        assertEquals(
            ExampleSentenceSegment(text = "喜欢", kind = ExampleSegmentKind.CLICKABLE),
            segments[1],
        )
        assertEquals(
            ExampleSentenceSegment(text = "喝", kind = ExampleSegmentKind.PLAIN),
            segments[2],
        )
        assertEquals(
            ExampleSentenceSegment(
                text = "茶",
                trailingPunctuation = "。",
                kind = ExampleSegmentKind.TARGET,
            ),
            segments[3],
        )
        assertEquals(
            sentence,
            segments.joinToString("") { it.leadingPunctuation + it.text + it.trailingPunctuation },
        )
    }

    @Test
    fun `attaches internal trailing and leading punctuation without creating orphan segments`() {
        val sentence = "你好，“朋友”，再见！"
        val segments = ExampleSentenceTokenizer.tokenize(
            exampleHanzi = sentence,
            exampleTokens = listOf("你好", "朋友", "再见"),
            targetHanzi = "朋友",
            knownTokenKeys = setOf("你好", "再见"),
        )

        assertEquals(3, segments.size)
        assertEquals(
            ExampleSentenceSegment(
                text = "你好",
                trailingPunctuation = "，",
                kind = ExampleSegmentKind.CLICKABLE,
            ),
            segments[0],
        )
        assertEquals(
            ExampleSentenceSegment(
                text = "朋友",
                leadingPunctuation = "“",
                trailingPunctuation = "”，",
                kind = ExampleSegmentKind.TARGET,
            ),
            segments[1],
        )
        assertEquals(
            ExampleSentenceSegment(
                text = "再见",
                trailingPunctuation = "！",
                kind = ExampleSegmentKind.CLICKABLE,
            ),
            segments[2],
        )
        assertEquals(
            sentence,
            segments.joinToString("") { it.leadingPunctuation + it.text + it.trailingPunctuation },
        )
    }

    @Test
    fun `falls back gracefully when tokens are empty or mismatch sentence while keeping target highlighted`() {
        val emptyTokensResult = ExampleSentenceTokenizer.tokenize(
            exampleHanzi = "你好吗？",
            exampleTokens = emptyList(),
            targetHanzi = "吗",
            knownTokenKeys = setOf("你好"),
        )
        assertEquals(
            listOf(
                ExampleSentenceSegment(text = "你好", kind = ExampleSegmentKind.PLAIN),
                ExampleSentenceSegment(text = "吗", trailingPunctuation = "？", kind = ExampleSegmentKind.TARGET),
            ),
            emptyTokensResult,
        )

        val mismatchResult = ExampleSentenceTokenizer.tokenize(
            exampleHanzi = "我们去北京。",
            exampleTokens = listOf("我", "去", "上海"),
            targetHanzi = "北京",
            knownTokenKeys = setOf("我", "去"),
        )
        assertEquals(
            listOf(
                ExampleSentenceSegment(text = "我们去", kind = ExampleSegmentKind.PLAIN),
                ExampleSentenceSegment(text = "北京", trailingPunctuation = "。", kind = ExampleSegmentKind.TARGET),
            ),
            mismatchResult,
        )
    }
}
