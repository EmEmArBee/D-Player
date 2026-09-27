package com.asfaltosonoro.dplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asfaltosonoro.dplayer.player.PlaybackServiceBridge
import com.asfaltosonoro.dplayer.player.VisualizerEngine
import kotlin.math.hypot
import kotlin.math.sqrt

/** Normalized frame ready for the Canvas views. */
data class VisualizerFrame(
    val waveform: FloatArray = FloatArray(0),      // -1f..1f, single (post-mix) channel — see VisualizerEngine notes
    val fftMagnitudes: FloatArray = FloatArray(0),  // 0f..1f, low->high frequency bins
    val level: Float = 0f,                          // 0f..1f RMS level, drives the single VU-meter needle
)

/**
 * Starts/stops VisualizerEngine as the current PlaybackService's audio
 * session id appears/disappears, and turns its raw byte callbacks into a
 * Compose-friendly VisualizerFrame. Same-process assumption as
 * PlaybackServiceBridge — see that file for why this is safe here.
 */
@Composable
fun rememberVisualizerFrame(): VisualizerFrame {
    var frame by remember { mutableStateOf(VisualizerFrame()) }

    val engine = remember {
        VisualizerEngine(
            onWaveform = { bytes ->
                val samples = FloatArray(bytes.size) { i -> ((bytes[i].toInt() and 0xFF) - 128) / 128f }
                var sumSquares = 0f
                for (s in samples) sumSquares += s * s
                val rms = sqrt(sumSquares / samples.size.coerceAtLeast(1))
                frame = frame.copy(waveform = samples, level = rms.coerceIn(0f, 1f))
            },
            onFft = { bytes ->
                val n = bytes.size / 2
                val mags = FloatArray(n)
                for (i in 0 until n) {
                    val real = bytes[i * 2].toFloat()
                    val imag = if (i * 2 + 1 < bytes.size) bytes[i * 2 + 1].toFloat() else 0f
                    mags[i] = (hypot(real, imag) / 128f).coerceIn(0f, 1f)
                }
                frame = frame.copy(fftMagnitudes = mags)
            },
        )
    }

    LaunchedEffect(Unit) {
        PlaybackServiceBridge.audioSessionId.collect { id ->
            if (id != null) engine.start(id) else engine.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }

    return frame
}
