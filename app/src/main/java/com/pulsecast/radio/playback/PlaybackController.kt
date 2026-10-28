package com.pulsecast.radio.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pulsecast.radio.data.CuratedStations
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.data.StationIntelligenceRepository
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.playback.audio.AudioBridge
import com.pulsecast.radio.playback.audio.AudioContentClassifier
import com.pulsecast.radio.playback.audio.StationSignalCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackController(
    private val context: Context,
    private val repository: HistoryAndFavoritesRepository,
    val intelligenceRepository: StationIntelligenceRepository = StationIntelligenceRepository(context)
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    val audioClassifier = AudioContentClassifier(context)
    val signalCollector = StationSignalCollector(intelligenceRepository)
    val smartSwitchCoordinator = SmartSwitchCoordinator(
        context = context,
        repository = repository,
        intelligenceRepository = intelligenceRepository,
        audioClassifier = audioClassifier,
        onSwitchStationRequest = { targetStation ->
            playStation(targetStation)
        }
    )

    private val _currentStation = MutableStateFlow<Station?>(null)
    val currentStation: StateFlow<Station?> = _currentStation.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _liveSeconds = MutableStateFlow(0)
    val liveSeconds: StateFlow<Int> = _liveSeconds.asStateFlow()

    private val _trackTitle = MutableStateFlow("")
    val trackTitle: StateFlow<String> = _trackTitle.asStateFlow()

    // Listening duration tracking for > 1 minute rule
    private var stationListeningSeconds = 0
    private var stationHistorySaved = false

    init {
        AudioBridge.classifier = audioClassifier
        AudioBridge.signalCollector = signalCollector

        // Wire playback bridge for Bluetooth / Car controls
        RadioPlaybackBridge.onToggleMute = { togglePlayPause() }
        RadioPlaybackBridge.onStopStream = { stop() }
        RadioPlaybackBridge.onStartStream = {
            val current = _currentStation.value ?: RadioPlaybackBridge.currentStationList.value.firstOrNull()
            if (current != null) {
                playStation(current)
            }
        }

        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                smartSwitchCoordinator.onPlaybackStateChanged(playing)
                if (playing) {
                    startTimer()
                } else {
                    stopTimer()
                    finalizeHistory()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    _isBuffering.value = false
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val mediaId = mediaItem?.mediaId ?: return
                val current = _currentStation.value
                if (current?.stationuuid != mediaId && current?.getStreamUrl() != mediaId) {
                    val matching = RadioPlaybackBridge.currentStationList.value.firstOrNull {
                        it.stationuuid == mediaId || it.getStreamUrl() == mediaId
                    } ?: repository.favorites.value.firstOrNull {
                        it.stationuuid == mediaId || it.getStreamUrl() == mediaId
                    } ?: CuratedStations.load(context).firstOrNull {
                        it.stationuuid == mediaId || it.getStreamUrl() == mediaId
                    }
                    if (matching != null) {
                        _currentStation.value = matching
                        smartSwitchCoordinator.onCurrentStationChanged(matching)
                        signalCollector.onStationChanged(matching)
                        _trackTitle.value = ""
                        _liveSeconds.value = 0
                        stationListeningSeconds = 0
                        stationHistorySaved = false
                        RadioPlaybackBridge.updateCurrentStation(matching)
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title = mediaMetadata.title?.toString() ?: ""
                val artist = mediaMetadata.artist?.toString() ?: ""
                val formatted = when {
                    artist.isNotBlank() && title.isNotBlank() -> "$artist - $title"
                    title.isNotBlank() -> title
                    artist.isNotBlank() -> artist
                    else -> ""
                }
                _trackTitle.value = formatted
                smartSwitchCoordinator.onMediaMetadataUpdated(formatted)
            }
        })
    }

    fun playStation(station: Station) {
        val controller = mediaController ?: return
        val streamUrl = station.getStreamUrl()
        if (streamUrl.isBlank()) return

        // Finalize previous station if >= 60s
        finalizeHistory()

        _currentStation.value = station
        RadioPlaybackBridge.updateCurrentStation(station)
        smartSwitchCoordinator.onCurrentStationChanged(station)
        signalCollector.onStationChanged(station)
        _trackTitle.value = ""
        _liveSeconds.value = 0
        stationListeningSeconds = 0
        stationHistorySaved = false

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(station.country ?: "Live Radio")
            .setArtworkUri(station.favicon?.let { Uri.parse(it) })
            .build()

        val parsedUri = Uri.parse(streamUrl)
        val mediaItem = MediaItem.Builder()
            .setUri(parsedUri)
            .setMediaId(station.stationuuid ?: streamUrl)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(parsedUri)
                    .build()
            )
            .setMediaMetadata(mediaMetadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    /**
     * Toggles live streaming play/stop:
     * When active, completely stops the live stream to save mobile data.
     * When stopped/paused, reconnects and starts fresh on the live radio edge.
     */
    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            finalizeHistory()
            controller.stop()
            _isPlaying.value = false
            stopTimer()
        } else {
            val station = _currentStation.value ?: RadioPlaybackBridge.currentStationList.value.firstOrNull()
            if (station != null) {
                playStation(station)
            } else {
                controller.prepare()
                controller.play()
            }
        }
    }

    fun stop() {
        finalizeHistory()
        mediaController?.stop()
        _isPlaying.value = false
        stopTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _liveSeconds.value++
                stationListeningSeconds++

                // STRICT RULE: Record to history when reaching 60 seconds (1 minute)
                val current = _currentStation.value
                if (stationListeningSeconds >= 60 && !stationHistorySaved && current != null) {
                    repository.addToHistory(current, stationListeningSeconds)
                    stationHistorySaved = true
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun finalizeHistory() {
        val current = _currentStation.value
        if (current != null && stationListeningSeconds >= 60 && !stationHistorySaved) {
            repository.addToHistory(current, stationListeningSeconds)
            stationHistorySaved = true
        }
    }

    fun release() {
        if (RadioPlaybackBridge.onToggleMute != null) {
            RadioPlaybackBridge.onToggleMute = null
            RadioPlaybackBridge.onStopStream = null
            RadioPlaybackBridge.onStartStream = null
        }
        smartSwitchCoordinator.release()
        finalizeHistory()
        stopTimer()
        MediaController.releaseFuture(controllerFuture ?: return)
    }
}
