package com.asfaltosonoro.dplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asfaltosonoro.dplayer.player.AudioEffectsChain
import com.asfaltosonoro.dplayer.settings.PlayerPreferencesHolder

/**
 * EQ / preamp / compressor-AGP / crossfade controls. Reads/writes
 * AudioEffectsChain directly (same-process singleton owned by
 * PlaybackService — see PlaybackServiceBridge for why that's fine here) and
 * mirrors every change into PlayerPreferences so it survives a service
 * restart (see AudioEffectsChain.restoreFrom).
 */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferencesHolder.get(context) }
    val bandCount = remember { AudioEffectsChain.bandCount() }
    val (minLevel, maxLevel) = remember { AudioEffectsChain.bandRange() }

    var compressorAgp by remember { mutableStateOf(prefs.compressorAgpEnabled) }
    var crossfade by remember { mutableStateOf(prefs.crossfadeEnabled) }
    var crossfadeSeconds by remember { mutableStateOf(prefs.crossfadeSeconds.toFloat()) }
    var preamp by remember { mutableStateOf(prefs.preampStrength.toFloat()) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("Equalizer", color = Color.White, fontSize = 20.sp)
        }

        if (bandCount == 0) {
            Text(
                "Connecting to the audio engine…",
                color = Color.Gray, modifier = Modifier.padding(16.dp),
            )
        } else {
            // Horizontal sliders, one per band — simpler and safer than a
            // rotated-vertical-fader layout hack; visually less like the
            // reference screenshot but functionally identical. TODO: swap
            // for a proper vertical fader widget in a later visual pass.
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                for (band in 0 until bandCount) {
                    var level by remember { mutableStateOf(prefs.eqBand(band).toFloat()) }
                    SliderRow(
                        label = "${AudioEffectsChain.bandCenterFreqHz(band)}Hz",
                        value = level,
                        range = minLevel.toFloat()..maxLevel.toFloat(),
                        onChange = {
                            level = it
                            AudioEffectsChain.setBand(band, it.toInt().toShort())
                            prefs.setEqBand(band, it.toInt().toShort())
                        },
                    )
                }
            }
        }

        SliderRow(
            label = "Preamp", value = preamp, range = 0f..1000f,
            onChange = { preamp = it; AudioEffectsChain.setPreampBoost(it.toInt().toShort()); prefs.preampStrength = it.toInt().toShort() },
        )

        SwitchRow2(
            label = "Compressor / AGP", checked = compressorAgp,
            onCheckedChange = { compressorAgp = it; AudioEffectsChain.setCompressorAgpEnabled(it); prefs.compressorAgpEnabled = it },
        )

        SwitchRow2(
            label = "Crossfade", checked = crossfade,
            onCheckedChange = { crossfade = it; prefs.crossfadeEnabled = it },
        )
        if (crossfade) {
            SliderRow(
                label = "Crossfade length (${crossfadeSeconds.toInt()}s)", value = crossfadeSeconds, range = 1f..12f,
                onChange = { crossfadeSeconds = it; prefs.crossfadeSeconds = it.toInt() },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow2(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
