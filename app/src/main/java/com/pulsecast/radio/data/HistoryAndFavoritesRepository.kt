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

    // 3 Favorite Lists
    // List 1: ❤️ Herz
    private val _favorites1 = MutableStateFlow<List<Station>>(emptyList())
    val favorites1: StateFlow<List<Station>> = _favorites1.asStateFlow()

    // List 2: 💎 Diamant
    private val _favorites2 = MutableStateFlow<List<Station>>(emptyList())
    val favorites2: StateFlow<List<Station>> = _favorites2.asStateFlow()

    // List 3: ⭐ Stern
    private val _favorites3 = MutableStateFlow<List<Station>>(emptyList())
    val favorites3: StateFlow<List<Station>> = _favorites3.asStateFlow()

    // Combined favorites for quick matching & Car Mode
    private val _allFavorites = MutableStateFlow<List<Station>>(emptyList())
    val allFavorites: StateFlow<List<Station>> = _allFavorites.asStateFlow()

    // Backwards compatibility alias
    val favorites: StateFlow<List<Station>> = _allFavorites

    init {
        loadAllFavorites()
    }

    private fun stationKey(station: Station): String {
        val uuid = station.stationuuid?.lowercase()?.trim() ?: ""
        val url = station.getStreamUrl().lowercase().trim()
        val cleanName = station.name.lowercase().split("(")[0].replace("fm", "").replace("radio", "").replace("hq", "").replace("orf", "").replace("|", "").trim()

        return when {
            cleanName.isNotBlank() -> "name:$cleanName"
            uuid.isNotBlank() && uuid.length > 10 -> "uuid:$uuid"
            else -> "url:$url"
        }
    }

    // --- FAVORITES ("Hart wie Herz") ---
    private fun loadFavoritesList(prefKey: String, targetFavoriteType: Int): List<Station> {
        val json = prefs.getString(prefKey, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Station>>() {}.type
            val list: List<Station> = gson.fromJson(json, type) ?: emptyList()
            list.map { it.copy(isFavorite = true, favoriteType = targetFavoriteType) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadAllFavorites() {
        val list1 = loadFavoritesList("pulse_favorites", 1)
        val list2 = loadFavoritesList("pulse_favorites_2", 2)
        val list3 = loadFavoritesList("pulse_favorites_3", 3)

        _favorites1.value = list1
        _favorites2.value = list2
        _favorites3.value = list3
        updateAllFavorites()
    }

    private fun updateAllFavorites() {
        _allFavorites.value = CuratedStations.deduplicateAndOptimize(_favorites1.value + _favorites2.value + _favorites3.value)
    }

    /**
     * Returns favorite list index:
     * 0 = Kein Favorit
     * 1 = Liste 1 (❤️ Herz)
     * 2 = Liste 2 (💎 Diamant)
     * 3 = Liste 3 (⭐ Stern)
     */
    fun getFavoriteType(station: Station): Int {
        val key = stationKey(station)
        if (_favorites1.value.any { stationKey(it) == key }) return 1
        if (_favorites2.value.any { stationKey(it) == key }) return 2
        if (_favorites3.value.any { stationKey(it) == key }) return 3
        return 0
    }

    /**
     * Cycles favorite state:
     * 0 (Kein) -> 1 (❤️ Herz) -> 2 (💎 Diamant) -> 3 (⭐ Stern) -> 0 (Kein)
     */
    fun cycleFavorite(station: Station): Int {
        val current = getFavoriteType(station)
        val next = (current + 1) % 4
        setFavoriteType(station, next)
        return next
    }

    fun setFavoriteType(station: Station, type: Int) {
        val key = stationKey(station)

        val list1 = _favorites1.value.toMutableList()
        val list2 = _favorites2.value.toMutableList()
        val list3 = _favorites3.value.toMutableList()

        list1.removeAll { stationKey(it) == key }
        list2.removeAll { stationKey(it) == key }
        list3.removeAll { stationKey(it) == key }

        when (type) {
            1 -> list1.add(0, station.copy(isFavorite = true, favoriteType = 1))
            2 -> list2.add(0, station.copy(isFavorite = true, favoriteType = 2))
            3 -> list3.add(0, station.copy(isFavorite = true, favoriteType = 3))
        }

        _favorites1.value = list1
        _favorites2.value = list2
        _favorites3.value = list3
        updateAllFavorites()

        saveFavorites(1, list1)
        saveFavorites(2, list2)
        saveFavorites(3, list3)
    }

    fun isFavorite(station: Station): Boolean {
        return getFavoriteType(station) > 0
    }

    fun toggleFavorite(station: Station): Boolean {
        return cycleFavorite(station) > 0
    }

    private fun saveFavorites(listNumber: Int, list: List<Station>) {
        val key = when (listNumber) {
            2 -> "pulse_favorites_2"
            3 -> "pulse_favorites_3"
            else -> "pulse_favorites"
        }
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }
}
