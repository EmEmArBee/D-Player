package com.asfaltosonoro.dplayer.skin

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit

enum class SkinMode { DEFAULT_PITCH_BLACK, FULL_GLASS, CUSTOM }

data class SkinConfig(
    val mode: SkinMode = SkinMode.DEFAULT_PITCH_BLACK,
    val customImageUri: String? = null,
    val showAlbumArtOverlay: Boolean = true,
    val albumArtOpacity: Float = 0.35f,
)

/**
 * Persists the user's chosen background skin + album-art-overlay flag.
 * Layering order enforced in the Compose UI (back to front): skin background
 * -> album art (semi-transparent, optional) -> oscilloscope/FFT -> transport
 * controls, exactly as requested.
 */
class SkinManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("skin", Context.MODE_PRIVATE)

    fun load(): SkinConfig = SkinConfig(
        mode = SkinMode.valueOf(prefs.getString("mode", SkinMode.DEFAULT_PITCH_BLACK.name)!!),
        customImageUri = prefs.getString("custom_uri", null),
        showAlbumArtOverlay = prefs.getBoolean("album_art_overlay", true),
        albumArtOpacity = prefs.getFloat("album_art_opacity", 0.35f),
    )

    fun save(config: SkinConfig) = prefs.edit {
        putString("mode", config.mode.name)
        putString("custom_uri", config.customImageUri)
        putBoolean("album_art_overlay", config.showAlbumArtOverlay)
        putFloat("album_art_opacity", config.albumArtOpacity)
    }

    fun setCustomImage(uri: Uri) = save(load().copy(mode = SkinMode.CUSTOM, customImageUri = uri.toString()))
}
