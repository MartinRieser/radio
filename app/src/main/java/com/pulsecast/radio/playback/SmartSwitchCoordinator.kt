package com.pulsecast.radio.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pulsecast.radio.data.CuratedStations
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.data.StationIntelligenceRepository
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.playback.audio.AudioBridge
import com.pulsecast.radio.playback.audio.AudioContentClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class SmartSwitchStatus {
    object Disabled : SmartSwitchStatus()
    object Music : SmartSwitchStatus()
    data class SpeechDetected(val elapsedSeconds: Int, val maxSeconds: Int) : SmartSwitchStatus()
    data class Switched(val originalStation: Station) : SmartSwitchStatus()
}

@OptIn(UnstableApi::class)
class SmartSwitchCoordinator(
    private val context: Context,
    private val repository: HistoryAndFavoritesRepository,
    private val intelligenceRepository: StationIntelligenceRepository,
    private val audioClassifier: AudioContentClassifier,
    private val onSwitchStationRequest: (Station) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var monitoringJob: Job? = null

    private var currentStation: Station? = null
    private var isAudiblePlaying: Boolean = false

    private val _status = MutableStateFlow<SmartSwitchStatus>(SmartSwitchStatus.Disabled)
    val status: StateFlow<SmartSwitchStatus> = _status.asStateFlow()

    private val _speechSeconds = MutableStateFlow(0)
    val speechSeconds: StateFlow<Int> = _speechSeconds.asStateFlow()

    private val _originalSwitchedStation = MutableStateFlow<Station?>(null)
    val originalSwitchedStation: StateFlow<Station?> = _originalSwitchedStation.asStateFlow()

    private var consecutiveSpeechSeconds: Int = 0
    private var consecutiveMusicSeconds: Int = 0
    private var lastSwitchTimeMs: Long = 0L

    // Set of station keys (stationuuid / streamUrl) for which speech detection should be ignored
    private val ignoredSpeechStationKeys = mutableSetOf<String>()

    // Sliding history buffer of recently used fallback stations
    private val recentFallbackKeys = mutableListOf<String>()

    private fun markFallbackUsed(station: Station) {
        val key = station.stationuuid?.takeIf { it.isNotBlank() } ?: station.getStreamUrl()
        recentFallbackKeys.remove(key)
        recentFallbackKeys.add(0, key)
        if (recentFallbackKeys.size > 5) {
            recentFallbackKeys.removeAt(recentFallbackKeys.size - 1)
        }
    }

    fun onCurrentStationChanged(station: Station?) {
        currentStation = station
        consecutiveSpeechSeconds = 0
        consecutiveMusicSeconds = 0
        _speechSeconds.value = 0
        audioClassifier.reset()
        lastSwitchTimeMs = System.currentTimeMillis()

        if (station != null) {
            audioClassifier.setStationContext(
                trackTitle = "",
                isAdFree = station.isAdFree()
            )
            // If user manually switched away to a station that is NOT originalSwitchedStation,
            // clear originalSwitchedStation
            val origKey = _originalSwitchedStation.value?.let { it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl() }
            val newKey = station.stationuuid?.takeIf { u -> u.isNotBlank() } ?: station.getStreamUrl()
            if (origKey != null && origKey != newKey) {
                _originalSwitchedStation.value = null
            }
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        isAudiblePlaying = isPlaying
        if (isPlaying) {
            startMonitoring()
        } else {
            stopMonitoring()
        }
    }

    fun onMediaMetadataUpdated(trackTitle: String) {
        val station = currentStation ?: return
        audioClassifier.setStationContext(
            trackTitle = trackTitle,
            isAdFree = station.isAdFree()
        )
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (!isAudiblePlaying || currentStation == null) continue

                val station = currentStation ?: continue
                val isSmartSwitchOn = intelligenceRepository.isSmartSwitchEnabled.value

                if (!isSmartSwitchOn) {
                    _status.value = SmartSwitchStatus.Disabled
                    consecutiveSpeechSeconds = 0
                    _speechSeconds.value = 0
                    continue
                }

                // 15-second Post-Switch Immunity Grace Period
                val timeSinceSwitchMs = System.currentTimeMillis() - lastSwitchTimeMs
                val isInGracePeriod = timeSinceSwitchMs < 15_000L

                if (isInGracePeriod) {
                    consecutiveSpeechSeconds = 0
                    _speechSeconds.value = 0
                    val remainingSecs = ((15_000 - timeSinceSwitchMs) / 1000).coerceAtLeast(1)
                    Log.d("PulseCastSmart", "Station: '${station.name}' | Grace Period Active (${remainingSecs}s remaining)")

                    val orig = _originalSwitchedStation.value
                    if (orig != null) {
                        _status.value = SmartSwitchStatus.Switched(orig)
                    } else {
                        _status.value = SmartSwitchStatus.Music
                    }
                    continue
                }

                val stationKey = station.stationuuid?.takeIf { it.isNotBlank() } ?: station.getStreamUrl()
                val isSpeechIgnoredForStation = ignoredSpeechStationKeys.contains(stationKey)

                val minuteOfHour = Calendar.getInstance().get(Calendar.MINUTE)
                val stationPrior = intelligenceRepository.getSpeechProbabilityAtMinute(station, minuteOfHour)

                audioClassifier.sensitivityThreshold = intelligenceRepository.speechSensitivity.value
                audioClassifier.stationMinutePrior = stationPrior

                val isSpeech = audioClassifier.isSpeechDetected
                val isPureSpeechOrAd = audioClassifier.isPureSpeech || audioClassifier.hasAdMetadata
                val isJingleMatched = AudioBridge.signalCollector?.isJingleMatched == true
                val isTopOfHourNewsWindow = (minuteOfHour in 58..59 || minuteOfHour in 0..3) && stationPrior >= 0.20f

                intelligenceRepository.recordSpeechSample(station, minuteOfHour, isSpeech)

                val shouldTriggerSpeech = isSpeech || isPureSpeechOrAd || isJingleMatched || isTopOfHourNewsWindow

                Log.d(
                    "PulseCastSmart",
                    "Station: '${station.name}' | isSpeech: $isSpeech | isPureOrAd: $isPureSpeechOrAd | isNewsWin: $isTopOfHourNewsWindow | Ignored: $isSpeechIgnoredForStation | SpeechSecs: $consecutiveSpeechSeconds | Reason: ${audioClassifier.detectionReason}"
                )

                if (!isSpeechIgnoredForStation && shouldTriggerSpeech) {
                    consecutiveSpeechSeconds += if (isJingleMatched || isPureSpeechOrAd || isTopOfHourNewsWindow) 2 else 1
                    consecutiveMusicSeconds = 0
                    _speechSeconds.value = consecutiveSpeechSeconds

                    val baseTolerance = intelligenceRepository.speechToleranceSeconds.value
                    val requiredTolerance = when {
                        isPureSpeechOrAd || isTopOfHourNewsWindow || isJingleMatched -> 2 // Fast switch (2 seconds) on pure news/ads
                        else -> baseTolerance // Standard tolerance for DJ comments
                    }

                    Log.d("PulseCastSmart", "--> SPEECH ACTIVE: $consecutiveSpeechSeconds / ${requiredTolerance}s on '${station.name}'")

                    if (consecutiveSpeechSeconds >= requiredTolerance) {
                        Log.i("PulseCastSmart", "===> TRIGGERING SMART SWITCH away from '${station.name}'!")
                        triggerSmartSwitch()
                        consecutiveSpeechSeconds = 0
                        consecutiveMusicSeconds = 0
                        _speechSeconds.value = 0
                    } else {
                        _status.value = SmartSwitchStatus.SpeechDetected(
                            elapsedSeconds = consecutiveSpeechSeconds,
                            maxSeconds = requiredTolerance
                        )
                    }
                } else {
                    consecutiveMusicSeconds++
                    val holdSeconds = intelligenceRepository.speechResetHoldSeconds.value
                    if (consecutiveMusicSeconds >= holdSeconds) {
                        consecutiveSpeechSeconds = 0
                        _speechSeconds.value = 0
                    }

                    val orig = _originalSwitchedStation.value
                    if (orig != null) {
                        _status.value = SmartSwitchStatus.Switched(orig)
                    } else {
                        _status.value = SmartSwitchStatus.Music
                    }
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        consecutiveSpeechSeconds = 0
        _speechSeconds.value = 0
    }

    private fun triggerSmartSwitch() {
        val current = currentStation ?: return
        scope.launch {
            val candidate = findBestCandidateStation(current) ?: return@launch

            // Save original station if not already in switched state
            if (_originalSwitchedStation.value == null) {
                _originalSwitchedStation.value = current
            }

            markFallbackUsed(candidate)
            _status.value = SmartSwitchStatus.Switched(_originalSwitchedStation.value ?: current)
            onSwitchStationRequest(candidate)
        }
    }

    fun returnToOriginalStationAndIgnoreSpeech() {
        val orig = _originalSwitchedStation.value ?: return
        val current = currentStation ?: return

        // Ignore speech on the current station that caused the switch or was return-target
        val currentKey = current.stationuuid?.takeIf { it.isNotBlank() } ?: current.getStreamUrl()
        ignoredSpeechStationKeys.add(currentKey)

        _originalSwitchedStation.value = null
        onSwitchStationRequest(orig)
    }

    private fun findBestCandidateStation(current: Station): Station? {
        val currentKey = current.stationuuid?.takeIf { it.isNotBlank() } ?: current.getStreamUrl()

        // 1. Favorites list first
        val favs = repository.favorites.value
        val validFavs = favs.filter {
            val k = it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl()
            k != currentKey && !recentFallbackKeys.contains(k)
        }
        if (validFavs.isNotEmpty()) {
            return validFavs.random()
        }

        // 2. Curated Catalogue
        val curated = CuratedStations.load(context)
        val validCurated = curated.filter {
            val k = it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl()
            k != currentKey && !recentFallbackKeys.contains(k)
        }
        if (validCurated.isNotEmpty()) {
            return validCurated.random()
        }

        return curated.firstOrNull {
            val k = it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl()
            k != currentKey
        }
    }

    fun release() {
        stopMonitoring()
    }
}
