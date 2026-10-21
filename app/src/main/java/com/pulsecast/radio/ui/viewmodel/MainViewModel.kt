package com.pulsecast.radio.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecast.radio.data.ApiClient
import com.pulsecast.radio.data.CuratedStations
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.playback.PlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = HistoryAndFavoritesRepository(application)
    val playbackController = PlaybackController(application, repository)

    private val _activeCategory = MutableStateFlow("trained")
    val activeCategory: StateFlow<String> = _activeCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Sleep Timer
    private val _sleepMinutesRemaining = MutableStateFlow(0)
    val sleepMinutesRemaining: StateFlow<Int> = _sleepMinutesRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Combines favorites & history state
    val favorites = repository.favorites
    val history = repository.history

    private var searchJob: Job? = null

    // OTA Intelligence & App Updater
    val appUpdateManager = com.pulsecast.radio.updater.AppUpdateManager(application)
    val appUpdateState = appUpdateManager.updateState

    val intelligenceSyncState = playbackController.intelligenceRepository.syncState
    val activeGeneratedAt = playbackController.intelligenceRepository.activeGeneratedAt
    val trainedStationCount: Int
        get() = playbackController.intelligenceRepository.trainedStationCount

    init {
        loadCategory("trained")
        checkForAppUpdate()
    }

    fun syncIntelligence(forced: Boolean = true) {
        viewModelScope.launch {
            val updated = playbackController.intelligenceRepository.syncRemoteIntelligence(forced)
            if (updated && _activeCategory.value == "trained") {
                _stations.value = CuratedStations.load(getApplication())
            }
        }
    }

    fun checkForAppUpdate() {
        appUpdateManager.checkForUpdate(viewModelScope)
    }

    fun startAppUpdate(activity: android.app.Activity, downloadUrl: String) {
        appUpdateManager.startDownloadAndInstall(activity, downloadUrl, viewModelScope)
    }

    fun dismissAppUpdate() {
        appUpdateManager.dismissUpdate()
    }

    fun selectCategory(category: String) {
        _activeCategory.value = category
        _searchQuery.value = ""
        loadCategory(category)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400) // Debounce
            if (query.isBlank()) {
                if (_activeCategory.value == "search") {
                    _activeCategory.value = "trained"
                }
                loadCategory(_activeCategory.value)
            } else {
                if (_activeCategory.value != "favorites" && _activeCategory.value != "history") {
                    _activeCategory.value = "search"
                }
                performSearch(query)
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        if (_activeCategory.value == "search") {
            _activeCategory.value = "trained"
        }
        loadCategory(_activeCategory.value)
    }

    private fun loadCategory(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                when (category) {
                    "trained", "featured" -> {
                        _stations.value = CuratedStations.load(getApplication())
                    }
                    "adfree" -> {
                        val curated = CuratedStations.load(getApplication())
                        val apiStations = try {
                            ApiClient.getApi().getTopClickStations(40)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        val combined = (curated + apiStations).distinctBy { it.stationuuid ?: it.getStreamUrl() }
                        _stations.value = combined.filter { it.isAdFree() }
                    }
                    "favorites" -> {
                        _stations.value = repository.favorites.value
                    }
                    "history" -> {
                        _stations.value = repository.history.value
                    }
                    else -> {
                        // Tag based
                        _stations.value = ApiClient.getApi().getStationsByTag(category, 50)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Fehler beim Laden der Sender. Bitte Internetverbindung prüfen."
                if (_stations.value.isEmpty()) {
                    _stations.value = CuratedStations.load(getApplication())
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun performSearch(query: String) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val q = query.lowercase().trim()
            if (_activeCategory.value == "history") {
                _stations.value = repository.history.value.filter {
                    it.name.lowercase().contains(q) ||
                    (it.tags?.lowercase()?.contains(q) == true) ||
                    (it.country?.lowercase()?.contains(q) == true)
                }
            } else if (_activeCategory.value == "favorites") {
                _stations.value = repository.favorites.value.filter {
                    it.name.lowercase().contains(q) ||
                    (it.tags?.lowercase()?.contains(q) == true) ||
                    (it.country?.lowercase()?.contains(q) == true)
                }
            } else {
                val curated = CuratedStations.load(getApplication())
                val curatedMatches = curated.filter {
                    it.name.lowercase().contains(q) ||
                    (it.tags?.lowercase()?.contains(q) == true) ||
                    (it.country?.lowercase()?.contains(q) == true)
                }
                val apiStations = try {
                    ApiClient.getApi().searchStations(query, 50)
                } catch (e: Exception) {
                    emptyList()
                }
                _stations.value = (curatedMatches + apiStations).distinctBy { it.stationuuid ?: it.getStreamUrl() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Suche fehlgeschlagen."
        } finally {
            _isLoading.value = false
        }
    }

    fun playStation(station: Station) {
        playbackController.playStation(station)
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun playNext() {
        val list = _stations.value
        if (list.isEmpty()) return
        val current = playbackController.currentStation.value
        val currentIndex = list.indexOfFirst { it.stationuuid == current?.stationuuid || it.getStreamUrl() == current?.getStreamUrl() }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % list.size else 0
        playStation(list[nextIndex])
    }

    fun playPrevious() {
        val original = playbackController.smartSwitchCoordinator.originalSwitchedStation.value
        if (original != null) {
            playbackController.smartSwitchCoordinator.returnToOriginalStationAndIgnoreSpeech()
            return
        }
        val list = _stations.value
        if (list.isEmpty()) return
        val current = playbackController.currentStation.value
        val currentIndex = list.indexOfFirst { it.stationuuid == current?.stationuuid || it.getStreamUrl() == current?.getStreamUrl() }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playStation(list[prevIndex])
    }

    fun toggleFavorite(station: Station) {
        repository.toggleFavorite(station)
        if (_activeCategory.value == "favorites") {
            _stations.value = repository.favorites.value
        }
    }

    fun clearHistory() {
        repository.clearHistory()
        if (_activeCategory.value == "history") {
            _stations.value = emptyList()
        }
    }

    // --- SLEEP TIMER ---
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepMinutesRemaining.value = minutes

        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var remainingSeconds = minutes * 60
                while (isActive && remainingSeconds > 0) {
                    delay(1000)
                    remainingSeconds--
                    _sleepMinutesRemaining.value = (remainingSeconds + 59) / 60
                }
                // Time is up -> Stop playback
                playbackController.stop()
                _sleepMinutesRemaining.value = 0
            }
        }
    }

    fun togglePureMusic(station: Station) {
        playbackController.intelligenceRepository.togglePureMusic(station)
        loadCategory(_activeCategory.value)
    }

    fun isStationPureMusic(station: Station): Boolean {
        return playbackController.intelligenceRepository.isStationPureMusic(station)
    }

    val speechSensitivity = playbackController.intelligenceRepository.speechSensitivity
    val speechResetHoldSeconds = playbackController.intelligenceRepository.speechResetHoldSeconds
    val smartSwitchStrategy = playbackController.intelligenceRepository.smartSwitchStrategy
    val specificFallbackKey = playbackController.intelligenceRepository.specificFallbackKey
    val miningMode = playbackController.intelligenceRepository.miningMode
    val originalSwitchedStation = playbackController.smartSwitchCoordinator.originalSwitchedStation

    fun setSmartSwitchEnabled(enabled: Boolean) {
        playbackController.intelligenceRepository.setSmartSwitchEnabled(enabled)
    }

    fun setSpeechToleranceSeconds(seconds: Int) {
        playbackController.intelligenceRepository.setSpeechToleranceSeconds(seconds)
    }

    fun setSpeechSensitivity(sensitivity: Float) {
        playbackController.intelligenceRepository.setSpeechSensitivity(sensitivity)
    }

    fun setSpeechResetHoldSeconds(seconds: Int) {
        playbackController.intelligenceRepository.setSpeechResetHoldSeconds(seconds)
    }

    fun setSmartSwitchStrategy(strategy: String) {
        playbackController.intelligenceRepository.setSmartSwitchStrategy(strategy)
    }

    fun setSpecificFallbackKey(key: String?) {
        playbackController.intelligenceRepository.setSpecificFallbackKey(key)
    }

    fun setMiningMode(mode: String) {
        playbackController.intelligenceRepository.setMiningMode(mode)
    }

    fun returnToOriginalStation() {
        playbackController.smartSwitchCoordinator.returnToOriginalStationAndIgnoreSpeech()
    }

    override fun onCleared() {
        super.onCleared()
        playbackController.release()
    }
}
