package com.asfaltosonoro.dplayer.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.asfaltosonoro.dplayer.settings.PlaybackStateStore

/**
 * Checkpoints the current queue/track/position so playback resumes on next
 * launch — on every track change, on pause, and every 10s while playing (so
 * an abrupt kill, e.g. the car shutting off, loses at most ~10s of position,
 * not the whole session).
 */
class PlaybackPersistence(private val player: ExoPlayer, private val store: PlaybackStateStore) {

    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (player.isPlaying) saveNow()
            handler.postDelayed(this, 10_000)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveNow()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveNow()
        }
    }

    fun saveNow() {
        val count = player.mediaItemCount
        if (count == 0) return
        val items = (0 until count).map { player.getMediaItemAt(it) }
        store.save(items, player.currentMediaItemIndex, player.currentPosition)
    }

    init {
        player.addListener(listener)
        handler.post(tick)
    }

    fun release() {
        saveNow()
        player.removeListener(listener)
        handler.removeCallbacksAndMessages(null)
    }
}
