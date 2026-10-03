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
 * Compose-friendly VisualizerFrame.
 *
 * Rendering-optimization pass: the byte->float conversion buffers are
 * reused across callbacks instead of allocating a new FloatArray every
 * frame (Visualizer fires at up to ~20-60Hz) — same array instance
 * overwritten in place, only reallocated if the capture size actually
 * changes (it doesn't, in practice, once started). Cuts GC churn, which
 * matters more than micro-optimizing the draw calls on weak head unit SoCs.
 */
@Composable
fun rememberVisualizerFrame(hd: Boolean = false): VisualizerFrame {
    var frame by remember { mutableStateOf(VisualizerFrame()) }

    val engine = remember(hd) {
        var waveBuf = FloatArray(0)
        var fftBuf = FloatArray(0)

        VisualizerEngine(
            onWaveform = { bytes ->
                if (waveBuf.size != bytes.size) waveBuf = FloatArray(bytes.size)
                var sumSquares = 0f
                for (i in bytes.indices) {
                    val s = ((bytes[i].toInt() and 0xFF) - 128) / 128f
                    waveBuf[i] = s
                    sumSquares += s * s
                }
                val rms = sqrt(sumSquares / bytes.size.coerceAtLeast(1))
                frame = frame.copy(waveform = waveBuf, level = rms.coerceIn(0f, 1f))
            },
            onFft = { bytes ->
                val n = bytes.size / 2
                if (fftBuf.size != n) fftBuf = FloatArray(n)
                for (i in 0 until n) {
                    val real = bytes[i * 2].toFloat()
                    val imag = if (i * 2 + 1 < bytes.size) bytes[i * 2 + 1].toFloat() else 0f
                    fftBuf[i] = (hypot(real, imag) / 128f).coerceIn(0f, 1f)
                }
                frame = frame.copy(fftMagnitudes = fftBuf)
            },
        )
    }

    LaunchedEffect(engine) {
        PlaybackServiceBridge.audioSessionId.collect { id ->
            if (id != null) engine.start(id, hd) else engine.stop()
        }
    }

    DisposableEffect(engine) {
        onDispose { engine.stop() }
    }

    return frame
}
