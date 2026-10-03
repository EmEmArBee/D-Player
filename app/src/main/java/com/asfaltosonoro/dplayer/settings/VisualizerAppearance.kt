package com.asfaltosonoro.dplayer.settings

enum class VizColorMode { SOLID, GRADIENT }

/**
 * Oscilloscope/FFT look. Gradient meaning depends on the view:
 *  - FFT: gradientFrom = bottom of the bars, gradientTo = top
 *  - Oscilloscope: gradientFrom = center (near-zero amplitude),
 *    gradientTo = extremes (peaks close to the canvas edges)
 * All colors packed as 0xAARRGGBB Longs (SharedPreferences-friendly).
 */
data class VisualizerAppearance(
    val colorMode: VizColorMode = VizColorMode.SOLID,
    val solidColor: Long = 0xFFDFF5E1,
    val gradientFrom: Long = 0xFFDFF5E1,
    val gradientTo: Long = 0xFF123322,
    val peakHoldEnabled: Boolean = true,
    val peakHoldDecaySeconds: Float = 1.5f,
    val hdRendering: Boolean = false,
)

object VizColorPresets {
    // name to ARGB
    val solids = listOf(
        "Retro Green" to 0xFFDFF5E1L,
        "Amber" to 0xFFFFB300L,
        "Ice Blue" to 0xFF42A5F5L,
        "Mono White" to 0xFFFFFFFFL,
        "Hot Red" to 0xFFFF5252L,
    )
    // from (center/bottom) to (edges/top)
    val gradients = listOf(
        Triple("Green Fade", 0xFFDFF5E1L, 0xFF0B2A1AL),
        Triple("Amber Fire", 0xFFFFF3C4L, 0xFFB33F00L),
        Triple("Ocean", 0xFF80DEEAL, 0xFF0D47A1L),
        Triple("Neon Purple", 0xFFE1BEE7L, 0xFF4A148CL),
        Triple("Mono Fade", 0xFFFFFFFFL, 0xFF444444L),
    )
}
