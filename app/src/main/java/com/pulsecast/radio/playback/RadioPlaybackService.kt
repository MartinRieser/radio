package com.pulsecast.radio.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pulsecast.radio.MainActivity
import com.pulsecast.radio.data.CuratedStations
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.model.Station

@OptIn(UnstableApi::class)
class RadioPlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var repository: HistoryAndFavoritesRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    private val muteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action == "android.media.STREAM_MUTE_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    val isMuted = intent.getBooleanExtra("android.media.EXTRA_STREAM_VOLUME_MUTED", false)
                    if (isMuted && player.isPlaying) {
                        stopStream()
                    } else if (!isMuted && !player.isPlaying) {
                        startStream()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = HistoryAndFavoritesRepository(this)

        // Configure ExoPlayer for live radio streaming
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

        // 3. Wrap ExoPlayer in ForwardingPlayer so Bluetooth AVRCP / Car media commands (Next, Prev, Mute)
        // are properly advertised and routed to station switching.
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() {
                playNextStation()
            }

            override fun seekToNextMediaItem() {
                playNextStation()
            }

            override fun seekToPrevious() {
                playPreviousStation()
            }

            override fun seekToPreviousMediaItem() {
                playPreviousStation()
            }
        }

        // 4. Build MediaLibrarySession for Bluetooth AVRCP & Android Auto
        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, CustomMediaLibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        // 5. Listen to ICY live metadata (song title, artist)
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
            }
        })

        // 6. Register mute change listener for vehicle / headset hardware mute events
        val muteFilter = IntentFilter("android.media.STREAM_MUTE_CHANGED_ACTION")
        ContextCompat.registerReceiver(this, muteReceiver, muteFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(muteReceiver) }
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    fun playNextStation() {
        val onNext = RadioPlaybackBridge.onPlayNext
        if (onNext != null) {
            mainHandler.post { onNext.invoke() }
            return
        }

        val list = getAvailableStationList()
        if (list.isEmpty()) return
        val currentMediaId = player.currentMediaItem?.mediaId
        val currentIndex = list.indexOfFirst { it.stationuuid == currentMediaId || it.getStreamUrl() == currentMediaId }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % list.size else 0
        playStationInService(list[nextIndex])
    }

    fun playPreviousStation() {
        val onPrev = RadioPlaybackBridge.onPlayPrevious
        if (onPrev != null) {
            mainHandler.post { onPrev.invoke() }
            return
        }

        val list = getAvailableStationList()
        if (list.isEmpty()) return
        val currentMediaId = player.currentMediaItem?.mediaId
        val currentIndex = list.indexOfFirst { it.stationuuid == currentMediaId || it.getStreamUrl() == currentMediaId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playStationInService(list[prevIndex])
    }

    fun toggleMutePlayStop() {
        val onToggle = RadioPlaybackBridge.onToggleMute
        if (onToggle != null) {
            mainHandler.post { onToggle.invoke() }
            return
        }

        if (player.isPlaying) {
            player.stop()
        } else {
            val station = RadioPlaybackBridge.currentStation.value ?: getAvailableStationList().firstOrNull()
            if (station != null) {
                playStationInService(station)
            } else {
                player.prepare()
                player.play()
            }
        }
    }

    fun stopStream() {
        val onStop = RadioPlaybackBridge.onStopStream
        if (onStop != null) {
            mainHandler.post { onStop.invoke() }
            return
        }
        player.stop()
    }

    fun startStream() {
        val onStart = RadioPlaybackBridge.onStartStream
        if (onStart != null) {
            mainHandler.post { onStart.invoke() }
            return
        }
        val station = RadioPlaybackBridge.currentStation.value ?: getAvailableStationList().firstOrNull()
        if (station != null) {
            playStationInService(station)
        } else {
            player.prepare()
            player.play()
        }
    }

    private fun getAvailableStationList(): List<Station> {
        val bridgeList = RadioPlaybackBridge.currentStationList.value
        if (bridgeList.isNotEmpty()) return bridgeList

        val favs = repository.favorites.value
        if (favs.isNotEmpty()) return favs

        return CuratedStations.load(this)
    }

    private fun playStationInService(station: Station) {
        val mediaItem = buildPlayableItem(station)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        RadioPlaybackBridge.updateCurrentStation(station)
    }

    /**
     * Media Library Callback for Car Screens, Bluetooth AVRCP and System Media Buttons
     */
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .build()
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands,
                availablePlayerCommands
            )
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            playerCommand: @Player.Command Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    playNextStation()
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    playPreviousStation()
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controllerInfo, playerCommand)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            if (keyEvent != null) {
                val action = keyEvent.action
                val keyCode = keyEvent.keyCode
                if (action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            playNextStation()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            playPreviousStation()
                            return true
                        }
                        KeyEvent.KEYCODE_VOLUME_MUTE,
                        KeyEvent.KEYCODE_MUTE -> {
                            toggleMutePlayStop()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            toggleMutePlayStop()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            stopStream()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            startStream()
                            return true
                        }
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_BUTTON_R1,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_BUTTON_L1,
                        KeyEvent.KEYCODE_VOLUME_MUTE,
                        KeyEvent.KEYCODE_MUTE,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK,
                        KeyEvent.KEYCODE_MEDIA_STOP,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            return true
                        }
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

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
                        buildFolderItem("featured", "📻 Entdecken", "Kuratierte Sender"),
                        buildFolderItem("favorites_1", "❤️ Favoriten", "${repository.favorites1.value.size} gespeicherte Sender"),
                        buildFolderItem("favorites_2", "💎 Diamant", "${repository.favorites2.value.size} gespeicherte Sender"),
                        buildFolderItem("favorites_3", "⭐ Stern", "${repository.favorites3.value.size} gespeicherte Sender"),
                        buildFolderItem("adfree", "🛡️ Werbefrei", "Reine Musik ohne Werbung"),
                        buildFolderItem("history", "🕒 Verlauf", "${repository.history.value.size} zuletzt gehörte Sender")
                    )
                }
                "featured" -> {
                    CuratedStations.load(this@RadioPlaybackService).map { buildPlayableItem(it) }
                }
                "favorites_1", "favorites" -> {
                    repository.favorites1.value.map { buildPlayableItem(it) }
                }
                "favorites_2" -> {
                    repository.favorites2.value.map { buildPlayableItem(it) }
                }
                "favorites_3" -> {
                    repository.favorites3.value.map { buildPlayableItem(it) }
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
