package com.asfaltosonoro.dplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * Canvas renderers driven by real VisualizerFrame data (see
 * VisualizerController.kt). Kept intentionally simple/allocation-light per
 * frame — no Path smoothing, no extra passes — for "max fluidity, min CPU"
 * on head unit SoCs.
 */

@Composable
fun OscilloscopeView(frame: VisualizerFrame, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val samples = frame.waveform
        if (samples.isEmpty()) return@Canvas
        val midY = size.height / 2f
        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
        val path = androidx.compose.ui.graphics.Path()
        for (i in samples.indices) {
            val x = i * stepX
            val y = midY - samples[i] * (size.height * 0.45f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = Color(0xFFDFF5E1),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun FftSpectrumView(frame: VisualizerFrame, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val mags = frame.fftMagnitudes
        if (mags.isEmpty()) return@Canvas
        val barCount = mags.size.coerceAtMost(64)
        val barWidth = size.width / barCount
        for (i in 0 until barCount) {
            val v = mags[i]
            val h = size.height * v
            drawRect(
                color = Color(0xFFDFF5E1),
                topLeft = Offset(i * barWidth, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, h),
            )
        }
    }
}

/**
 * Single VU needle driven by the RMS level of the post-mix capture buffer.
 * NOTE: true discrete L/R metering needs a pre-mix stereo tap (custom
 * AudioProcessor in the ExoPlayer render pipeline) that Visualizer can't
 * give us — simplified to one meter per the "if it's a pain just do one"
 * call. TODO: revisit with a stereo AudioProcessor if real L/R is wanted.
 */
@Composable
fun VuMeterView(frame: VisualizerFrame, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.85f)
        val radius = size.minDimension * 0.55f
        drawArc(
            color = Color(0xFFDFF5E1),
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
        // level 0f..1f -> needle sweeps 180deg (silence) .. 360deg (full)
        val angleDeg = 180f + (frame.level.coerceIn(0f, 1f) * 180f)
        val rad = Math.toRadians(angleDeg.toDouble())
        val end = Offset(
            center.x + (radius * 0.9f * kotlin.math.cos(rad)).toFloat(),
            center.y + (radius * 0.9f * kotlin.math.sin(rad)).toFloat(),
        )
        drawLine(Color.Red, center, end, strokeWidth = 4f)
    }
}
