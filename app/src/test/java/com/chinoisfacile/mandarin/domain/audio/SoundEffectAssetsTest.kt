package com.chinoisfacile.mandarin.domain.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundEffectAssetsTest {

    @Test
    fun `raw wav assets exist with valid PCM 44_1kHz headers, expected durations, and ~85 percent peak`() {
        val expectedDurationsMs = mapOf(
            "sfx_correct.wav" to 200,
            "sfx_wrong.wav" to 220,
            "sfx_complete.wav" to 380,
        )

        expectedDurationsMs.forEach { (filename, expectedMs) ->
            val file = listOf(
                File("src/main/res/raw/$filename"),
                File("app/src/main/res/raw/$filename"),
            ).firstOrNull { it.exists() }
                ?: error("Fichier res/raw/$filename introuvable")

            val bytes = file.readBytes()
            assertTrue("En-tête WAV trop court pour $filename", bytes.size > 44)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))

            val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            val audioFormat = header.getShort(20).toInt()
            val channels = header.getShort(22).toInt()
            val sampleRate = header.getInt(24)
            val bitsPerSample = header.getShort(34).toInt()
            val dataSize = header.getInt(40)

            assertEquals("Format PCM attendu pour $filename", 1, audioFormat)
            assertEquals("Mono attendu pour $filename", 1, channels)
            assertEquals("44.1 kHz attendu pour $filename", 44_100, sampleRate)
            assertEquals("16-bit attendu pour $filename", 16, bitsPerSample)

            val sampleCount = dataSize / 2
            val expectedSamples = (expectedMs * 44_100) / 1000
            assertEquals("Durée inattendue pour $filename", expectedSamples, sampleCount)

            val pcm = ByteBuffer.wrap(bytes, 44, dataSize).order(ByteOrder.LITTLE_ENDIAN)
            var peak = 0
            var lastSample = 0
            repeat(sampleCount) {
                val sample = pcm.short.toInt()
                peak = maxOf(peak, abs(sample))
                lastSample = sample
            }
            assertTrue("Pic hors plage (~85%) pour $filename : $peak", peak in 26_000..29_000)
            assertTrue("Fade-out final attendu pour $filename : $lastSample", abs(lastSample) < 50)
        }
    }
}
