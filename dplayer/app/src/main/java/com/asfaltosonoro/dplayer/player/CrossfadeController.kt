package com.asfaltosonoro.dplayer.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.asfaltosonoro.dplayer.settings.PlayerPreferences

/**
 * Pragmatic crossfade: a single ExoPlayer can't truly overlap two decoders,
 * so instead of a dual-player deck-swap architecture (heavier, and awkward
 * to reconcile with a single MediaSession) this ramps volume down toward
 * the end of a track and back up right after the gapless auto-transition to
 * the next one. Audible result is a fade-out/fade-in across the boundary —
 * not a true overlap-crossfade, but a deliberate simplification to keep this
 * cheap on head unit hardware. Revisit with a dual-ExoPlayer approach if the
 * fade-only result isn't convincing enough in testing.
 */
class CrossfadeController(private val player: ExoPlayer, private val prefs: PlayerPreferences) {

    private val handler = Handler(Looper.getMainLooper())
    private var fadingIn = false

    private val positionWatcher = object : Runnable {
        override fun run() {
            if (prefs.crossfadeEnabled && !fadingIn && player.isPlaying) {
                val duration = player.duration
                if (duration > 0) {
                    val fadeMs = (prefs.crossfadeSeconds * 1000L).coerceAtLeast(500L)
                    val remaining = duration - player.currentPosition
                    player.volume = if (remaining in 0..fadeMs) {
                        (remaining.toFloat() / fadeMs).coerceIn(0.05f, 1f)
                    } else {
                        1f
                    }
                }
            }
            handler.postDelayed(this, 200)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (prefs.crossfadeEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                fadeIn()
            } else {
                player.volume = 1f
            }
        }
    }

    private fun fadeIn() {
        fadingIn = true
        player.volume = 0.05f
        val fadeMs = (prefs.crossfadeSeconds * 1000L).coerceAtLeast(500L)
        val steps = 20
        val stepDelay = fadeMs / steps
        var i = 0
        val runnable = object : Runnable {
            override fun run() {
                i++
                player.volume = (i / steps.toFloat()).coerceIn(0f, 1f)
                if (i < steps) handler.postDelayed(this, stepDelay) else fadingIn = false
            }
        }
        handler.postDelayed(runnable, stepDelay)
    }

    init {
        player.addListener(playerListener)
        handler.post(positionWatcher)
    }

    fun release() {
        player.removeListener(playerListener)
        handler.removeCallbacksAndMessages(null)
    }
}
