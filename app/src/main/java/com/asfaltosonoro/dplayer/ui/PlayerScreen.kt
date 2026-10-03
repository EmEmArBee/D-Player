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
 * Top-level layout: shortcut bar -> visual area (tap to cycle) -> transport,
 * mirroring the reference screenshots.
 *
 * Visualizer behaviour (per user spec):
 *  - Settings "Full Screen VU-Meters" OFF (default): tap cycles
 *    Oscilloscope <-> FFT; a small corner toggle turns the VU-meter into a
 *    semi-transparent overlay on top of whichever of the two is showing.
 *  - Settings "Full Screen VU-Meters" ON: tap cycles Oscilloscope -> FFT ->
 *    VU-meter, all full screen (matches the old 3-way behaviour); the corner
 *    overlay toggle is hidden since it doesn't apply in this mode.
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

    // Bars: solid black on the default skin, translucent scrim over glass/custom
    // so the background stays visible but icons stay readable.
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

                // Corner toggle for the VU overlay — only relevant in compact mode.
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

        TransportBar(controller = controller, barColor = barColor)
    }
    }
}

@Composable
private fun ShortcutBar(
    barColor: Color,
    prefs: com.asfaltosonoro.dplayer.settings.PlayerPreferences,
    onOptions: () -> Unit,
    onEqualizer: () -> Unit,
    onShortcut: (Int) -> Unit,
) {
    // Same plain white as the transport bar icons, on purpose — no theme
    // dependency, no ambiguity, easy to eyeball-verify against TransportBar.
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

@Composable
private fun TransportBar(controller: MediaController?, barColor: Color) {
    // Observe play state so the play/pause icon actually follows the player.
    var playing by remember(controller) { mutableStateOf(controller?.isPlaying == true) }
    DisposableEffect(controller) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(barColor).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controller?.seekToPrevious() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White) }
        IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Filled.FastRewind, "Prev track", tint = Color.White) }
        IconButton(onClick = {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color.White)
        }
        IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Filled.FastForward, "Next track", tint = Color.White) }
        IconButton(onClick = { controller?.seekToNext() }) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White) }
    }
}
