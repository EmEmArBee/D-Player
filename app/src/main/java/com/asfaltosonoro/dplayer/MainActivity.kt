package com.asfaltosonoro.dplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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

    /**
     * Explicit STREAM_MUSIC targeting for the hardware volume keys. Normally
     * Android routes these automatically to whatever stream is "active",
     * but some aftermarket head unit ROMs route them to a vendor radio/
     * source service instead of standard Android key dispatch — if that's
     * what's happening here, this override never even gets called (the OS
     * never hands us the KeyEvent), and there's nothing an app can do about
     * that short of the ROM itself exposing a setting for it.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            return true
        }
        return super.onKeyDown(keyCode, event)
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
