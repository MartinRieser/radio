package com.pulsecast.radio.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pulsecast.radio.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryAndFavoritesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("pulsecast_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _favorites = MutableStateFlow<List<Station>>(emptyList())
    val favorites: StateFlow<List<Station>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<Station>>(emptyList())
    val history: StateFlow<List<Station>> = _history.asStateFlow()

    init {
        loadFavorites()
        loadHistory()
    }

    // --- FAVORITES ---
    private fun loadFavorites() {
        val json = prefs.getString("pulse_favorites", null)
        val list: List<Station> = if (json != null) {
            try {
                val type = object : TypeToken<List<Station>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _favorites.value = list
    }

    fun toggleFavorite(station: Station): Boolean {
        val current = _favorites.value.toMutableList()
        val index = current.indexOfFirst { it.stationuuid == station.stationuuid || it.getStreamUrl() == station.getStreamUrl() }
        val isNowFav: Boolean
        if (index >= 0) {
            current.removeAt(index)
            isNowFav = false
        } else {
            current.add(0, station.copy(isFavorite = true))
            isNowFav = true
        }
        _favorites.value = current
        saveFavorites(current)
        return isNowFav
    }

    fun isFavorite(station: Station): Boolean {
        return _favorites.value.any { it.stationuuid == station.stationuuid || it.getStreamUrl() == station.getStreamUrl() }
    }

    private fun saveFavorites(list: List<Station>) {
        prefs.edit().putString("pulse_favorites", gson.toJson(list)).apply()
    }

    // --- HISTORY (> 1 Minute Playback) ---
    private fun loadHistory() {
        val json = prefs.getString("pulse_history", null)
        val list: List<Station> = if (json != null) {
            try {
                val type = object : TypeToken<List<Station>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _history.value = list
    }

    /**
     * Records a station to history.
     * STRICT RULE: Only stations with duration >= 60 seconds are recorded!
     * Capacity is unlimited.
     * Duplicate stations are moved to the top with the latest timestamp.
     */
    fun addToHistory(station: Station, durationSeconds: Int) {
        if (durationSeconds < 60) {
            // Strictly ignore plays under 1 minute
            return
        }

        val current = _history.value.toMutableList()
        val existingIndex = current.indexOfFirst {
            (it.stationuuid != null && it.stationuuid == station.stationuuid) ||
            (it.getStreamUrl().isNotBlank() && it.getStreamUrl() == station.getStreamUrl()) ||
            it.name.equals(station.name, ignoreCase = true)
        }

        val historyItem = station.copy(
            lastPlayed = System.currentTimeMillis(),
            playDuration = durationSeconds,
            isFavorite = isFavorite(station)
        )

        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
        }

        // Unlimited memory - newest station to top
        current.add(0, historyItem)
        _history.value = current
        saveHistory(current)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("pulse_history").apply()
    }

    private fun saveHistory(list: List<Station>) {
        prefs.edit().putString("pulse_history", gson.toJson(list)).apply()
    }

    companion object {
        fun formatRelativeTime(timestamp: Long?): String {
            if (timestamp == null) return ""
            val diffSeconds = (System.currentTimeMillis() - timestamp) / 1000
            return when {
                diffSeconds < 60 -> "Gerade eben"
                diffSeconds < 3600 -> "vor ${diffSeconds / 60} Min."
                diffSeconds < 86400 -> "vor ${diffSeconds / 3600} Std."
                diffSeconds < 172800 -> "Gestern"
                diffSeconds < 604800 -> "vor ${diffSeconds / 86400} Tagen"
                else -> java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
            }
        }
    }
}
