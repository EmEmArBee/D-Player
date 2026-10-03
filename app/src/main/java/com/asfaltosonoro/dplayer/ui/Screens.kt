package com.asfaltosonoro.dplayer.ui

sealed class Screen {
    data object Player : Screen()
    data object Settings : Screen()
    data class ShortcutConfig(val index: Int) : Screen()
    data class Browser(val shortcutIndex: Int) : Screen()
    data object Equalizer : Screen()
    data object VisualizerAppearance : Screen()
}
