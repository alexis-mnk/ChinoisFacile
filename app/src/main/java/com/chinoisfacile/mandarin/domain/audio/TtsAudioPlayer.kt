package com.chinoisfacile.mandarin.domain.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var released = false

    init {
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { initResult ->
            val engine = tts
            if (released) {
                engine?.shutdown()
                return@TextToSpeech
            }
            if (initResult == TextToSpeech.SUCCESS) {
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
                    selectBestLocalChineseVoice(engine)
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
        if (released) return
        val engine = tts ?: return
        if (_status.value != TtsStatus.READY) return
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.nanoTime()}")
    }

    override fun stop() {
        if (released) return
        tts?.stop()
    }

    override fun release() {
        released = true
        val engine = tts
        tts = null
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
    }

    private fun selectBestLocalChineseVoice(engine: TextToSpeech) {
        runCatching {
            val bestVoice = engine.voices
                .orEmpty()
                .asSequence()
                .filter { voice ->
                    voice.locale.language.equals("zh", ignoreCase = true) &&
                        !voice.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
                }
                .maxWithOrNull(
                    compareBy<Voice> { isMainlandCnVoice(it) }
                        .thenBy { it.quality },
                )
            if (bestVoice != null) {
                engine.voice = bestVoice
            }
        }
    }

    private fun isMainlandCnVoice(voice: Voice): Boolean {
        return voice.locale.country.equals("CN", ignoreCase = true) ||
            voice.locale.script.equals("Hans", ignoreCase = true)
    }

    companion object {
        const val NORMAL_RATE = 1.0f
        const val SLOW_RATE = 0.7f
    }
}
