package com.asfaltosonoro.dplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.asfaltosonoro.dplayer.player.PlaybackService
import com.asfaltosonoro.dplayer.player.PlaybackServiceBridge
import com.asfaltosonoro.dplayer.ui.AppRoot
import com.google.common.util.concurrent.MoreExecutors

/**
 * Single-Activity host. Playback state lives in PlaybackService (via
 * MediaController). Skin theme is picked BEFORE super.onCreate(): FULL GLASS
 * needs a translucent window theme, which only works if set that early.
 */
class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)
    private var isGlassSkin = false

    // RECORD_AUDIO is required by android.media.audiofx.Visualizer (oscilloscope/FFT/VU).
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        PlaybackServiceBridge.setVisualizerPermission(result[Manifest.permission.RECORD_AUDIO] == true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isGlassSkin = getSharedPreferences("skin", MODE_PRIVATE).getString("mode", null) == "FULL_GLASS"
        if (isGlassSkin) setTheme(R.style.Theme_DPlayer_Glass)
        super.onCreate(savedInstanceState)
        if (isGlassSkin) window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))

        val hasRecord = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        PlaybackServiceBridge.setVisualizerPermission(hasRecord)
        val toAsk = mutableListOf<String>()
        if (!hasRecord) toAsk += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) toAsk += Manifest.permission.POST_NOTIFICATIONS
        if (toAsk.isNotEmpty()) permissionLauncher.launch(toAsk.toTypedArray())

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())

        setContent {
            // Dark scheme + white content color: default icon/text tint was black-on-black before.
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isGlassSkin) Color.Transparent else Color.Black,
                    contentColor = Color.White,
                ) {
                    AppRoot(controller = controller)
                }
            }
        }
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}
