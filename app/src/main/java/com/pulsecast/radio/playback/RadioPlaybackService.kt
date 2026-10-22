package com.pulsecast.radio.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pulsecast.radio.MainActivity
import com.pulsecast.radio.data.CuratedStations
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.playback.audio.AudioBridge
import com.pulsecast.radio.playback.audio.PcmTapAudioProcessor

@OptIn(UnstableApi::class)
class RadioPlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var repository: HistoryAndFavoritesRepository

    override fun onCreate() {
        super.onCreate()
        repository = HistoryAndFavoritesRepository(this)

        // 1. Configure PCM Audio Tap for Speech / Commercial Detection & Signal Mining
        val pcmTap = PcmTapAudioProcessor { buffer, sampleRate, channelCount ->
            AudioBridge.classifier?.processPcmBuffer(buffer, sampleRate, channelCount)
            AudioBridge.signalCollector?.processPcmBuffer(buffer, sampleRate, channelCount)
        }

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(pcmTap))
                    .build()
            }
        }

        // Configure ExoPlayer for live radio streaming
        player = ExoPlayer.Builder(this, renderersFactory)
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

        // 3. Build MediaLibrarySession for Android Auto & Automotive dashboards
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, CustomMediaLibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        // 4. Listen to ICY live metadata (song title, artist)
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    /**
     * Android Auto Media Library Callback
     * Exposes root categories & stations directly to car screens
     */
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = buildFolderItem("root", "PulseCast Radio", "Radio & Streams")
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = when (parentId) {
                "root" -> {
                    listOf(
                        buildFolderItem("smart_radio", "🧠 Smart Radio", "Trainierte Sender"),
                        buildFolderItem("adfree", "🛡️ Werbefrei", "Reine Musik ohne Werbung"),
                        buildFolderItem("favorites", "❤️ Favoriten", "${repository.favorites.value.size} gespeicherte Sender"),
                        buildFolderItem("history", "🕒 Verlauf", "${repository.history.value.size}zuletzt gehörte Sender")
                    )
                }
                "smart_radio" -> {
                    CuratedStations.load(this@RadioPlaybackService).map { buildPlayableItem(it) }
                }
                "adfree" -> {
                    CuratedStations.load(this@RadioPlaybackService).filter { it.isAdFree() }.map { buildPlayableItem(it) }
                }
                "favorites" -> {
                    repository.favorites.value.map { buildPlayableItem(it) }
                }
                "history" -> {
                    repository.history.value.map { buildPlayableItem(it) }
                }
                else -> emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val stations = CuratedStations.load(this@RadioPlaybackService)
            val station = stations.firstOrNull { it.stationuuid == mediaId || it.getStreamUrl() == mediaId }
            return if (station != null) {
                Futures.immediateFuture(LibraryResult.ofItem(buildPlayableItem(station), null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val stations = CuratedStations.load(this@RadioPlaybackService)
            val updatedItems = mediaItems.map { item ->
                val matchingStation = stations.firstOrNull {
                    it.stationuuid == item.mediaId || it.getStreamUrl() == item.mediaId || it.getStreamUrl() == item.localConfiguration?.uri?.toString()
                }

                val streamUrl = matchingStation?.getStreamUrl()
                    ?: item.localConfiguration?.uri?.toString()
                    ?: item.requestMetadata.mediaUri?.toString()
                    ?: item.mediaId

                val uri = runCatching { Uri.parse(streamUrl) }.getOrNull()

                val metadata = item.mediaMetadata.buildUpon()
                    .apply {
                        if (matchingStation != null) {
                            setTitle(matchingStation.name)
                            setArtist(matchingStation.country ?: "Live Radio")
                            setSubtitle(matchingStation.tags)
                            if (!matchingStation.favicon.isNullOrBlank()) {
                                setArtworkUri(Uri.parse(matchingStation.favicon))
                            }
                        }
                    }
                    .setIsPlayable(true)
                    .build()

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
                        setMediaMetadata(metadata)
                    }
                    .build()
            }.toMutableList()
            return Futures.immediateFuture(updatedItems)
        }
    }

    private fun buildFolderItem(id: String, title: String, subtitle: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setFolderType(MediaMetadata.FOLDER_TYPE_TITLES)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildPlayableItem(station: Station): MediaItem {
        val streamUrl = station.getStreamUrl()
        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(station.country ?: "Live Radio")
            .setSubtitle(station.tags)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                if (!station.favicon.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(station.favicon))
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(station.stationuuid ?: streamUrl)
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(metadata)
            .build()
    }
}
