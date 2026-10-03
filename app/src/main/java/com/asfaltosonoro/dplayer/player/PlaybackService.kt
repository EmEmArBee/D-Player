package com.asfaltosonoro.dplayer.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.asfaltosonoro.dplayer.MainActivity
import com.asfaltosonoro.dplayer.settings.PlaybackStateStore
import com.asfaltosonoro.dplayer.settings.PlayerPreferencesHolder

/**
 * Background playback + MediaSession.
 *
 * Using MediaSessionService gives us, for free and without extra weight:
 *  - a persistent foreground notification with play/pause/next/prev
 *  - standard ACTION_MEDIA_BUTTON handling -> steering wheel / BT remote
 *    buttons "just work" system-side, no custom BroadcastReceiver needed
 *  - proper audio focus handling via ExoPlayer's built-in AudioFocus manager
 * This is the lightest way to get hardware-button support on Android 6+,
 * which is why it's wired in from the start rather than bolted on later.
 */
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var crossfadeController: CrossfadeController? = null
    private var persistence: PlaybackPersistence? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MultiSchemeDataSourceFactory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Effects chain (EQ/preamp/compressor/AGP) attaches to this player's
        // audio session id — see AudioEffectsChain. Published on the
        // in-process bridge too, so the UI's VisualizerEngine can attach
        // without any IPC (see PlaybackServiceBridge).
        AudioEffectsChain.attach(player.audioSessionId)
        PlaybackServiceBridge.setAudioSessionId(player.audioSessionId)
        AudioEffectsChain.restoreFrom(PlayerPreferencesHolder.get(this))

        crossfadeController = CrossfadeController(player, PlayerPreferencesHolder.get(this))

        // Resume where we left off: restore the saved queue/index/position,
        // prepared but paused (never auto-blast audio the instant the car
        // turns on — the person taps play when ready).
        val stateStore = PlaybackStateStore(this)
        stateStore.load()?.let { saved ->
            val mediaItems = saved.items.map {
                MediaItem.Builder()
                    .setUri(Uri.parse(it.uri))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build())
                    .build()
            }
            runCatching {
                player.setMediaItems(mediaItems, saved.index, saved.positionMs)
                player.prepare()
            }
        }
        persistence = PlaybackPersistence(player, stateStore)

        // ExoPlayer can (re)create its audio session when playback starts;
        // keep effects + visualizer glued to whatever id is current.
        val prefsForEffects = PlayerPreferencesHolder.get(this)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                if (audioSessionId > 0) {
                    AudioEffectsChain.attach(audioSessionId)
                    AudioEffectsChain.restoreFrom(prefsForEffects)
                    PlaybackServiceBridge.setAudioSessionId(audioSessionId)
                }
            }
        })

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        AudioEffectsChain.release()
        crossfadeController?.release()
        persistence?.release()
        PlaybackServiceBridge.clear()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
