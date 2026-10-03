package com.asfaltosonoro.dplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import com.asfaltosonoro.dplayer.settings.VisualizerAppearance
import com.asfaltosonoro.dplayer.settings.VizColorMode
import kotlin.math.log10
import kotlin.math.max

private fun Long.toColor() = Color(this)

/** Solid color or a Brush built from the appearance's gradient pair. */
private fun solidOrBrush(appearance: VisualizerAppearance, vertical: Boolean): Pair<Color?, Brush?> =
    if (appearance.colorMode == VizColorMode.SOLID) {
        appearance.solidColor.toColor() to null
    } else {
        val from = appearance.gradientFrom.toColor()
        val to = appearance.gradientTo.toColor()
        null to if (vertical) {
            Brush.verticalGradient(listOf(to, from, to)) // edge -> center -> edge, top to bottom
        } else {
            Brush.verticalGradient(listOf(to, from)) // top -> bottom (FFT bars)
        }
    }

/** Elapsed real seconds since last call — used to drive frame-rate-independent peak decay. */
private class FrameClock {
    private var last = System.nanoTime()
    fun deltaSeconds(): Float {
        val now = System.nanoTime()
        val dt = (now - last) / 1_000_000_000f
        last = now
        return dt.coerceIn(0f, 0.25f) // clamp in case of a big recomposition gap
    }
}

@Composable
fun OscilloscopeView(frame: VisualizerFrame, appearance: VisualizerAppearance, modifier: Modifier = Modifier) {
    val (solid, brush) = solidOrBrush(appearance, vertical = true)
    var peak by remember { mutableFloatStateOf(0f) }
    val clock = remember { FrameClock() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val dt = clock.deltaSeconds()
        val samples = frame.waveform
        if (samples.isEmpty()) return@Canvas
        val midY = size.height / 2f
        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)

        val path = androidx.compose.ui.graphics.Path()
        var maxAbs = 0f
        for (i in samples.indices) {
            val x = i * stepX
            val s = samples[i]
            if (kotlin.math.abs(s) > maxAbs) maxAbs = kotlin.math.abs(s)
            val y = midY - s * (size.height * 0.45f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round)
        if (brush != null) drawPath(path, brush = brush, style = stroke) else drawPath(path, color = solid!!, style = stroke)

        if (appearance.peakHoldEnabled) {
            if (maxAbs > peak) peak = maxAbs else {
                val decayPerSecond = 1f / appearance.peakHoldDecaySeconds.coerceAtLeast(0.1f)
                peak = max(0f, peak - decayPerSecond * dt)
            }
            val peakY = midY - peak * (size.height * 0.45f)
            val peakYMirror = midY + peak * (size.height * 0.45f)
            val peakColor = (if (appearance.colorMode == VizColorMode.GRADIENT) appearance.gradientTo else appearance.solidColor).toColor()
            drawLine(peakColor, Offset(0f, peakY), Offset(size.width, peakY), strokeWidth = 1.5f)
            drawLine(peakColor, Offset(0f, peakYMirror), Offset(size.width, peakYMirror), strokeWidth = 1.5f)
        }
    }
}

@Composable
fun FftSpectrumView(frame: VisualizerFrame, appearance: VisualizerAppearance, modifier: Modifier = Modifier) {
    val (solid, brush) = solidOrBrush(appearance, vertical = true)
    var peaks by remember { mutableStateOf(FloatArray(0)) }
    val clock = remember { FrameClock() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val dt = clock.deltaSeconds()
        val mags = frame.fftMagnitudes
        if (mags.isEmpty()) return@Canvas
        val barCount = mags.size.coerceAtMost(64)
        if (peaks.size != barCount) peaks = FloatArray(barCount)
        val barWidth = size.width / barCount
        val decayPerSecond = 1f / appearance.peakHoldDecaySeconds.coerceAtLeast(0.1f)

        for (i in 0 until barCount) {
            val v = mags[i]
            val h = size.height * v
            if (brush != null) {
                drawRect(brush = brush, topLeft = Offset(i * barWidth, size.height - h), size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, h))
            } else {
                drawRect(color = solid!!, topLeft = Offset(i * barWidth, size.height - h), size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, h))
            }

            if (appearance.peakHoldEnabled) {
                if (v > peaks[i]) peaks[i] = v else peaks[i] = max(0f, peaks[i] - decayPerSecond * dt)
                val peakY = size.height - size.height * peaks[i]
                val peakColor = (if (appearance.colorMode == VizColorMode.GRADIENT) appearance.gradientTo else appearance.solidColor).toColor()
                drawRect(peakColor, topLeft = Offset(i * barWidth, peakY), size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, 2f))
            }
        }
    }
}

/**
 * Single VU needle with a dB scale + ticks, styled after the reference
 * screenshots. Level is RMS from the post-mix capture buffer, converted to
 * an approximate dBFS value — not true VU ballistics (300ms attack/release)
 * since a visual approximation is all that's needed here.
 */
@Composable
fun VuMeterView(frame: VisualizerFrame, appearance: VisualizerAppearance, modifier: Modifier = Modifier) {
    val needleColor = appearance.solidColor.toColor()
    // (dB, label) ticks across the arc, matching the Neutron-style reference.
    val ticks = listOf(-20f to "20", -16f to "16", -13f to "13", -10f to "10", -7f to "7", -5f to "5", -3f to "3", -1f to "1", 0f to "0", 3f to "+3")
    val minDb = -22f
    val maxDb = 4f

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.88f)
        val radius = size.minDimension * 0.55f

        fun angleFor(db: Float): Double {
            val t = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            return Math.toRadians(180.0 + t * 180.0) // 180deg (left) .. 360deg (right)
        }

        // Arc + red zone past 0dB
        drawArc(
            color = Color(0xFF888888), startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
        val redStartAngle = Math.toDegrees(angleFor(0f)).toFloat()
        drawArc(
            color = Color(0xFFCC3333), startAngle = redStartAngle, sweepAngle = 180f - (redStartAngle - 180f), useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )

        // Ticks + labels
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = radius * 0.13f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        for ((db, label) in ticks) {
            val a = angleFor(db)
            val innerR = radius * 0.85f
            val outerR = radius
            val p1 = Offset(center.x + (innerR * kotlin.math.cos(a)).toFloat(), center.y + (innerR * kotlin.math.sin(a)).toFloat())
            val p2 = Offset(center.x + (outerR * kotlin.math.cos(a)).toFloat(), center.y + (outerR * kotlin.math.sin(a)).toFloat())
            drawLine(Color.LightGray, p1, p2, strokeWidth = 2f)
            val labelR = radius * 0.68f
            val lp = Offset(center.x + (labelR * kotlin.math.cos(a)).toFloat(), center.y + (labelR * kotlin.math.sin(a)).toFloat())
            drawContext.canvas.nativeCanvas.drawText(label, lp.x, lp.y, textPaint)
        }

        // Needle, from an approximate dBFS of the RMS level
        val db = if (frame.level > 0.0001f) (20f * log10(frame.level.toDouble())).toFloat() else minDb
        val needleAngle = angleFor(db.coerceIn(minDb, maxDb))
        val end = Offset(center.x + (radius * 0.9f * kotlin.math.cos(needleAngle)).toFloat(), center.y + (radius * 0.9f * kotlin.math.sin(needleAngle)).toFloat())
        drawLine(needleColor, center, end, strokeWidth = 4f)
    }
}
