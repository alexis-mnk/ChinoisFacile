package com.chinoisfacile.mandarin.domain.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TtsStatus {
    INITIALIZING,
    READY,
    LANGUAGE_MISSING,
    ERROR,
}

interface AudioPlayer {
    val status: StateFlow<TtsStatus>
    fun speakNormal(text: String)
    fun speakSlow(text: String)
    fun stop()
    fun release()
}

/**
 * Wrapper Android TextToSpeech configuré sur zh-CN (vitesse normale 1.0x / lente 0.7x).
 */
class TtsAudioPlayer(context: Context) : AudioPlayer {

    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    override val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { initResult ->
            if (initResult == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine == null) {
                    _status.value = TtsStatus.ERROR
                    return@TextToSpeech
                }
                val availability = engine.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
                if (availability == TextToSpeech.LANG_MISSING_DATA ||
                    availability == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    _status.value = TtsStatus.LANGUAGE_MISSING
                } else {
                    engine.language = Locale.SIMPLIFIED_CHINESE
                    _status.value = TtsStatus.READY
                }
            } else {
                _status.value = TtsStatus.ERROR
            }
        }
    }

    override fun speakNormal(text: String) {
        speakWithRate(text, NORMAL_RATE)
    }

    override fun speakSlow(text: String) {
        speakWithRate(text, SLOW_RATE)
    }

    private fun speakWithRate(text: String, rate: Float) {
        val engine = tts ?: return
        if (_status.value != TtsStatus.READY) return
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.nanoTime()}")
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val NORMAL_RATE = 1.0f
        const val SLOW_RATE = 0.7f
    }
}
