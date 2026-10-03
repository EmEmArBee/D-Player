package com.asfaltosonoro.dplayer.settings

import android.content.Context
import androidx.core.content.edit

class VisualizerAppearanceManager(context: Context) {
    private val prefs = context.getSharedPreferences("visualizer_appearance", Context.MODE_PRIVATE)

    fun load(): VisualizerAppearance = VisualizerAppearance(
        colorMode = VizColorMode.valueOf(prefs.getString("mode", VizColorMode.SOLID.name)!!),
        solidColor = prefs.getLong("solid", 0xFFDFF5E1),
        gradientFrom = prefs.getLong("grad_from", 0xFFDFF5E1),
        gradientTo = prefs.getLong("grad_to", 0xFF123322),
        peakHoldEnabled = prefs.getBoolean("peak_hold", true),
        peakHoldDecaySeconds = prefs.getFloat("peak_decay", 1.5f),
        hdRendering = prefs.getBoolean("hd", false),
    )

    fun save(a: VisualizerAppearance) = prefs.edit {
        putString("mode", a.colorMode.name)
        putLong("solid", a.solidColor)
        putLong("grad_from", a.gradientFrom)
        putLong("grad_to", a.gradientTo)
        putBoolean("peak_hold", a.peakHoldEnabled)
        putFloat("peak_decay", a.peakHoldDecaySeconds)
        putBoolean("hd", a.hdRendering)
    }
}
