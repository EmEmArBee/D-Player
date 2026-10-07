package com.asfaltosonoro.dplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.asfaltosonoro.dplayer.settings.PlayerPreferencesHolder
import com.asfaltosonoro.dplayer.skin.SkinManager
import com.asfaltosonoro.dplayer.settings.VisualizerAppearanceManager

/** Full-screen mode when "Full Screen VU-Meters" is on in Settings. */
private enum class FullScreenVisual { OSCILLOSCOPE, FFT, VU_METER }

/** Default mode (no full-screen VU): only these two are tap-cyclable, VU is an optional overlay. */
private enum class CompactVisual { OSCILLOSCOPE, FFT }

/**
 * Top-level layout: shortcut bar -> visual area (tap to cycle, takes all
 * remaining space) -> one compact bottom block (title / seekbar / controls).
 * The bottom block is deliberately kept to 3 thin rows with tight padding —
 * oscilloscope/FFT/VU-meter are the point of this screen, the bottom chrome
 * should take as little of it as possible.
 */
@Composable
fun PlayerScreen(
    controller: MediaController?,
    onOpenSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenShortcut: (Int) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferencesHolder.get(context) }
    val skinManager = remember { SkinManager(context) }
    val skin = remember { skinManager.load() }
    val appearanceManager = remember { VisualizerAppearanceManager(context) }
    val appearance = remember { appearanceManager.load() }
    var fullScreenVu by remember { mutableStateOf(prefs.fullScreenVuMeters) }
    var vuOverlayOn by remember { mutableStateOf(prefs.vuOverlayEnabled) }

    var compactMode by remember { mutableStateOf(CompactVisual.OSCILLOSCOPE) }
    var fullScreenMode by remember { mutableStateOf(FullScreenVisual.OSCILLOSCOPE) }

    val frame = rememberVisualizerFrame(hd = appearance.hdRendering)

    val barColor = when (skin.mode) {
        com.asfaltosonoro.dplayer.skin.SkinMode.DEFAULT_PITCH_BLACK -> Color.Black
        com.asfaltosonoro.dplayer.skin.SkinMode.FULL_GLASS -> Color(0x33000000)
        com.asfaltosonoro.dplayer.skin.SkinMode.CUSTOM -> Color(0x88000000)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    SkinBackgroundLayer(config = skin, modifier = Modifier.fillMaxSize())

    Column(modifier = Modifier.fillMaxSize()) {
        ShortcutBar(barColor, prefs, onOptions = onOpenSettings, onEqualizer = onOpenEqualizer, onShortcut = onOpenShortcut)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable {
                    if (fullScreenVu) {
                        fullScreenMode = when (fullScreenMode) {
                            FullScreenVisual.OSCILLOSCOPE -> FullScreenVisual.FFT
                            FullScreenVisual.FFT -> FullScreenVisual.VU_METER
                            FullScreenVisual.VU_METER -> FullScreenVisual.OSCILLOSCOPE
                        }
                    } else {
                        compactMode = when (compactMode) {
                            CompactVisual.OSCILLOSCOPE -> CompactVisual.FFT
                            CompactVisual.FFT -> CompactVisual.OSCILLOSCOPE
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AlbumArtLayer(controller = controller, config = skin, modifier = Modifier.fillMaxSize())

            if (fullScreenVu) {
                when (fullScreenMode) {
                    FullScreenVisual.OSCILLOSCOPE -> OscilloscopeView(frame, appearance, Modifier.fillMaxSize())
                    FullScreenVisual.FFT -> FftSpectrumView(frame, appearance, Modifier.fillMaxSize())
                    FullScreenVisual.VU_METER -> VuMeterView(frame, appearance, Modifier.fillMaxSize())
                }
            } else {
                when (compactMode) {
                    CompactVisual.OSCILLOSCOPE -> OscilloscopeView(frame, appearance, Modifier.fillMaxSize())
                    CompactVisual.FFT -> FftSpectrumView(frame, appearance, Modifier.fillMaxSize())
                }
                if (vuOverlayOn) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
                        VuMeterView(frame, appearance, Modifier.fillMaxWidth().fillMaxHeight(0.4f))
                    }
                }

                IconButton(
                    onClick = {
                        vuOverlayOn = !vuOverlayOn
                        prefs.vuOverlayEnabled = vuOverlayOn
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = "Toggle VU-meter overlay",
                        tint = if (vuOverlayOn) Color(0xFFDFF5E1) else Color.Gray,
                    )
                }
            }
        }

        BottomBar(controller = controller, barColor = barColor, showVolume = prefs.showVolumeButtons, useIdTags = prefs.showIdTagsInsteadOfFilename)
    }
    }
}

/**
 * Title (1 thin line) + seekbar-with-inline-time (1 thin line) + a single
 * row holding transport AND volume controls together. Three compact rows
 * total instead of the three separate full-padding bars this used to be —
 * that's what was eating the oscilloscope/FFT/VU's vertical space.
 */
@Composable
private fun BottomBar(controller: MediaController?, barColor: Color, showVolume: Boolean, useIdTags: Boolean) {
    var title by remember { mutableStateOf("") }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var playing by remember(controller) { mutableStateOf(controller?.isPlaying == true) }

    DisposableEffect(controller) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                title = mediaMetadata.title?.toString().orEmpty()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        controller?.addListener(listener)
        title = controller?.mediaMetadata?.title?.toString().orEmpty()
        onDispose { controller?.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            if (controller != null && !dragging) {
                position = controller.currentPosition.coerceAtLeast(0L)
                duration = controller.duration.coerceAtLeast(0L)
            }
            kotlinx.coroutines.delay(500)
        }
    }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }
    var muted by remember { mutableStateOf(audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) }
    fun refreshVolume() {
        volume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        muted = audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)
    }

    Column(modifier = Modifier.fillMaxWidth().background(barColor).padding(horizontal = 12.dp, vertical = 2.dp)) {
        androidx.compose.material3.Text(
            title.ifBlank { "—" }, color = Color.White, fontSize = 12.sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(22.dp)) {
            androidx.compose.material3.Text(formatMs(if (dragging) dragValue.toLong() else position), color = Color.Gray, fontSize = 10.sp)
            val maxMs = duration.coerceAtLeast(1L).toFloat()
            androidx.compose.material3.Slider(
                value = (if (dragging) dragValue else position.toFloat()).coerceIn(0f, maxMs),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { controller?.seekTo(dragValue.toLong()); dragging = false },
                valueRange = 0f..maxMs,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(20.dp),
            )
            androidx.compose.material3.Text(formatMs(duration), color = Color.Gray, fontSize = 10.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactButton(Icons.Filled.SkipPrevious, "Previous track") { controller?.seekToPreviousMediaItem() }
            CompactButton(Icons.Filled.FastRewind, "Rewind 10s") {
                controller?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) }
            }
            CompactButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause") {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }
            CompactButton(Icons.Filled.FastForward, "Forward 10s") {
                controller?.let {
                    val max = it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE
                    it.seekTo((it.currentPosition + 10_000L).coerceAtMost(max))
                }
            }
            CompactButton(Icons.Filled.SkipNext, "Next track") { controller?.seekToNextMediaItem() }

            if (showVolume) {
                CompactButton(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Mute", tint = if (muted) Color(0xFFFF8A65) else Color.White) {
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_TOGGLE_MUTE, 0)
                    refreshVolume()
                }
                CompactButton(Icons.Filled.Remove, "Volume down") {
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, 0)
                    refreshVolume()
                }
                androidx.compose.material3.Text(
                    "$volume/$maxVolume", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.widthIn(min = 28.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                CompactButton(Icons.Filled.Add, "Volume up") {
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, 0)
                    refreshVolume()
                }
            }
        }
    }
}

/** No IconButton here on purpose — Material enforces a ~48dp min touch
 *  target on IconButton, which is exactly what was making the controls
 *  row taller than it needs to be now that it also carries volume. */
@Composable
private fun CompactButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(32.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun ShortcutBar(
    barColor: Color,
    prefs: com.asfaltosonoro.dplayer.settings.PlayerPreferences,
    onOptions: () -> Unit,
    onEqualizer: () -> Unit,
    onShortcut: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(barColor).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.Filled.Tune, "EQ", Color.White, onEqualizer)
        repeat(3) { index ->
            val label = remember { prefs.loadShortcut(index)?.label ?: "Set ${index + 1}" }
            BarButton(Icons.Filled.Folder, label, Color.White) { onShortcut(index) }
        }
        BarButton(Icons.Filled.Settings, "Options", Color.White, onOptions)
    }
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        androidx.compose.material3.Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
    }
}
