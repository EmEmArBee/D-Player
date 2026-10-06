package com.asfaltosonoro.dplayer.settings

import android.content.Context
import androidx.core.content.edit
import com.asfaltosonoro.dplayer.source.FolderShortcut
import com.asfaltosonoro.dplayer.source.SourceType

/**
 * Everything that isn't the skin (see skin/SkinManager) but still needs to
 * survive process death: the 3 folder shortcuts, visualizer display mode,
 * EQ/preamp/compressor/AGP/crossfade values. Flat SharedPreferences on
 * purpose — no local DB, matches the "leggero e veloce" requirement.
 */
class PlayerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    // ---- Folder shortcuts (3 slots) ----
    fun loadShortcut(index: Int): FolderShortcut? {
        val type = prefs.getString("shortcut_${index}_type", null) ?: return null
        val label = prefs.getString("shortcut_${index}_label", "Shortcut $index")!!
        val uri = prefs.getString("shortcut_${index}_uri", null) ?: return null
        return FolderShortcut(index, label, SourceType.valueOf(type), uri)
    }

    fun saveShortcut(shortcut: FolderShortcut) = prefs.edit {
        putString("shortcut_${shortcut.id}_type", shortcut.type.name)
        putString("shortcut_${shortcut.id}_label", shortcut.label)
        putString("shortcut_${shortcut.id}_uri", shortcut.uri)
    }

    fun clearShortcut(index: Int) = prefs.edit {
        remove("shortcut_${index}_type"); remove("shortcut_${index}_label"); remove("shortcut_${index}_uri")
    }

    // ---- Visualizer display ----
    /** false (default): tap cycles Oscilloscope <-> FFT, VU-meter is an optional overlay.
     *  true: tap cycles Oscilloscope -> FFT -> VU-meter, all full screen, no overlay. */
    var fullScreenVuMeters: Boolean
        get() = prefs.getBoolean("full_screen_vu", false)
        set(value) = prefs.edit { putBoolean("full_screen_vu", value) }

    /** Only meaningful when fullScreenVuMeters == false. */
    var vuOverlayEnabled: Boolean
        get() = prefs.getBoolean("vu_overlay_enabled", false)
        set(value) = prefs.edit { putBoolean("vu_overlay_enabled", value) }

    // ---- EQ / preamp / compressor-AGP / crossfade ----
    fun eqBand(index: Int): Short = prefs.getInt("eq_band_$index", 0).toShort()
    fun setEqBand(index: Int, millibel: Short) = prefs.edit { putInt("eq_band_$index", millibel.toInt()) }

    var preampStrength: Short
        get() = prefs.getInt("preamp_strength", 0).toInt().toShort()
        set(value) = prefs.edit { putInt("preamp_strength", value.toInt()) }

    var compressorAgpEnabled: Boolean
        get() = prefs.getBoolean("compressor_agp_enabled", false)
        set(value) = prefs.edit { putBoolean("compressor_agp_enabled", value) }

    var crossfadeEnabled: Boolean
        get() = prefs.getBoolean("crossfade_enabled", false)
        set(value) = prefs.edit { putBoolean("crossfade_enabled", value) }

    /** Seconds, 1-12, applied as a volume ramp across the track boundary. */
    var crossfadeSeconds: Int
        get() = prefs.getInt("crossfade_seconds", 4)
        set(value) = prefs.edit { putInt("crossfade_seconds", value) }

    /** On-screen volume +/- and mute buttons — for head units where the
     *  physical volume keys don't reach standard Android key dispatch.
     *  Defaults on since that's exactly the situation this was added for. */
    var showVolumeButtons: Boolean
        get() = prefs.getBoolean("show_volume_buttons", true)
        set(value) = prefs.edit { putBoolean("show_volume_buttons", value) }
}
