package com.asfaltosonoro.dplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asfaltosonoro.dplayer.settings.VisualizerAppearanceManager
import com.asfaltosonoro.dplayer.settings.VizColorMode
import com.asfaltosonoro.dplayer.settings.VizColorPresets

@Composable
fun VisualizerAppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { VisualizerAppearanceManager(context) }
    var appearance by remember { mutableStateOf(manager.load()) }

    fun update(block: (com.asfaltosonoro.dplayer.settings.VisualizerAppearance) -> com.asfaltosonoro.dplayer.settings.VisualizerAppearance) {
        appearance = block(appearance)
        manager.save(appearance)
    }

    var showPicker by remember { mutableStateOf<PickerTarget?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("Visualizer appearance", color = Color.White, fontSize = 20.sp)
        }

        SectionLabel("Color mode")
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            FilterChip2("Solid", appearance.colorMode == VizColorMode.SOLID) { update { it.copy(colorMode = VizColorMode.SOLID) } }
            Spacer(Modifier.width(12.dp))
            FilterChip2("Gradient", appearance.colorMode == VizColorMode.GRADIENT) { update { it.copy(colorMode = VizColorMode.GRADIENT) } }
        }

        if (appearance.colorMode == VizColorMode.SOLID) {
            SectionLabel("Solid color presets")
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                VizColorPresets.solids.forEach { (name, argb) ->
                    Swatch(Color(argb), selected = appearance.solidColor == argb) { update { it.copy(solidColor = argb) } }
                }
            }
            TextButton(onClick = { showPicker = PickerTarget.SOLID }) { Text("Custom color…") }
        } else {
            SectionLabel("Gradient presets")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                VizColorPresets.gradients.forEach { (name, from, to) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { update { it.copy(gradientFrom = from, gradientTo = to) } }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Swatch(Color(from), selected = appearance.gradientFrom == from && appearance.gradientTo == to) {}
                        Spacer(Modifier.width(4.dp))
                        Swatch(Color(to), selected = false) {}
                        Spacer(Modifier.width(12.dp))
                        Text(name, color = Color.White)
                    }
                }
            }
            Row {
                TextButton(onClick = { showPicker = PickerTarget.GRADIENT_FROM }) { Text("Custom center/bottom…") }
                TextButton(onClick = { showPicker = PickerTarget.GRADIENT_TO }) { Text("Custom edges/top…") }
            }
        }

        showPicker?.let { target ->
            val initial = when (target) {
                PickerTarget.SOLID -> appearance.solidColor
                PickerTarget.GRADIENT_FROM -> appearance.gradientFrom
                PickerTarget.GRADIENT_TO -> appearance.gradientTo
            }
            HsvColorPicker(initialArgb = initial) { newArgb ->
                update {
                    when (target) {
                        PickerTarget.SOLID -> it.copy(solidColor = newArgb)
                        PickerTarget.GRADIENT_FROM -> it.copy(gradientFrom = newArgb)
                        PickerTarget.GRADIENT_TO -> it.copy(gradientTo = newArgb)
                    }
                }
            }
        }

        SectionLabel("Peak hold")
        SwitchRow3("Show fading peak marks", appearance.peakHoldEnabled) { update { c -> c.copy(peakHoldEnabled = it) } }
        if (appearance.peakHoldEnabled) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Decay time: ${"%.1f".format(appearance.peakHoldDecaySeconds)}s", color = Color.White, fontSize = 13.sp)
                Slider(
                    value = appearance.peakHoldDecaySeconds,
                    onValueChange = { v -> update { it.copy(peakHoldDecaySeconds = v) } },
                    valueRange = 0.3f..4f,
                )
            }
        }

        SectionLabel("Performance")
        SwitchRow3("HD rendering (higher capture rate, more CPU)", appearance.hdRendering) { update { c -> c.copy(hdRendering = it) } }
        Text(
            "Off by default. Turn on only if the head unit's CPU can keep up — it noticeably increases load.",
            color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

private enum class PickerTarget { SOLID, GRADIENT_FROM, GRADIENT_TO }

@Composable
private fun HsvColorPicker(initialArgb: Long, onChange: (Long) -> Unit) {
    val initColor = Color(initialArgb)
    val hsv = remember {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initColor.toArgb(), arr)
        mutableStateOf(arr)
    }
    var hue by remember { mutableStateOf(hsv.value[0]) }
    var sat by remember { mutableStateOf(hsv.value[1]) }
    var value by remember { mutableStateOf(hsv.value[2]) }

    fun currentColor(): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp)).background(currentColor()))
        Spacer(Modifier.height(8.dp))
        Text("Hue", color = Color.Gray, fontSize = 11.sp)
        Slider(value = hue, onValueChange = { hue = it; onChange(currentColor().toArgb().toLong() and 0xFFFFFFFFL) }, valueRange = 0f..360f)
        Text("Saturation", color = Color.Gray, fontSize = 11.sp)
        Slider(value = sat, onValueChange = { sat = it; onChange(currentColor().toArgb().toLong() and 0xFFFFFFFFL) }, valueRange = 0f..1f)
        Text("Brightness", color = Color.Gray, fontSize = 11.sp)
        Slider(value = value, onValueChange = { value = it; onChange(currentColor().toArgb().toLong() and 0xFFFFFFFFL) }, valueRange = 0f..1f)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xFFDFF5E1), fontSize = 14.sp, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp))
}

@Composable
private fun FilterChip2(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFDFF5E1) else Color(0xFF222222))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp))
                else Modifier,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow3(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
