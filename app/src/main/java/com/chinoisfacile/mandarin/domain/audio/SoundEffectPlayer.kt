package com.chinoisfacile.mandarin.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.chinoisfacile.mandarin.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface SoundEffects {
    fun playCorrect()
    fun playWrong()
    fun playSessionComplete()
    fun release()
}

/**
 * Lecteur d'effets sonores 100 % hors-ligne basé sur Android SoundPool
 * (USAGE_MEDIA + CONTENT_TYPE_SONIFICATION) pour suivre le volume média comme le TTS.
 */
class SoundEffectPlayer(context: Context) : SoundEffects {

    private val loadedSampleIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingSampleId = AtomicInteger(0)

    @Volatile
    private var released = false

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val correctSampleId: Int
    private val wrongSampleId: Int
    private val completeSampleId: Int

    init {
        soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
            if (released || status != 0) return@setOnLoadCompleteListener
            loadedSampleIds.add(sampleId)
            if (pendingSampleId.compareAndSet(sampleId, 0)) {
                runCatching {
                    pool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
        }

        val appContext = context.applicationContext
        correctSampleId = soundPool.load(appContext, R.raw.sfx_correct, 1)
        wrongSampleId = soundPool.load(appContext, R.raw.sfx_wrong, 1)
        completeSampleId = soundPool.load(appContext, R.raw.sfx_complete, 1)
    }

    override fun playCorrect() {
        playSample(correctSampleId)
    }

    override fun playWrong() {
        playSample(wrongSampleId)
    }

    override fun playSessionComplete() {
        playSample(completeSampleId)
    }

    private fun playSample(sampleId: Int) {
        if (released || sampleId == 0) return
        if (sampleId in loadedSampleIds) {
            runCatching {
                soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f)
            }
        } else {
            pendingSampleId.set(sampleId)
        }
    }

    override fun release() {
        released = true
        pendingSampleId.set(0)
        loadedSampleIds.clear()
        runCatching {
            soundPool.release()
        }
    }

    private companion object {
        const val MAX_STREAMS = 4
    }
}
