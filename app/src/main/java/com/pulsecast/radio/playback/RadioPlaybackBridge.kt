package com.pulsecast.radio.playback

import com.pulsecast.radio.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe global bridge coordinating playback actions (station switching, mute/play/pause)
 * between RadioPlaybackService, PlaybackController, MainViewModel and Bluetooth/Automotive controls.
 */
object RadioPlaybackBridge {

    @Volatile
    var onPlayNext: (() -> Unit)? = null

    @Volatile
    var onPlayPrevious: (() -> Unit)? = null

    @Volatile
    var onToggleMute: (() -> Unit)? = null

    @Volatile
    var onStopStream: (() -> Unit)? = null

    @Volatile
    var onStartStream: (() -> Unit)? = null

    private val _currentStationList = MutableStateFlow<List<Station>>(emptyList())
    val currentStationList: StateFlow<List<Station>> = _currentStationList.asStateFlow()

    fun updateStationList(list: List<Station>) {
        _currentStationList.value = list
    }

    private val _currentStation = MutableStateFlow<Station?>(null)
    val currentStation: StateFlow<Station?> = _currentStation.asStateFlow()

    fun updateCurrentStation(station: Station?) {
        _currentStation.value = station
    }
}
