package com.pulsecast.radio.playback

import android.content.Context
import android.net.Uri
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
    object Idle : SmartSwitchStatus()
    data class Monitoring(val reason: String) : SmartSwitchStatus()
    data class SpeechTolerance(val elapsedSeconds: Int, val maxSeconds: Int) : SmartSwitchStatus()
    data class ProbingCandidate(val candidateName: String) : SmartSwitchStatus()
    data class AbsorbingPreRoll(val stationName: String, val secondsWaited: Int) : SmartSwitchStatus()
    data class BounceBackActive(val originalStation: Station, val remainingSeconds: Int) : SmartSwitchStatus()
}

@OptIn(UnstableApi::class)
class SmartSwitchCoordinator(
    private val context: Context,
    private val repository: HistoryAndFavoritesRepository,
    val intelligenceRepository: StationIntelligenceRepository,
    val audioClassifier: AudioContentClassifier,
    private val onSwitchStationRequest: (Station) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _status = MutableStateFlow<SmartSwitchStatus>(SmartSwitchStatus.Idle)
    val status: StateFlow<SmartSwitchStatus> = _status.asStateFlow()

    private val _speechSeconds = MutableStateFlow(0)
    val speechSeconds: StateFlow<Int> = _speechSeconds.asStateFlow()

    private val _bounceBackStation = MutableStateFlow<Station?>(null)
    val bounceBackStation: StateFlow<Station?> = _bounceBackStation.asStateFlow()

    private val _bounceBackSecondsRemaining = MutableStateFlow(0)
    val bounceBackSecondsRemaining: StateFlow<Int> = _bounceBackSecondsRemaining.asStateFlow()

    private var monitoringJob: Job? = null
    private var bounceBackJob: Job? = null
    private var probePlayer: ExoPlayer? = null

    private var currentStation: Station? = null
    private var isAudiblePlaying: Boolean = false
    private var consecutiveSpeechSeconds: Int = 0
    private var consecutiveMusicSeconds: Int = 0

    // Sliding history buffer of recently used fallback stations (prevents repeating the same station 5 times)
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
        val previous = currentStation
        currentStation = station
        consecutiveSpeechSeconds = 0
        _speechSeconds.value = 0

        if (station != null) {
            audioClassifier.setStationContext(
                trackTitle = "",
                isAdFree = station.isAdFree()
            )
            // If user manually switched away from the bounce-back station, cancel bounce-back
            if (_bounceBackStation.value != null &&
                station.stationuuid != _bounceBackStation.value?.stationuuid &&
                station.getStreamUrl() != _bounceBackStation.value?.getStreamUrl() &&
                previous?.stationuuid != _bounceBackStation.value?.stationuuid
            ) {
                // User chose another station manually
                cancelBounceBack()
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
                    _status.value = SmartSwitchStatus.Idle
                    consecutiveSpeechSeconds = 0
                    _speechSeconds.value = 0
                    continue
                }

                // Sync dynamic speech sensitivity to audio classifier
                audioClassifier.sensitivityThreshold = intelligenceRepository.speechSensitivity.value

                val minuteOfHour = Calendar.getInstance().get(Calendar.MINUTE)
                val isSpeech = audioClassifier.isSpeechDetected

                // Self-learning update in background
                intelligenceRepository.recordSpeechSample(station, minuteOfHour, isSpeech)

                if (isSpeech) {
                    consecutiveSpeechSeconds++
                    consecutiveMusicSeconds = 0
                    _speechSeconds.value = consecutiveSpeechSeconds
                    val tolerance = intelligenceRepository.speechToleranceSeconds.value

                    if (consecutiveSpeechSeconds >= tolerance) {
                        // Trigger Smart Switch
                        triggerSmartSwitch()
                        consecutiveSpeechSeconds = 0
                        consecutiveMusicSeconds = 0
                        _speechSeconds.value = 0
                    } else {
                        _status.value = SmartSwitchStatus.SpeechTolerance(
                            elapsedSeconds = consecutiveSpeechSeconds,
                            maxSeconds = tolerance
                        )
                    }
                } else {
                    consecutiveMusicSeconds++
                    val holdSeconds = intelligenceRepository.speechResetHoldSeconds.value
                    if (consecutiveMusicSeconds >= holdSeconds) {
                        // Reset speech timer ONLY after sustained music for holdSeconds
                        consecutiveSpeechSeconds = 0
                        _speechSeconds.value = 0
                    }

                    val original = _bounceBackStation.value
                    if (original != null && _bounceBackSecondsRemaining.value > 0) {
                        _status.value = SmartSwitchStatus.BounceBackActive(
                            originalStation = original,
                            remainingSeconds = _bounceBackSecondsRemaining.value
                        )
                    } else {
                        _status.value = SmartSwitchStatus.Monitoring(audioClassifier.detectionReason)
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
            // Find candidates
            val candidate = findBestCandidateStation(current) ?: return@launch

            // If we are not already in a bounce-back, remember the original station
            if (_bounceBackStation.value == null) {
                _bounceBackStation.value = current
            }

            // Silent probe candidate
            _status.value = SmartSwitchStatus.ProbingCandidate(candidate.name)
            val isCandidateReady = silentlyProbeCandidate(candidate)

            if (isCandidateReady) {
                // Execute switch
                markFallbackUsed(candidate)
                onSwitchStationRequest(candidate)
                startBounceBackTimer()
            } else {
                // Try an electronic fallback directly
                val electronicFallback = getCuratedElectronicFallback(current)
                if (electronicFallback != null && electronicFallback.getStreamUrl() != candidate.getStreamUrl()) {
                    _status.value = SmartSwitchStatus.ProbingCandidate(electronicFallback.name)
                    val probe2 = silentlyProbeCandidate(electronicFallback)
                    if (probe2) {
                        markFallbackUsed(electronicFallback)
                        onSwitchStationRequest(electronicFallback)
                        startBounceBackTimer()
                    }
                }
            }
        }
    }

    private fun findBestCandidateStation(excludeStation: Station): Station? {
        val strategy = intelligenceRepository.smartSwitchStrategy.value
        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
        val curated = CuratedStations.load(context)
        val favorites = repository.favorites.value

        when (strategy) {
            "favorites_only" -> {
                return pickBestCandidateFromList(favorites, excludeStation, currentMinute)
            }

            "specific" -> {
                val specificKey = intelligenceRepository.specificFallbackKey.value
                if (!specificKey.isNullOrBlank()) {
                    val candidate = (curated + favorites).firstOrNull {
                        (it.stationuuid == specificKey || it.getStreamUrl() == specificKey) &&
                        (it.stationuuid != excludeStation.stationuuid && it.getStreamUrl() != excludeStation.getStreamUrl())
                    }
                    if (candidate != null) return candidate
                }
            }

            "same_genre" -> {
                val currentTags = excludeStation.tags?.lowercase()?.split(",")?.map { it.trim() } ?: emptyList()
                if (currentTags.isNotEmpty()) {
                    val matchingGenreStations = (curated + favorites).filter { st ->
                        st.stationuuid != excludeStation.stationuuid && st.getStreamUrl() != excludeStation.getStreamUrl() &&
                        st.tags?.lowercase()?.split(",")?.any { tag -> currentTags.contains(tag.trim()) } == true
                    }
                    val pick = pickBestCandidateFromList(matchingGenreStations, excludeStation, currentMinute)
                    if (pick != null) return pick
                }
            }
        }

        // Default: "favorites_first"
        val bestFav = pickBestCandidateFromList(favorites, excludeStation, currentMinute)
        if (bestFav != null) return bestFav

        // Electronic / Pure Music Fallback
        return getCuratedElectronicFallback(excludeStation)
    }

    private fun pickBestCandidateFromList(
        candidates: List<Station>,
        excludeStation: Station,
        currentMinute: Int
    ): Station? {
        if (candidates.isEmpty()) return null

        val excludeKeys = setOfNotNull(
            excludeStation.stationuuid,
            excludeStation.getStreamUrl(),
            _bounceBackStation.value?.stationuuid,
            _bounceBackStation.value?.getStreamUrl()
        )

        // Filter out current playing station and original bounce-back station
        val eligible = candidates.filter { st ->
            val key1 = st.stationuuid
            val key2 = st.getStreamUrl()
            (key1 == null || !excludeKeys.contains(key1)) && !excludeKeys.contains(key2)
        }

        if (eligible.isEmpty()) return null

        // Filter out recently used fallbacks if alternative candidates exist
        val freshCandidates = eligible.filter { st ->
            val key = st.stationuuid?.takeIf { it.isNotBlank() } ?: st.getStreamUrl()
            !recentFallbackKeys.contains(key)
        }.ifEmpty { eligible }

        // Rank candidates by pure music status, pre-roll ad presence, and lowest speech probability
        val ranked = freshCandidates.sortedWith(
            compareByDescending<Station> { intelligenceRepository.isStationPureMusic(it) }
                .thenByDescending { !intelligenceRepository.hasPreRollAd(it) }
                .thenBy { intelligenceRepository.getSpeechProbabilityAtMinute(it, currentMinute) }
        )

        // Filter out candidates with very high speech probability at this minute
        val lowSpeechCandidates = ranked.filter {
            intelligenceRepository.getSpeechProbabilityAtMinute(it, currentMinute) < 0.70f
        }.ifEmpty { ranked }

        // Take top 3 candidates and shuffle to ensure natural rotation variety
        return lowSpeechCandidates.take(3).shuffled().firstOrNull()
    }

    private fun getCuratedElectronicFallback(excludeStation: Station): Station? {
        val curated = CuratedStations.load(context)
        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
        val electronicStations = curated.filter {
            (it.stationuuid != excludeStation.stationuuid && it.getStreamUrl() != excludeStation.getStreamUrl()) &&
            ((it.tags?.lowercase()?.contains("electronic") == true) ||
             (it.tags?.lowercase()?.contains("chillout") == true) ||
             (it.tags?.lowercase()?.contains("lo-fi") == true) ||
             (it.tags?.lowercase()?.contains("ambient") == true) ||
             it.name.contains("Ibiza", ignoreCase = true) ||
             it.name.contains("Groove Salad", ignoreCase = true) ||
             it.name.contains("Lofi Cafe", ignoreCase = true))
        }

        return pickBestCandidateFromList(electronicStations, excludeStation, currentMinute)
    }

    /**
     * Silently probes candidate station in background with muted volume.
     * Absorbs pre-roll ads silently if detected before reporting ready.
     */
    private suspend fun silentlyProbeCandidate(candidate: Station): Boolean {
        return try {
            releaseProbePlayer()
            val probe = ExoPlayer.Builder(context).build().apply {
                volume = 0f // Completely silent
            }
            probePlayer = probe

            val streamUrl = candidate.getStreamUrl()
            if (streamUrl.isBlank()) return false

            var isTrackMusic = true
            probe.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val t = (mediaMetadata.title?.toString() ?: "").lowercase()
                    if (t.contains("werbung") || t.contains("ad") || t.contains("nachrichten") || t.contains("news")) {
                        isTrackMusic = false
                    }
                }
            })

            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            probe.setMediaItem(mediaItem)
            probe.prepare()
            probe.play()

            // Pre-roll ad absorption: if station is known to have pre-roll or candidate is non-ad-free
            val hasPreRoll = intelligenceRepository.hasPreRollAd(candidate)
            if (hasPreRoll) {
                _status.value = SmartSwitchStatus.AbsorbingPreRoll(candidate.name, 0)
                // Wait up to 10-15s for pre-roll ad to buffer through silently
                var waited = 0
                while (waited < 10) {
                    delay(1000)
                    waited++
                    _status.value = SmartSwitchStatus.AbsorbingPreRoll(candidate.name, waited)
                    if (probe.playbackState == Player.STATE_READY) {
                        break
                    }
                }
            } else {
                // Ad-free fast path (~2 seconds)
                delay(2000)
            }

            releaseProbePlayer()
            isTrackMusic
        } catch (e: Exception) {
            e.printStackTrace()
            releaseProbePlayer()
            false
        }
    }

    private fun startBounceBackTimer() {
        bounceBackJob?.cancel()
        val minutes = intelligenceRepository.bounceBackMinutes.value
        var remainingSeconds = minutes * 60
        _bounceBackSecondsRemaining.value = remainingSeconds

        bounceBackJob = scope.launch {
            val original = _bounceBackStation.value ?: return@launch
            while (isActive && remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                _bounceBackSecondsRemaining.value = remainingSeconds

                _status.value = SmartSwitchStatus.BounceBackActive(
                    originalStation = original,
                    remainingSeconds = remainingSeconds
                )
            }

            // 7 minutes are up -> Silently verify original station has returned to music
            _status.value = SmartSwitchStatus.ProbingCandidate(original.name)
            val isOriginalReady = silentlyProbeCandidate(original)
            if (isOriginalReady) {
                // Return to original station
                returnToOriginalStation()
            } else {
                // Retry in 20 seconds
                remainingSeconds = 20
                _bounceBackSecondsRemaining.value = remainingSeconds
                startBounceBackTimer()
            }
        }
    }

    fun returnToOriginalStation() {
        val original = _bounceBackStation.value ?: return
        cancelBounceBack()
        onSwitchStationRequest(original)
    }

    fun cancelBounceBack() {
        bounceBackJob?.cancel()
        bounceBackJob = null
        _bounceBackStation.value = null
        _bounceBackSecondsRemaining.value = 0
        _status.value = SmartSwitchStatus.Idle
    }

    private fun releaseProbePlayer() {
        try {
            probePlayer?.stop()
            probePlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            probePlayer = null
        }
    }

    fun release() {
        stopMonitoring()
        cancelBounceBack()
        releaseProbePlayer()
    }
}
