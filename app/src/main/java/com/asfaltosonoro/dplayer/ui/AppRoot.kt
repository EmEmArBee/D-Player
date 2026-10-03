package com.asfaltosonoro.dplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController

/** Simple manual back-stack — deliberately no Navigation-Compose dependency
 *  for a shell this shallow (5 screens, no deep linking needed). */
@Composable
fun AppRoot(controller: MediaController?) {
    var screen by remember { mutableStateOf<Screen>(Screen.Player) }

    BackHandler(enabled = screen != Screen.Player) { screen = Screen.Player }

    when (val s = screen) {
        is Screen.Player -> PlayerScreen(
            controller = controller,
            onOpenSettings = { screen = Screen.Settings },
            onOpenEqualizer = { screen = Screen.Equalizer },
            onOpenShortcut = { index -> screen = Screen.Browser(index) },
        )
        is Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Player },
            onConfigureShortcut = { index -> screen = Screen.ShortcutConfig(index) },
            onOpenVisualizerAppearance = { screen = Screen.VisualizerAppearance },
        )
        is Screen.VisualizerAppearance -> VisualizerAppearanceScreen(onBack = { screen = Screen.Settings })
        is Screen.ShortcutConfig -> ShortcutConfigScreen(
            index = s.index,
            onDone = { screen = Screen.Settings },
        )
        is Screen.Browser -> FileBrowserScreen(
            shortcutIndex = s.shortcutIndex,
            controller = controller,
            onBack = { screen = Screen.Player },
            onConfigure = { screen = Screen.ShortcutConfig(s.shortcutIndex) },
        )
        is Screen.Equalizer -> EqualizerScreen(onBack = { screen = Screen.Player })
    }
}
