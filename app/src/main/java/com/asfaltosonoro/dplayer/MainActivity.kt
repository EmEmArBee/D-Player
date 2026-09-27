package com.asfaltosonoro.dplayer

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.asfaltosonoro.dplayer.player.PlaybackService
import com.asfaltosonoro.dplayer.ui.AppRoot
import com.google.common.util.concurrent.MoreExecutors

/**
 * Single-Activity host. Real transport state lives in PlaybackService via
 * MediaController (so the UI just reflects/commands the session — this is
 * what keeps playback alive across configuration changes / Activity death,
 * important since the manifest allows userLandscape<->portrait switches).
 *
 * Skin theme is picked BEFORE super.onCreate(): "FULL GLASS" needs a real
 * translucent window theme (Theme.DPlayer.Glass) for the head unit's own
 * background to show through — that only takes effect if set this early.
 */
class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)
    private var isGlassSkin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isGlassSkin = getSharedPreferences("skin", MODE_PRIVATE).getString("mode", null) == "FULL_GLASS"
        if (isGlassSkin) setTheme(R.style.Theme_DPlayer_Glass)
        super.onCreate(savedInstanceState)

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isGlassSkin) Color.Transparent else Color.Black,
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
