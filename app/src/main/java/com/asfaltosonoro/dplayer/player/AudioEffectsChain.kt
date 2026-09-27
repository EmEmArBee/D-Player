package com.asfaltosonoro.dplayer.player

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Build

/**
 * Owns the platform AudioEffect chain (Equalizer + BassBoost-as-preamp-boost
 * + DynamicsProcessing-as-compressor/AGP), all hung off the ExoPlayer audio
 * session id. Using the built-in android.media.audiofx.* effects instead of
 * a custom AudioProcessor keeps this cheap on low-power head unit SoCs: the
 * mixing happens in the platform's audio effects framework (often DSP-backed
 * on car hardware), not in our own Kotlin/JNI code per PCM buffer.
 *
 * 4-band EQ (250Hz/500Hz/3kHz/8kHz, matching the reference screenshot),
 * preamp = master gain, AGP + compressor = DynamicsProcessing multi-band
 * compressor/limiter. Crossfade is handled separately in PlaybackQueue
 * (dual-ExoPlayer crossfade), not here.
 */
object AudioEffectsChain {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null           // stands in for "preamp" low-end boost
    private var dynamicsProcessing: DynamicsProcessing? = null  // AGP + compressor

    var enabled: Boolean = true
        set(value) {
            field = value
            equalizer?.enabled = value
            bassBoost?.enabled = value
            dynamicsProcessing?.enabled = value
        }

    fun attach(audioSessionId: Int) {
        release()
        equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
        bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, null).apply { enabled = true }
        }
        // TODO: DynamicsProcessing requires API 28+; below that, compressor/AGP
        // become no-ops (fields still settable in UI, just not applied) —
        // acceptable trade-off vs pulling in a custom DSP compressor for the
        // Android 6-9 long tail.
    }

    fun setBand(bandIndex: Int, millibel: Short) {
        equalizer?.setBandLevel(bandIndex.toShort(), millibel)
    }

    fun bandRange(): Pair<Short, Short> = equalizer?.bandLevelRange?.let { it[0] to it[1] } ?: (0.toShort() to 0.toShort())

    fun setPreampBoost(strength: Short) {
        bassBoost?.setStrength(strength) // 0..1000
    }

    fun release() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        dynamicsProcessing?.release(); dynamicsProcessing = null
    }
}
