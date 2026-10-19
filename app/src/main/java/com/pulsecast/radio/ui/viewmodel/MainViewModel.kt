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

    private val _activeCategory = MutableStateFlow("featured")
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

    init {
        loadCategory("featured")
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
                    _activeCategory.value = "featured"
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
            _activeCategory.value = "featured"
        }
        loadCategory(_activeCategory.value)
    }

    private fun loadCategory(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                when (category) {
                    "featured" -> {
                        _stations.value = CuratedStations.load(getApplication())
                    }
                    "puremusic" -> {
                        val curated = CuratedStations.load(getApplication())
                        val apiStations = try {
                            ApiClient.getApi().getTopClickStations(40)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        val combined = (curated + apiStations).distinctBy { it.stationuuid ?: it.getStreamUrl() }
                        _stations.value = combined.filter { playbackController.intelligenceRepository.isStationPureMusic(it) }
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
                    "topclick" -> {
                        _stations.value = ApiClient.getApi().getTopClickStations(50)
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
            if (_activeCategory.value == "history") {
                val q = query.lowercase().trim()
                _stations.value = repository.history.value.filter {
                    it.name.lowercase().contains(q) ||
                    (it.tags?.lowercase()?.contains(q) == true) ||
                    (it.country?.lowercase()?.contains(q) == true)
                }
            } else if (_activeCategory.value == "favorites") {
                val q = query.lowercase().trim()
                _stations.value = repository.favorites.value.filter {
                    it.name.lowercase().contains(q) ||
                    (it.tags?.lowercase()?.contains(q) == true) ||
                    (it.country?.lowercase()?.contains(q) == true)
                }
            } else if (_activeCategory.value == "puremusic") {
                val q = query.lowercase().trim()
                val curated = CuratedStations.load(getApplication())
                val apiStations = try {
                    ApiClient.getApi().searchStations(query, 50)
                } catch (e: Exception) {
                    emptyList()
                }
                _stations.value = (curated + apiStations)
                    .distinctBy { it.stationuuid ?: it.getStreamUrl() }
                    .filter { playbackController.intelligenceRepository.isStationPureMusic(it) }
                    .filter {
                        it.name.lowercase().contains(q) ||
                        (it.tags?.lowercase()?.contains(q) == true) ||
                        (it.country?.lowercase()?.contains(q) == true)
                    }
            } else {
                _stations.value = ApiClient.getApi().searchStations(query, 50)
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
        if (_activeCategory.value == "puremusic") {
            loadCategory("puremusic")
        }
    }

    fun isStationPureMusic(station: Station): Boolean {
        return playbackController.intelligenceRepository.isStationPureMusic(station)
    }

    fun setSmartSwitchEnabled(enabled: Boolean) {
        playbackController.intelligenceRepository.setSmartSwitchEnabled(enabled)
    }

    fun setSpeechToleranceSeconds(seconds: Int) {
        playbackController.intelligenceRepository.setSpeechToleranceSeconds(seconds)
    }

    fun setBounceBackMinutes(minutes: Int) {
        playbackController.intelligenceRepository.setBounceBackMinutes(minutes)
    }

    fun returnToOriginalStation() {
        playbackController.smartSwitchCoordinator.returnToOriginalStation()
    }

    fun cancelBounceBack() {
        playbackController.smartSwitchCoordinator.cancelBounceBack()
    }

    override fun onCleared() {
        super.onCleared()
        playbackController.release()
    }
}
