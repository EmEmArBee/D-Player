package com.asfaltosonoro.dplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asfaltosonoro.dplayer.settings.PlayerPreferencesHolder
import com.asfaltosonoro.dplayer.skin.SkinManager
import com.asfaltosonoro.dplayer.skin.SkinMode

@Composable
fun SettingsScreen(onBack: () -> Unit, onConfigureShortcut: (Int) -> Unit) {
    val context = LocalContext.current
    val skinManager = remember { SkinManager(context) }
    val prefs = remember { PlayerPreferencesHolder.get(context) }

    var skin by remember { mutableStateOf(skinManager.load()) }

    // FULL GLASS uses a different window theme, only applied at Activity creation:
    // recreate the Activity right away when switching into/out of it.
    fun applySkin(newConfig: com.asfaltosonoro.dplayer.skin.SkinConfig) {
        val glassChanged = (skin.mode == SkinMode.FULL_GLASS) != (newConfig.mode == SkinMode.FULL_GLASS)
        skinManager.save(newConfig)
        skin = skinManager.load()
        if (glassChanged) {
            var c: android.content.Context = context
            while (c is android.content.ContextWrapper) {
                if (c is android.app.Activity) { c.recreate(); break }
                c = c.baseContext
            }
        }
    }
    var fullScreenVu by remember { mutableStateOf(prefs.fullScreenVuMeters) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            applySkin(skin.copy(mode = SkinMode.CUSTOM, customImageUri = uri.toString()))
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
        TopBar(title = "Options", onBack = onBack)

        SectionLabel("Background skin")
        SkinModeRow(
            label = "DEFAULT PITCH BLACK", selected = skin.mode == SkinMode.DEFAULT_PITCH_BLACK,
            onClick = { applySkin(skin.copy(mode = SkinMode.DEFAULT_PITCH_BLACK)) },
        )
        SkinModeRow(
            label = "FULL GLASS (transparent)", selected = skin.mode == SkinMode.FULL_GLASS,
            onClick = { applySkin(skin.copy(mode = SkinMode.FULL_GLASS)) },
        )

        SkinModeRow(
            label = "CUSTOM (pick an image)", selected = skin.mode == SkinMode.CUSTOM,
            onClick = { pickImage.launch(arrayOf("image/*")) },
        )

        SectionLabel("Album art")
        SwitchRow(
            label = "Show album art overlay",
            checked = skin.showAlbumArtOverlay,
            onCheckedChange = { skinManager.save(skin.copy(showAlbumArtOverlay = it)); skin = skinManager.load() },
        )

        SectionLabel("Visualizer")
        SwitchRow(
            label = "Full Screen VU-Meters",
            checked = fullScreenVu,
            onCheckedChange = { fullScreenVu = it; prefs.fullScreenVuMeters = it },
        )
        Text(
            "Off: tap cycles oscilloscope/FFT, VU-meter available as an overlay. On: VU-meter becomes a third full-screen mode.",
            color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        SectionLabel("Folder shortcuts")
        repeat(3) { index ->
            val shortcut = remember(index) { prefs.loadShortcut(index) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onConfigureShortcut(index) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = Color(0xFFDFF5E1))
                    Spacer(Modifier.width(12.dp))
                    Text(shortcut?.label ?: "Shortcut ${index + 1} — not set", color = Color.White)
                }
                Text(shortcut?.type?.name ?: "", color = Color.Gray, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
        Text(title, color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xFFDFF5E1), fontSize = 14.sp, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp))
}

@Composable
private fun SkinModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = Color.White)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
