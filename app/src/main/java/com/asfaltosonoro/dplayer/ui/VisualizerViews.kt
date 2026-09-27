package com.asfaltosonoro.dplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * Lightweight Canvas renderers fed by VisualizerEngine callbacks (see
 * player/VisualizerEngine.kt). State is a plain mutableStateOf ByteArray
 * updated from the visualizer's own capture thread at ~half max rate — this
 * keeps recomposition cheap and avoids over-drawing on low-power SoCs,
 * per "max fluidity without weighing down the system".
 *
 * TODO (wiring pass): hoist waveform/fft state from a ViewModel that owns
 * VisualizerEngine bound to the current audioSessionId; these composables
 * take the byte arrays as params once that's wired. For now they render a
 * placeholder idle trace so the screen is visually complete/testable.
 */

@Composable
fun OscilloscopeView(modifier: Modifier = Modifier) {
    var phase by remember { mutableStateOf(0f) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val midY = size.height / 2f
        val path = androidx.compose.ui.graphics.Path()
        val step = size.width / 128
        for (i in 0..128) {
            val x = i * step
            val y = midY + kotlin.math.sin((i * 0.25f) + phase) * (size.height * 0.15f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFFDFF5E1), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round))
    }
    phase += 0.05f
}

@Composable
fun FftSpectrumView(modifier: Modifier = Modifier) {
    val bars = remember { List(48) { (5..90).random() } }
    Canvas(modifier = modifier.fillMaxSize()) {
        val barWidth = size.width / bars.size
        bars.forEachIndexed { i, v ->
            val h = size.height * (v / 100f)
            drawRect(
                color = Color(0xFFDFF5E1),
                topLeft = Offset(i * barWidth, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, h),
            )
        }
    }
}

@Composable
fun StereoVuMeterView(modifier: Modifier = Modifier) {
    // Two needles: left channel + right channel, per "vorrei due vumeter"
    androidx.compose.foundation.layout.Row(modifier = modifier.fillMaxSize()) {
        VuMeterNeedle(label = "L", modifier = Modifier.weight(1f))
        VuMeterNeedle(label = "R", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VuMeterNeedle(label: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.85f)
        val radius = size.minDimension * 0.6f
        drawArc(
            color = Color(0xFFDFF5E1),
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
        val needleAngleDeg = 200f // placeholder until wired to real L/R peak
        val rad = Math.toRadians(needleAngleDeg.toDouble())
        val end = Offset(
            center.x + (radius * 0.9f * kotlin.math.cos(rad)).toFloat(),
            center.y + (radius * 0.9f * kotlin.math.sin(rad)).toFloat(),
        )
        drawLine(Color.Red, center, end, strokeWidth = 4f)
    }
}
