package com.chinoisfacile.mandarin.domain.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPackFormatTest {

    @Test
    fun `hashKey64 matches big-endian first 8 bytes of trimmed UTF-8 SHA-256`() {
        val text = "你好"
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        var expected = 0L
        for (i in 0 until 8) {
            expected = (expected shl 8) or (digest[i].toLong() and 0xFFL)
        }

        assertEquals(expected, AudioPackHash.hashKey64(text))
        assertEquals(expected, AudioPackHash.hashKey64("  $text\n "))
    }

    @Test
    fun `writePack and readIndex round-trip exact binary offsets and payloads`() {
        val sampleA = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 0x01, 0x02) // OggS...
        val sampleB = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 0x10, 0x20, 0x30)

        val out = ByteArrayOutputStream()
        AudioPackCodec.writePack(
            output = out,
            entries = mapOf(
                "你好" to sampleA,
                "今天天气很好。" to sampleB,
            ),
        )
        val archiveBytes = out.toByteArray()

        val rawEntries = AudioPackCodec.readIndex(ByteArrayInputStream(archiveBytes))
        assertNotNull(rawEntries)
        assertEquals(2, rawEntries!!.size)

        val index = AudioPackIndex.fromPacks(listOf("audio/hsk1.pack" to rawEntries))
        assertEquals(2, index.size)

        val refA = index.lookup("你好")
        assertNotNull(refA)
        assertEquals("audio/hsk1.pack", refA!!.assetPath)
        assertArrayEquals(
            sampleA,
            archiveBytes.copyOfRange(refA.offset.toInt(), refA.offset.toInt() + refA.length),
        )

        val refB = index.lookup(" 今天天气很好。 ")
        assertNotNull(refB)
        assertArrayEquals(
            sampleB,
            archiveBytes.copyOfRange(refB!!.offset.toInt(), refB.offset.toInt() + refB.length),
        )
        assertNull(index.lookup("不存在的词"))
    }

    @Test
    fun `multi-pack index resolves hits across packs and handles missing or empty packs gracefully`() {
        val pack1Bytes = ByteArrayOutputStream().also {
            AudioPackCodec.writePack(it, mapOf("猫" to byteArrayOf(1, 2, 3)))
        }.toByteArray()
        val pack2Bytes = ByteArrayOutputStream().also {
            AudioPackCodec.writePack(it, mapOf("我是学生。" to byteArrayOf(4, 5, 6, 7)))
        }.toByteArray()

        val entries1 = AudioPackCodec.readIndex(ByteArrayInputStream(pack1Bytes))!!
        val entries2 = AudioPackCodec.readIndex(ByteArrayInputStream(pack2Bytes))!!

        val multiIndex = AudioPackIndex.fromPacks(
            listOf(
                "audio/hsk1.pack" to entries1,
                "audio/grammar.pack" to entries2,
            ),
        )

        assertEquals(2, multiIndex.size)
        assertEquals("audio/hsk1.pack", multiIndex.lookup("猫")?.assetPath)
        assertEquals("audio/grammar.pack", multiIndex.lookup("我是学生。")?.assetPath)
        assertNull(multiIndex.lookup("狗"))

        assertTrue(AudioPackIndex.fromPacks(emptyList()).isEmpty)
        assertNull(AudioPackIndex.EMPTY.lookup("猫"))
    }

    @Test
    fun `readIndex returns null on invalid magic, unsupported version, or truncated stream`() {
        val valid = ByteArrayOutputStream().also {
            AudioPackCodec.writePack(it, mapOf("茶" to byteArrayOf(9, 8, 7)))
        }.toByteArray()

        val badMagic = valid.copyOf().apply { this[0] = 'X'.code.toByte() }
        assertNull(AudioPackCodec.readIndex(ByteArrayInputStream(badMagic)))

        val badVersion = valid.copyOf().apply {
            this[4] = 0
            this[5] = 99
        }
        assertNull(AudioPackCodec.readIndex(ByteArrayInputStream(badVersion)))

        val truncated = valid.copyOf(14)
        assertNull(AudioPackCodec.readIndex(ByteArrayInputStream(truncated)))
        assertNull(AudioPackCodec.readIndex(ByteArrayInputStream(ByteArray(0))))
    }
}
