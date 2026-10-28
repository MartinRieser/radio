package com.pulsecast.radio.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecast.radio.data.ApiClient
import com.pulsecast.radio.data.CuratedStations
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.playback.PlaybackController
import com.pulsecast.radio.playback.RadioPlaybackBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // 3 Favorite lists ("Hart", "Diamant", "Stern")
    val favorites1 = repository.favorites1
    val favorites2 = repository.favorites2
    val favorites3 = repository.favorites3
    val allFavorites = repository.allFavorites
    val favorites = repository.allFavorites // Alias

    private var searchJob: Job? = null

    // App Updater
    val appUpdateManager = com.pulsecast.radio.updater.AppUpdateManager(application)
    val appUpdateState = appUpdateManager.updateState

    // Automotive Car Drive Mode
    val carDriveModeManager = com.pulsecast.radio.automotive.CarDriveModeManager(application)
    val isCarModeActive = carDriveModeManager.isCarModeActive
    val currentSpeedKmh = carDriveModeManager.currentSpeedKmh
    val carConnectionReason = carDriveModeManager.carConnectionReason

    fun toggleCarMode() {
        carDriveModeManager.toggleManualCarMode()
    }

    fun exitCarMode() {
        carDriveModeManager.exitCarMode()
    }

    init {
        loadCategory("featured")
        checkForAppUpdate()
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
                    _activeCategory.value = "featured"
                }
                loadCategory(_activeCategory.value)
            } else {
                if (!_activeCategory.value.startsWith("favorites")) {
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

    private fun filterMaxBitrate(stations: List<Station>): List<Station> {
        return stations.filter { it.bitrate <= 320 }
    }

    private fun loadCategory(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val loadedList = when (category) {
                    "featured" -> {
                        CuratedStations.load(getApplication())
                    }
                    "favorites", "favorites_1" -> {
                        CuratedStations.deduplicateAndOptimize(repository.favorites1.value)
                    }
                    "favorites_2" -> {
                        CuratedStations.deduplicateAndOptimize(repository.favorites2.value)
                    }
                    "favorites_3" -> {
                        CuratedStations.deduplicateAndOptimize(repository.favorites3.value)
                    }
                    else -> {
                        // Tag based
                        val apiStations = ApiClient.getApi().getStationsByTag(category, 50)
                        CuratedStations.deduplicateAndOptimize(apiStations)
                    }
                }
                val filtered = filterMaxBitrate(loadedList)
                _stations.value = filtered
                RadioPlaybackBridge.updateStationList(filtered)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Fehler beim Laden der Sender. Bitte Internetverbindung prüfen."
                if (_stations.value.isEmpty()) {
                    val fallback = filterMaxBitrate(CuratedStations.load(getApplication()))
                    _stations.value = fallback
                    RadioPlaybackBridge.updateStationList(fallback)
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
            val matches = when (_activeCategory.value) {
                "favorites", "favorites_1" -> {
                    CuratedStations.deduplicateAndOptimize(
                        repository.favorites1.value.filter {
                            it.name.lowercase().contains(q) ||
                            (it.tags?.lowercase()?.contains(q) == true) ||
                            (it.country?.lowercase()?.contains(q) == true)
                        }
                    )
                }
                "favorites_2" -> {
                    CuratedStations.deduplicateAndOptimize(
                        repository.favorites2.value.filter {
                            it.name.lowercase().contains(q) ||
                            (it.tags?.lowercase()?.contains(q) == true) ||
                            (it.country?.lowercase()?.contains(q) == true)
                        }
                    )
                }
                "favorites_3" -> {
                    CuratedStations.deduplicateAndOptimize(
                        repository.favorites3.value.filter {
                            it.name.lowercase().contains(q) ||
                            (it.tags?.lowercase()?.contains(q) == true) ||
                            (it.country?.lowercase()?.contains(q) == true)
                        }
                    )
                }
                else -> {
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
                    CuratedStations.deduplicateAndOptimize(curatedMatches + apiStations)
                }
            }
            val filtered = filterMaxBitrate(matches)
            _stations.value = filtered
            RadioPlaybackBridge.updateStationList(filtered)
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

    fun cycleFavorite(station: Station): Int {
        val type = repository.cycleFavorite(station)
        if (_activeCategory.value.startsWith("favorites")) {
            loadCategory(_activeCategory.value)
        }
        return type
    }

    fun getFavoriteType(station: Station): Int {
        return repository.getFavoriteType(station)
    }

    fun setFavoriteType(station: Station, type: Int) {
        repository.setFavoriteType(station, type)
        if (_activeCategory.value.startsWith("favorites")) {
            loadCategory(_activeCategory.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        carDriveModeManager.unregister()
        playbackController.release()
    }
}
