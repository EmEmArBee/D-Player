package com.asfaltosonoro.dplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PlaybackService and MainActivity run in the same app process (no
 * android:process split in the manifest), so instead of round-tripping
 * through MediaController custom commands just to read the audio session id
 * for the Visualizer, we share it via this in-process singleton. Simpler,
 * cheaper, zero IPC — appropriate since both sides are Kotlin objects in the
 * same VM. If a future build ever moves playback to a separate process this
 * needs to become a MediaController custom command instead.
 */
object PlaybackServiceBridge {
    private val _audioSessionId = MutableStateFlow<Int?>(null)
    val audioSessionId: StateFlow<Int?> = _audioSessionId

    fun setAudioSessionId(id: Int) {
        _audioSessionId.value = id
    }

    fun clear() {
        _audioSessionId.value = null
    }
}
