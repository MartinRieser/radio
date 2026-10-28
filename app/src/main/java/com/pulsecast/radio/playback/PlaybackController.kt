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
import com.pulsecast.radio.model.Station
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
    private val repository: HistoryAndFavoritesRepository
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

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

    init {
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
                if (playing) {
                    startTimer()
                } else {
                    stopTimer()
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
                        _trackTitle.value = ""
                        _liveSeconds.value = 0
                        RadioPlaybackBridge.updateCurrentStation(matching)
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val station = _currentStation.value
                val rawTitle = mediaMetadata.title?.toString()
                val formatted = RadioMetadataFormatter.format(rawTitle, station)
                _trackTitle.value = formatted.appTrackTitle
            }
        })
    }

    fun playStation(station: Station) {
        val controller = mediaController ?: return
        val streamUrl = station.getStreamUrl()
        if (streamUrl.isBlank()) return

        _currentStation.value = station
        RadioPlaybackBridge.updateCurrentStation(station)
        _trackTitle.value = ""
        _liveSeconds.value = 0

        val formatted = RadioMetadataFormatter.format(null, station)
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(formatted.title)
            .setArtist(formatted.artist)
            .setAlbumTitle(formatted.album)
            .apply {
                if (!station.favicon.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(station.favicon))
                }
            }
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
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun release() {
        if (RadioPlaybackBridge.onToggleMute != null) {
            RadioPlaybackBridge.onToggleMute = null
            RadioPlaybackBridge.onStopStream = null
            RadioPlaybackBridge.onStartStream = null
        }
        stopTimer()
        MediaController.releaseFuture(controllerFuture ?: return)
    }
}
