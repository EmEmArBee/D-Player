package com.asfaltosonoro.dplayer.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.session.MediaController
import com.asfaltosonoro.dplayer.skin.SkinConfig
import com.asfaltosonoro.dplayer.skin.SkinMode

/**
 * Layering order (back to front), per spec: skin background -> album art
 * (semi-transparent, optional) -> oscilloscope/FFT/VU on top of that.
 * FULL_GLASS deliberately renders nothing here — the head unit's own
 * background shows through because MainActivity switches to a translucent
 * window theme for that mode (see MainActivity.onCreate / Theme.DPlayer.Glass).
 */
@Composable
fun SkinBackgroundLayer(config: SkinConfig, modifier: Modifier = Modifier) {
    when (config.mode) {
        SkinMode.DEFAULT_PITCH_BLACK -> androidx.compose.foundation.layout.Box(modifier.background(Color.Black))
        SkinMode.FULL_GLASS -> { /* window itself is translucent, nothing to draw */ }
        SkinMode.CUSTOM -> {
            val context = LocalContext.current
            val bitmap = remember(config.customImageUri) {
                config.customImageUri?.let { uriString ->
                    runCatching {
                        context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use {
                            BitmapFactory.decodeStream(it)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
            }
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
            } else {
                androidx.compose.foundation.layout.Box(modifier.background(Color.Black))
            }
        }
    }
}

@Composable
fun AlbumArtLayer(controller: MediaController?, config: SkinConfig, modifier: Modifier = Modifier) {
    if (!config.showAlbumArtOverlay) return
    val artworkData = controller?.mediaMetadata?.artworkData ?: return
    val bitmap = remember(artworkData) {
        runCatching { BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)?.asImageBitmap() }.getOrNull()
    } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.alpha(config.albumArtOpacity),
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
    )
}
