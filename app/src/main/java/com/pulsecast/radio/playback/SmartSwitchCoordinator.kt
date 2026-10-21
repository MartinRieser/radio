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
    val intelligenceRepository: StationIntelligenceRepository,
    val audioClassifier: AudioContentClassifier,
    private val onSwitchStationRequest: (Station) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _status = MutableStateFlow<SmartSwitchStatus>(SmartSwitchStatus.Music)
    val status: StateFlow<SmartSwitchStatus> = _status.asStateFlow()

    private val _speechSeconds = MutableStateFlow(0)
    val speechSeconds: StateFlow<Int> = _speechSeconds.asStateFlow()

    private val _originalSwitchedStation = MutableStateFlow<Station?>(null)
    val originalSwitchedStation: StateFlow<Station?> = _originalSwitchedStation.asStateFlow()

    private var monitoringJob: Job? = null
    private var probePlayer: ExoPlayer? = null

    private var currentStation: Station? = null
    private var isAudiblePlaying: Boolean = false
    private var consecutiveSpeechSeconds: Int = 0
    private var consecutiveMusicSeconds: Int = 0

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
        _speechSeconds.value = 0

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

                if (!isSpeechIgnoredForStation && shouldTriggerSpeech) {
                    consecutiveSpeechSeconds += if (isJingleMatched || isPureSpeechOrAd || isTopOfHourNewsWindow) 2 else 1
                    consecutiveMusicSeconds = 0
                    _speechSeconds.value = consecutiveSpeechSeconds

                    val baseTolerance = intelligenceRepository.speechToleranceSeconds.value
                    val requiredTolerance = when {
                        isPureSpeechOrAd || isTopOfHourNewsWindow || isJingleMatched -> 2 // Fast switch (2 seconds) on pure news/ads
                        else -> baseTolerance // Standard tolerance for DJ comments
                    }

                    if (consecutiveSpeechSeconds >= requiredTolerance) {
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

            if (_originalSwitchedStation.value == null) {
                _originalSwitchedStation.value = current
            }

            val isCandidateReady = silentlyProbeCandidate(candidate)
            if (isCandidateReady) {
                markFallbackUsed(candidate)
                onSwitchStationRequest(candidate)
                _status.value = SmartSwitchStatus.Switched(current)
            } else {
                val electronicFallback = getCuratedElectronicFallback(current)
                if (electronicFallback != null && electronicFallback.getStreamUrl() != candidate.getStreamUrl()) {
                    val probe2 = silentlyProbeCandidate(electronicFallback)
                    if (probe2) {
                        markFallbackUsed(electronicFallback)
                        onSwitchStationRequest(electronicFallback)
                        _status.value = SmartSwitchStatus.Switched(current)
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

        val bestFav = pickBestCandidateFromList(favorites, excludeStation, currentMinute)
        if (bestFav != null) return bestFav

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
            _originalSwitchedStation.value?.stationuuid,
            _originalSwitchedStation.value?.getStreamUrl()
        )

        val eligible = candidates.filter { st ->
            val key1 = st.stationuuid
            val key2 = st.getStreamUrl()
            (key1 == null || !excludeKeys.contains(key1)) && !excludeKeys.contains(key2)
        }

        if (eligible.isEmpty()) return null

        val freshCandidates = eligible.filter { st ->
            val key = st.stationuuid?.takeIf { it.isNotBlank() } ?: st.getStreamUrl()
            !recentFallbackKeys.contains(key)
        }.ifEmpty { eligible }

        val ranked = freshCandidates.sortedWith(
            compareByDescending<Station> { intelligenceRepository.isStationPureMusic(it) }
                .thenByDescending { !intelligenceRepository.hasPreRollAd(it) }
                .thenBy { intelligenceRepository.getSpeechProbabilityAtMinute(it, currentMinute) }
        )

        val lowSpeechCandidates = ranked.filter {
            intelligenceRepository.getSpeechProbabilityAtMinute(it, currentMinute) < 0.70f
        }.ifEmpty { ranked }

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

    private suspend fun silentlyProbeCandidate(candidate: Station): Boolean {
        return try {
            releaseProbePlayer()
            val probe = ExoPlayer.Builder(context).build().apply {
                volume = 0f
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

            val hasPreRoll = intelligenceRepository.hasPreRollAd(candidate)
            if (hasPreRoll) {
                var waited = 0
                while (waited < 10) {
                    delay(1000)
                    waited++
                    if (probe.playbackState == Player.STATE_READY) {
                        break
                    }
                }
            } else {
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

    /**
     * Returns to the original station that triggered the smart switch
     * and adds it to ignoredSpeechStationKeys so speech on that station is ignored for now.
     */
    fun returnToOriginalStationAndIgnoreSpeech() {
        val original = _originalSwitchedStation.value ?: return
        val key = original.stationuuid?.takeIf { it.isNotBlank() } ?: original.getStreamUrl()
        ignoredSpeechStationKeys.add(key)
        _originalSwitchedStation.value = null
        _status.value = SmartSwitchStatus.Music
        onSwitchStationRequest(original)
    }

    fun clearIgnoredSpeechStations() {
        ignoredSpeechStationKeys.clear()
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
        _originalSwitchedStation.value = null
        releaseProbePlayer()
    }
}
