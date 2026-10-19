package com.pulsecast.radio.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pulsecast.radio.MainActivity

@UnstableApi
class RadioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        // 1. Configure ExoPlayer for live radio streaming
        player = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_NETWORK) // Prevent CPU/WiFi sleep
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Automatic audio focus handling (pause on incoming phone call, duck on navigation)
            )
            .setHandleAudioBecomingNoisy(true) // Pause automatically when headphones are unplugged
            .build()

        // 2. Open MainActivity on notification click
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Build MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomMediaSessionCallback())
            .build()

        // 4. Listen to ICY live metadata (song title, artist)
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // ICY stream headers update title & artist automatically
                super.onMediaMetadataChanged(mediaMetadata)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map { item ->
                val uri = item.localConfiguration?.uri
                    ?: item.requestMetadata.mediaUri
                    ?: runCatching { Uri.parse(item.mediaId) }.getOrNull()

                item.buildUpon()
                    .apply {
                        if (uri != null) {
                            setUri(uri)
                            setRequestMetadata(
                                item.requestMetadata.buildUpon()
                                    .setMediaUri(uri)
                                    .build()
                            )
                        }
                    }
                    .build()
            }.toMutableList()
            return Futures.immediateFuture(updatedItems)
        }
    }
}
