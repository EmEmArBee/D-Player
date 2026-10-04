package com.asfaltosonoro.dplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
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
import androidx.media3.common.MediaItem
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
 *
 * Also the entry point for "Open with" (VIEW) and "Share" (SEND) on audio
 * files from other apps — see handleIncomingAudioIntent().
 */
class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)
    private var isGlassSkin = false
    private var pendingAudioUri: Uri? = null

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
        // Backs the manual-path USB/SD fallback (LocalPathBrowser) — only
        // meaningful up to API 29, harmless no-op request above that.
        if (Build.VERSION.SDK_INT <= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) toAsk += Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) toAsk += Manifest.permission.POST_NOTIFICATIONS
        if (toAsk.isNotEmpty()) permissionLauncher.launch(toAsk.toTypedArray())

        pendingAudioUri = extractAudioUri(intent)

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get()
            pendingAudioUri?.let { playExternalUri(it) }
            pendingAudioUri = null
        }, MoreExecutors.directExecutor())

        setContent {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractAudioUri(intent)?.let {
            if (controller != null) playExternalUri(it) else pendingAudioUri = it
        }
    }

    /** VIEW ("open with") gives the uri in intent.data; SEND ("share to") gives it in EXTRA_STREAM. */
    private fun extractAudioUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }

    private fun playExternalUri(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        controller?.setMediaItem(MediaItem.Builder().setUri(uri).build())
        controller?.prepare()
        controller?.play()
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}
