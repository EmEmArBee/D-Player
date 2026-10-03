package com.asfaltosonoro.dplayer.player

import android.media.audiofx.Visualizer

/**
 * Wraps android.media.audiofx.Visualizer: the platform-native way to grab
 * waveform + FFT magnitude data from a running audio session, with no extra
 * PCM decoding work on our side. This is the "max compatibility, max
 * smoothness, low overhead" option requested:
 *  - waveform capture -> oscilloscope
 *  - FFT capture -> spectrum view
 *  - same raw waveform buffer, split by L/R phase correlation approx, feeds
 *    the stereo VU-meters (true discrete L/R would need tapping the mixer
 *    pre-mix, which most OEM head unit audio HALs don't expose — see below)
 *
 * Compatibility note: some OEM head unit ROMs lock down
 * captureAudio()/getEnabled() on the *global* mix (session 0) for non-system
 * apps. We always attach to our own PlaybackService's audioSessionId (never
 * session 0), which is the session every app is guaranteed to control -
 * this is the fallback-safe choice mentioned in the requirements.
 *
 * True separate L/R metering: Visualizer only exposes the post-mix mono-ish
 * capture buffer on most devices. For real discrete stereo VU we'd need an
 * AudioProcessor tap inside the ExoPlayer render pipeline (reads the actual
 * stereo PCM before output). TODO: add a lightweight custom AudioProcessor
 * for exact L/R peak metering; Visualizer stays as the FFT/oscilloscope
 * source either way since it's cheaper for that job.
 */
class VisualizerEngine(private val onWaveform: (ByteArray) -> Unit, private val onFft: (ByteArray) -> Unit) {

    private var visualizer: Visualizer? = null

    fun start(audioSessionId: Int, hd: Boolean = false) {
        stop()
        runCatching {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                val rate = if (hd) Visualizer.getMaxCaptureRate() else Visualizer.getMaxCaptureRate() / 2
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray, samplingRate: Int) {
                            onWaveform(waveform)
                        }
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray, samplingRate: Int) {
                            onFft(fft)
                        }
                    },
                    rate, // half max by default — smooth without being CPU-hungry; HD setting uses full rate
                    true,
                    true,
                )
                enabled = true
            }
        }
        // If instantiation fails (locked-down OEM audio session), visualizer
        // stays null and the UI simply shows a static/idle scope — no crash.
    }

    fun stop() {
        visualizer?.release()
        visualizer = null
    }
}
