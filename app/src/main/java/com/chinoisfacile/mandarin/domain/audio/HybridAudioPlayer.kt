package com.chinoisfacile.mandarin.domain.audio

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Façade hybride : privilégie les clips Opus neuronaux embarqués (.pack) et bascule
 * automatiquement sur Android TTS si un clip n'est pas présent dans les packs chargés.
 */
class HybridAudioPlayer(context: Context) : AudioPlayer {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val packedPlayer = PackedAudioPlayer(appContext)
    private val ttsPlayer = TtsAudioPlayer(appContext)

    override val status: StateFlow<TtsStatus> = combine(
        packedPlayer.loadState,
        ttsPlayer.status,
    ) { packState, ttsStatus ->
        when (packState) {
            PackLoadState.HAS_CLIPS -> TtsStatus.READY
            PackLoadState.LOADING -> TtsStatus.INITIALIZING
            PackLoadState.EMPTY -> ttsStatus
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = TtsStatus.INITIALIZING,
    )

    override fun speakNormal(text: String) {
        packedPlayer.playOrFallback(
            text = text,
            speed = TtsAudioPlayer.NORMAL_RATE,
            onBeforePlayPacked = { ttsPlayer.stop() },
            onFallback = { ttsPlayer.speakNormal(text) },
        )
    }

    override fun speakSlow(text: String) {
        packedPlayer.playOrFallback(
            text = text,
            speed = TtsAudioPlayer.SLOW_RATE,
            onBeforePlayPacked = { ttsPlayer.stop() },
            onFallback = { ttsPlayer.speakSlow(text) },
        )
    }

    override fun stop() {
        packedPlayer.stop()
        ttsPlayer.stop()
    }

    override fun release() {
        scope.cancel()
        packedPlayer.release()
        ttsPlayer.release()
    }
}
