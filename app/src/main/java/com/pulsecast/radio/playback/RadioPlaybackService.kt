package com.pulsecast.radio.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pulsecast.radio.MainActivity
import com.pulsecast.radio.PulseCastApp
import com.pulsecast.radio.R
import com.pulsecast.radio.data.CuratedStations
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.model.Station

@OptIn(UnstableApi::class)
class RadioPlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var repository: HistoryAndFavoritesRepository
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentPlayingStation: Station? = null
    private var currentFormattedMetadata: MediaMetadata? = null

    // Service-level WakeLock & WifiLock to prevent CPU/network sleep in phone standby
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Tracks whether live radio should actively be playing (for standby auto-reconnect)
    @Volatile
    private var shouldBePlaying = false
    private var reconnectAttempts = 0
    private var wasMutedByReceiver = false

    private val reconnectRunnable = Runnable {
        if (shouldBePlaying && !player.isPlaying) {
            Log.d("RadioPlaybackService", "Auto-reconnecting live stream (attempt $reconnectAttempts)...")
            if (player.currentMediaItem != null) {
                player.prepare()
                player.play()
            } else {
                val station = currentPlayingStation ?: RadioPlaybackBridge.currentStation.value
                if (station != null) {
                    playStationInService(station)
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                if (shouldBePlaying && (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED)) {
                    Log.d("RadioPlaybackService", "Network restored in standby, reconnecting stream immediately")
                    reconnectAttempts = 0
                    mainHandler.removeCallbacks(reconnectRunnable)
                    mainHandler.post(reconnectRunnable)
                }
            }
        }
    }

    private val muteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action == "android.media.STREAM_MUTE_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    val isMuted = intent.getBooleanExtra("android.media.EXTRA_STREAM_VOLUME_MUTED", false)
                    if (isMuted && player.isPlaying) {
                        wasMutedByReceiver = true
                        stopStream()
                    } else if (!isMuted && wasMutedByReceiver && !player.isPlaying) {
                        wasMutedByReceiver = false
                        startStream()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = HistoryAndFavoritesRepository(this)
        initStandbyLocks()

        // Configure notification provider so Android promotes the service to Foreground immediately
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(PulseCastApp.CHANNEL_ID)
            .setChannelName(R.string.channel_name)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        setMediaNotificationProvider(notificationProvider)

        // Resilient HTTP data source & load control for uninterrupted standby streaming
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("PulseCastRadio/1.2 (Android)")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(10))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .build()

        // Configure ExoPlayer for live radio streaming
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
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
        // are properly advertised and routed to station switching, and ICY metadata is formatted for car screens.
        val forwardingPlayer = object : ForwardingPlayer(player) {
            private val listenerMap = mutableMapOf<Player.Listener, Player.Listener>()

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

            override fun play() {
                shouldBePlaying = true
                reconnectAttempts = 0
                mainHandler.removeCallbacks(reconnectRunnable)
                acquireStandbyLocks()
                if (player.playbackState == Player.STATE_IDLE && player.currentMediaItem != null) {
                    player.prepare()
                }
                super.play()
            }

            override fun pause() {
                shouldBePlaying = false
                mainHandler.removeCallbacks(reconnectRunnable)
                releaseStandbyLocks()
                super.stop() // Stop stream immediately to prevent stream buffering in background
            }

            override fun stop() {
                shouldBePlaying = false
                mainHandler.removeCallbacks(reconnectRunnable)
                releaseStandbyLocks()
                super.stop()
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

            override fun addListener(listener: Player.Listener) {
                val wrapped = MetadataFormattingPlayerListener(listener)
                listenerMap[listener] = wrapped
                super.addListener(wrapped)
            }

            override fun removeListener(listener: Player.Listener) {
                val wrapped = listenerMap.remove(listener) ?: listener
                super.removeListener(wrapped)
            }

            override fun getMediaMetadata(): MediaMetadata {
                val activeStation = currentPlayingStation ?: RadioPlaybackBridge.currentStation.value
                return currentFormattedMetadata ?: buildFormattedMediaMetadata(super.getMediaMetadata(), activeStation)
            }
        }

        // 4. Build MediaLibrarySession for Bluetooth AVRCP & Android Auto
        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, CustomMediaLibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(CoilBitmapLoader(this))
            .build()

        // 5. Listen to ICY live metadata, playback state, and stream errors for standby resilience
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                val activeStation = currentPlayingStation ?: RadioPlaybackBridge.currentStation.value
                currentFormattedMetadata = buildFormattedMediaMetadata(mediaMetadata, activeStation)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    reconnectAttempts = 0
                    mainHandler.removeCallbacks(reconnectRunnable)
                    acquireStandbyLocks()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && (
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                    )
                ) {
                    shouldBePlaying = false
                    mainHandler.removeCallbacks(reconnectRunnable)
                    releaseStandbyLocks()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && shouldBePlaying) {
                    scheduleStreamReconnect()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("RadioPlaybackService", "Stream playback error in service: ${error.message}")
                if (shouldBePlaying) {
                    scheduleStreamReconnect()
                }
            }
        })

        // 6. Register mute change listener for vehicle / headset hardware mute events
        val muteFilter = IntentFilter("android.media.STREAM_MUTE_CHANGED_ACTION")
        ContextCompat.registerReceiver(this, muteReceiver, muteFilter, ContextCompat.RECEIVER_EXPORTED)

        // 7. Register network callback to immediately resume stream after standby network handoffs
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(request, networkCallback)
        }
    }

    /**
     * Explicit Player.Listener wrapper that forwards ALL Player.Listener methods (including
     * onEvents, onPlaybackStateChanged, onIsPlayingChanged, etc.) so Media3's MediaSession
     * and MediaNotificationManager properly promote RadioPlaybackService to a Foreground Service.
     */
    private inner class MetadataFormattingPlayerListener(
        private val delegate: Player.Listener
    ) : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = delegate.onEvents(player, events)
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = delegate.onTimelineChanged(timeline, reason)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = delegate.onMediaItemTransition(mediaItem, reason)
        override fun onTracksChanged(tracks: Tracks) = delegate.onTracksChanged(tracks)
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val activeStation = currentPlayingStation ?: RadioPlaybackBridge.currentStation.value
            val formatted = buildFormattedMediaMetadata(mediaMetadata, activeStation)
            currentFormattedMetadata = formatted
            delegate.onMediaMetadataChanged(formatted)
        }
        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = delegate.onPlaylistMetadataChanged(mediaMetadata)
        override fun onIsLoadingChanged(isLoading: Boolean) = delegate.onIsLoadingChanged(isLoading)
        @Suppress("DEPRECATION")
        override fun onLoadingChanged(isLoading: Boolean) = delegate.onLoadingChanged(isLoading)
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = delegate.onAvailableCommandsChanged(availableCommands)
        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = delegate.onTrackSelectionParametersChanged(parameters)
        @Suppress("DEPRECATION")
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = delegate.onPlayerStateChanged(playWhenReady, playbackState)
        override fun onPlaybackStateChanged(playbackState: Int) = delegate.onPlaybackStateChanged(playbackState)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = delegate.onPlayWhenReadyChanged(playWhenReady, reason)
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = delegate.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        override fun onIsPlayingChanged(isPlaying: Boolean) = delegate.onIsPlayingChanged(isPlaying)
        override fun onRepeatModeChanged(repeatMode: Int) = delegate.onRepeatModeChanged(repeatMode)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = delegate.onShuffleModeEnabledChanged(shuffleModeEnabled)
        override fun onPlayerError(error: PlaybackException) = delegate.onPlayerError(error)
        override fun onPlayerErrorChanged(error: PlaybackException?) = delegate.onPlayerErrorChanged(error)
        @Suppress("DEPRECATION")
        override fun onPositionDiscontinuity(reason: Int) = delegate.onPositionDiscontinuity(reason)
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
            delegate.onPositionDiscontinuity(oldPosition, newPosition, reason)
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = delegate.onPlaybackParametersChanged(playbackParameters)
        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = delegate.onSeekBackIncrementChanged(seekBackIncrementMs)
        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = delegate.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) = delegate.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        override fun onVideoSizeChanged(videoSize: VideoSize) = delegate.onVideoSizeChanged(videoSize)
        override fun onSurfaceSizeChanged(width: Int, height: Int) = delegate.onSurfaceSizeChanged(width, height)
        override fun onRenderedFirstFrame() = delegate.onRenderedFirstFrame()
        override fun onAudioSessionIdChanged(audioSessionId: Int) = delegate.onAudioSessionIdChanged(audioSessionId)
        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) = delegate.onAudioAttributesChanged(audioAttributes)
        override fun onVolumeChanged(volume: Float) = delegate.onVolumeChanged(volume)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = delegate.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        @Suppress("DEPRECATION")
        override fun onCues(cues: MutableList<Cue>) = delegate.onCues(cues)
        override fun onCues(cueGroup: CueGroup) = delegate.onCues(cueGroup)
        override fun onMetadata(metadata: Metadata) = delegate.onMetadata(metadata)
        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = delegate.onDeviceInfoChanged(deviceInfo)
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = delegate.onDeviceVolumeChanged(volume, muted)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is MetadataFormattingPlayerListener) return delegate == other.delegate
            return delegate == other
        }

        override fun hashCode(): Int = delegate.hashCode()
    }

    private fun initStandbyLocks() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PulseCast:RadioPlaybackWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        runCatching {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(lockType, "PulseCast:RadioPlaybackWifiLock")?.apply {
                setReferenceCounted(false)
            }
        }
    }

    private fun acquireStandbyLocks() {
        runCatching {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        }
        runCatching {
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseStandbyLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        }
    }

    private fun scheduleStreamReconnect() {
        if (!shouldBePlaying) return
        acquireStandbyLocks()
        mainHandler.removeCallbacks(reconnectRunnable)
        val delayMs = (2_000L * (reconnectAttempts + 1)).coerceAtMost(10_000L)
        reconnectAttempts++
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val isActivelyPlaying = shouldBePlaying || player.playWhenReady || player.isPlaying
        if (!isActivelyPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        shouldBePlaying = false
        mainHandler.removeCallbacks(reconnectRunnable)
        releaseStandbyLocks()
        runCatching { unregisterReceiver(muteReceiver) }
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(networkCallback)
        }
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
        val currentStation = RadioPlaybackBridge.currentStation.value
        val currentMediaId = player.currentMediaItem?.mediaId
        val currentIndex = list.indexOfFirst {
            (currentStation != null && it.isSameStation(currentStation)) || it.matchesIdOrUrl(currentMediaId)
        }
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
        val currentStation = RadioPlaybackBridge.currentStation.value
        val currentMediaId = player.currentMediaItem?.mediaId
        val currentIndex = list.indexOfFirst {
            (currentStation != null && it.isSameStation(currentStation)) || it.matchesIdOrUrl(currentMediaId)
        }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playStationInService(list[prevIndex])
    }

    fun toggleMutePlayStop() {
        val onMute = RadioPlaybackBridge.onToggleMute
        if (onMute != null) {
            mainHandler.post { onMute.invoke() }
            return
        }

        if (player.isPlaying) {
            shouldBePlaying = false
            mainHandler.removeCallbacks(reconnectRunnable)
            releaseStandbyLocks()
            player.stop()
        } else {
            val station = RadioPlaybackBridge.currentStation.value ?: getAvailableStationList().firstOrNull()
            if (station != null) {
                playStationInService(station)
            } else {
                shouldBePlaying = true
                acquireStandbyLocks()
                player.prepare()
                player.play()
            }
        }
    }

    fun stopStream() {
        shouldBePlaying = false
        mainHandler.removeCallbacks(reconnectRunnable)
        releaseStandbyLocks()
        val onStop = RadioPlaybackBridge.onStopStream
        if (onStop != null) {
            mainHandler.post { onStop.invoke() }
            return
        }
        player.stop()
    }

    fun startStream() {
        shouldBePlaying = true
        acquireStandbyLocks()
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
        currentPlayingStation = station
        shouldBePlaying = true
        reconnectAttempts = 0
        mainHandler.removeCallbacks(reconnectRunnable)
        acquireStandbyLocks()
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
                        buildFolderItem("favorites_1", "❤️ Hart", "${repository.favorites1.value.size} gespeicherte Sender"),
                        buildFolderItem("favorites_2", "💎 Diamant", "${repository.favorites2.value.size} gespeicherte Sender"),
                        buildFolderItem("favorites_3", "⭐ Stern", "${repository.favorites3.value.size} gespeicherte Sender")
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
                else -> emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val allKnown = RadioPlaybackBridge.currentStationList.value +
                repository.allFavorites.value +
                CuratedStations.load(this@RadioPlaybackService)
            val station = allKnown.firstOrNull { it.matchesIdOrUrl(mediaId) }
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
            val allKnown = listOfNotNull(RadioPlaybackBridge.currentStation.value) +
                RadioPlaybackBridge.currentStationList.value +
                repository.allFavorites.value +
                CuratedStations.load(this@RadioPlaybackService)

            val updatedItems = mediaItems.map { item ->
                val requestUri = item.requestMetadata.mediaUri?.toString()
                val requestMediaId = item.mediaId

                val matchingStation = allKnown.firstOrNull {
                    it.matchesIdOrUrl(requestMediaId) || (requestUri != null && it.matchesIdOrUrl(requestUri))
                }

                val candidateUrl = requestUri?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: matchingStation?.getStreamUrl()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: requestMediaId.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: "https://orf-live.ors-shoutcast.at/fm4-q2a"

                val finalUri = Uri.parse(candidateUrl)

                val stationName = matchingStation?.name ?: item.mediaMetadata.title?.toString() ?: "PulseCast Radio"
                val stationCountry = matchingStation?.country ?: "Live Radio"

                val metadata = item.mediaMetadata.buildUpon()
                    .setTitle(stationName)
                    .setArtist(stationCountry)
                    .apply {
                        if (matchingStation != null) {
                            currentPlayingStation = matchingStation
                            val formatted = RadioMetadataFormatter.format(item.mediaMetadata.title?.toString(), matchingStation)
                            setTitle(formatted.title)
                            setArtist(formatted.artist)
                            setAlbumTitle(formatted.album)
                            setSubtitle(matchingStation.tags)
                            if (!matchingStation.favicon.isNullOrBlank()) {
                                setArtworkUri(Uri.parse(matchingStation.favicon))
                            }
                        }
                    }
                    .setIsPlayable(true)
                    .build()

                item.buildUpon()
                    .setUri(finalUri)
                    .setRequestMetadata(
                        item.requestMetadata.buildUpon()
                            .setMediaUri(finalUri)
                            .build()
                    )
                    .setMediaMetadata(metadata)
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

    private fun buildFormattedMediaMetadata(rawMetadata: MediaMetadata, station: Station?): MediaMetadata {
        val rawTitle = rawMetadata.title?.toString()
        val formatted = RadioMetadataFormatter.format(rawTitle, station)
        return rawMetadata.buildUpon()
            .setTitle(formatted.title)
            .setArtist(formatted.artist)
            .setAlbumTitle(formatted.album)
            .build()
    }
}
