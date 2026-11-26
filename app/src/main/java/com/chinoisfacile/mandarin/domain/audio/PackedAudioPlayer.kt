package com.chinoisfacile.mandarin.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PackLoadState {
    LOADING,
    HAS_CLIPS,
    EMPTY,
}

/**
 * Lecteur hors-ligne de clips Opus (.ogg) embarqués dans les archives .pack sous assets/audio.
 * Toutes les opérations d'indexation et de MediaPlayer tournent sur un exécuteur mono-thread.
 */
class PackedAudioPlayer(context: Context) {

    private val appContext = context.applicationContext

    private val _loadState = MutableStateFlow(PackLoadState.LOADING)
    val loadState: StateFlow<PackLoadState> = _loadState.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "audio-pack-player").apply { isDaemon = true }
    }
    private val playGeneration = AtomicLong(0L)

    @Volatile
    private var released = false

    // Accessed exclusively from executor
    private var indexLoaded = false
    private var index: AudioPackIndex = AudioPackIndex.EMPTY
    private var mediaPlayer: MediaPlayer? = null

    init {
        executor.execute {
            ensureIndexLoaded()
        }
    }

    fun playOrFallback(
        text: String,
        speed: Float,
        onBeforePlayPacked: () -> Unit,
        onFallback: () -> Unit,
    ) {
        if (released || text.isBlank()) return
        val generation = playGeneration.incrementAndGet()
        runCatching {
            executor.execute {
                if (released || generation != playGeneration.get()) return@execute
                ensureIndexLoaded()
                if (released || generation != playGeneration.get()) return@execute

                val clip = index.lookup(text)
                if (clip == null) {
                    stopMediaPlayerInternal()
                    onFallback()
                    return@execute
                }

                onBeforePlayPacked()
                val played = playClipInternal(clip = clip, speed = speed, generation = generation)
                if (!played && !released && generation == playGeneration.get()) {
                    onFallback()
                }
            }
        }
    }

    fun stop() {
        playGeneration.incrementAndGet()
        if (released) return
        runCatching {
            executor.execute {
                stopMediaPlayerInternal()
            }
        }
    }

    fun release() {
        released = true
        playGeneration.incrementAndGet()
        runCatching {
            executor.execute {
                releaseMediaPlayerInternal()
            }
            executor.shutdown()
        }
    }

    private fun ensureIndexLoaded() {
        if (indexLoaded || released) return
        val fileNames = runCatching {
            appContext.assets.list(AUDIO_ASSETS_DIR)
        }.getOrNull()
            .orEmpty()
            .filter { it.endsWith(".pack", ignoreCase = true) }
            .sorted()

        val parsedPacks = ArrayList<Pair<String, List<RawPackEntry>>>(fileNames.size)
        for (fileName in fileNames) {
            if (released) return
            val assetPath = "$AUDIO_ASSETS_DIR/$fileName"
            val entries = runCatching {
                appContext.assets.open(assetPath).buffered().use { stream ->
                    AudioPackCodec.readIndex(stream)
                }
            }.getOrNull()
            if (!entries.isNullOrEmpty()) {
                parsedPacks += assetPath to entries
            }
        }

        index = AudioPackIndex.fromPacks(parsedPacks)
        indexLoaded = true
        _loadState.value = if (index.isEmpty) PackLoadState.EMPTY else PackLoadState.HAS_CLIPS
    }

    private fun playClipInternal(
        clip: PackedClipRef,
        speed: Float,
        generation: Long,
    ): Boolean {
        return try {
            val player = mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
            player.reset()
            player.setAudioAttributes(SPEECH_AUDIO_ATTRIBUTES)
            appContext.assets.openFd(clip.assetPath).use { afd ->
                player.setDataSource(
                    afd.fileDescriptor,
                    afd.startOffset + clip.offset,
                    clip.length.toLong(),
                )
            }
            player.prepare()
            if (released || generation != playGeneration.get()) {
                runCatching { player.reset() }
                return true
            }
            if (speed != 1.0f) {
                runCatching {
                    val params = player.playbackParams
                    params.speed = speed
                    params.pitch = 1.0f
                    player.playbackParams = params
                }
            }
            if (!player.isPlaying) {
                player.start()
            }
            true
        } catch (_: Exception) {
            releaseMediaPlayerInternal()
            false
        }
    }

    private fun stopMediaPlayerInternal() {
        val player = mediaPlayer ?: return
        runCatching {
            if (player.isPlaying) {
                player.stop()
            }
        }
        runCatching {
            player.reset()
        }.onFailure {
            releaseMediaPlayerInternal()
        }
    }

    private fun releaseMediaPlayerInternal() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching { player.release() }
    }

    private companion object {
        const val AUDIO_ASSETS_DIR = "audio"

        val SPEECH_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
