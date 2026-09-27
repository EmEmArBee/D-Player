package com.asfaltosonoro.dplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController

/** What's shown in the central visual area; tap cycles through the three,
 *  matching the behaviour annotated in the reference screenshots. */
private enum class VisualMode { OSCILLOSCOPE, VU_METER, FFT }

/**
 * Top-level layout: shortcut bar -> visual area (tap to cycle) -> transport,
 * mirroring the reference screenshots. Real folder-shortcut config, EQ
 * screen and file browser are separate screens (see ui/ package, TODO:
 * ShortcutBarConfig.kt, EqualizerScreen.kt, FileBrowserScreen.kt — next
 * pass); this scaffold wires the always-visible player shell + tap-to-cycle
 * visualizer so the app already runs end-to-end.
 */
@Composable
fun PlayerScreen(controller: MediaController?) {
    var visualMode by remember { mutableStateOf(VisualMode.OSCILLOSCOPE) }

    Column(modifier = Modifier.fillMaxSize()) {
        ShortcutBar(onOptions = { /* TODO: open settings */ })

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable {
                    visualMode = when (visualMode) {
                        VisualMode.OSCILLOSCOPE -> VisualMode.VU_METER
                        VisualMode.VU_METER -> VisualMode.FFT
                        VisualMode.FFT -> VisualMode.OSCILLOSCOPE
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when (visualMode) {
                VisualMode.OSCILLOSCOPE -> OscilloscopeView()
                VisualMode.VU_METER -> StereoVuMeterView()
                VisualMode.FFT -> FftSpectrumView()
            }
        }

        TransportBar(controller = controller)
    }
}

@Composable
private fun ShortcutBar(onOptions: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { /* TODO: open EQ screen */ }) { Icon(Icons.Filled.Tune, contentDescription = "Equalizer") }
        // 3 configurable folder/source shortcuts
        repeat(3) { index ->
            IconButton(onClick = { /* TODO: browse FolderShortcut[index] */ }) {
                Icon(Icons.Filled.Folder, contentDescription = "Shortcut $index")
            }
        }
        IconButton(onClick = onOptions) { Icon(Icons.Filled.Settings, contentDescription = "Options") }
    }
}

@Composable
private fun TransportBar(controller: MediaController?) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controller?.seekToPrevious() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White) }
        IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Filled.FastRewind, "Prev track", tint = Color.White) }
        IconButton(onClick = {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }) {
            val playing = controller?.isPlaying == true
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color.White)
        }
        IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Filled.FastForward, "Next track", tint = Color.White) }
        IconButton(onClick = { controller?.seekToNext() }) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White) }
    }
}
