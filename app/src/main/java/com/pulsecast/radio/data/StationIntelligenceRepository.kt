package com.pulsecast.radio.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pulsecast.radio.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Persists and updates learned station intelligence:
 * 1. 60-minute speech/ad heatmap (minute-of-hour 0..59)
 * 2. Pure Music ("100% Musik") tags & user overrides
 * 3. Pre-roll ad detection history
 * 4. User preferences (Tolerance delay, 7-min Bounce-Back, Smart-Switch toggle)
 */
class StationIntelligenceRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("pulse_station_intel", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Smart-Switch Settings
    private val _isSmartSwitchEnabled = MutableStateFlow(prefs.getBoolean("smart_switch_enabled", true))
    val isSmartSwitchEnabled: StateFlow<Boolean> = _isSmartSwitchEnabled.asStateFlow()

    private val _speechToleranceSeconds = MutableStateFlow(prefs.getInt("speech_tolerance_seconds", 10))
    val speechToleranceSeconds: StateFlow<Int> = _speechToleranceSeconds.asStateFlow()

    private val _bounceBackMinutes = MutableStateFlow(prefs.getInt("bounce_back_minutes", 7))
    val bounceBackMinutes: StateFlow<Int> = _bounceBackMinutes.asStateFlow()

    private val _speechSensitivity = MutableStateFlow(prefs.getFloat("speech_sensitivity", 0.50f))
    val speechSensitivity: StateFlow<Float> = _speechSensitivity.asStateFlow()

    private val _speechResetHoldSeconds = MutableStateFlow(prefs.getInt("speech_reset_hold_seconds", 3))
    val speechResetHoldSeconds: StateFlow<Int> = _speechResetHoldSeconds.asStateFlow()

    private val _smartSwitchStrategy = MutableStateFlow(prefs.getString("smart_switch_strategy", "favorites_first") ?: "favorites_first")
    val smartSwitchStrategy: StateFlow<String> = _smartSwitchStrategy.asStateFlow()

    private val _specificFallbackKey = MutableStateFlow(prefs.getString("specific_fallback_key", null))
    val specificFallbackKey: StateFlow<String?> = _specificFallbackKey.asStateFlow()

    // In-memory caches for fast lookup
    // Key: station identifier (UUID or Stream URL)
    private val hourlyHeatmaps = mutableMapOf<String, FloatArray>()
    private val pureMusicOverrides = mutableMapOf<String, Boolean>()
    private val learnedPreRolls = mutableMapOf<String, Boolean>()
    private val learnedNewsWindows = mutableMapOf<String, String>()

    // Observable flow for pure music changes so UI updates live
    private val _pureMusicUpdateCounter = MutableStateFlow(0)
    val pureMusicUpdateCounter: StateFlow<Int> = _pureMusicUpdateCounter.asStateFlow()

    init {
        loadData()
    }

    private fun getStationKey(station: Station): String {
        return station.stationuuid?.takeIf { it.isNotBlank() } ?: station.getStreamUrl()
    }

    private fun loadData() {
        // Load Heatmaps
        val heatmapsJson = prefs.getString("hourly_heatmaps", null)
        if (heatmapsJson != null) {
            try {
                val type = object : TypeToken<Map<String, List<Float>>>() {}.type
                val map: Map<String, List<Float>> = gson.fromJson(heatmapsJson, type) ?: emptyMap()
                map.forEach { (k, list) ->
                    val arr = FloatArray(60)
                    list.take(60).forEachIndexed { index, fl -> arr[index] = fl }
                    hourlyHeatmaps[k] = arr
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load Overrides
        val overridesJson = prefs.getString("pure_music_overrides", null)
        if (overridesJson != null) {
            try {
                val type = object : TypeToken<Map<String, Boolean>>() {}.type
                val map: Map<String, Boolean> = gson.fromJson(overridesJson, type) ?: emptyMap()
                pureMusicOverrides.putAll(map)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load Pre-rolls
        val preRollsJson = prefs.getString("learned_prerolls", null)
        if (preRollsJson != null) {
            try {
                val type = object : TypeToken<Map<String, Boolean>>() {}.type
                val map: Map<String, Boolean> = gson.fromJson(preRollsJson, type) ?: emptyMap()
                learnedPreRolls.putAll(map)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load News Windows
        val newsJson = prefs.getString("learned_news_windows", null)
        if (newsJson != null) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String> = gson.fromJson(newsJson, type) ?: emptyMap()
                learnedNewsWindows.putAll(map)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setSmartSwitchEnabled(enabled: Boolean) {
        _isSmartSwitchEnabled.value = enabled
        prefs.edit().putBoolean("smart_switch_enabled", enabled).apply()
    }

    fun setSpeechToleranceSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 30)
        _speechToleranceSeconds.value = clamped
        prefs.edit().putInt("speech_tolerance_seconds", clamped).apply()
    }

    fun setBounceBackMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 15)
        _bounceBackMinutes.value = clamped
        prefs.edit().putInt("bounce_back_minutes", clamped).apply()
    }

    fun setSpeechSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.25f, 0.75f)
        _speechSensitivity.value = clamped
        prefs.edit().putFloat("speech_sensitivity", clamped).apply()
    }

    fun setSpeechResetHoldSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 10)
        _speechResetHoldSeconds.value = clamped
        prefs.edit().putInt("speech_reset_hold_seconds", clamped).apply()
    }

    fun setSmartSwitchStrategy(strategy: String) {
        _smartSwitchStrategy.value = strategy
        prefs.edit().putString("smart_switch_strategy", strategy).apply()
    }

    fun setSpecificFallbackKey(key: String?) {
        _specificFallbackKey.value = key
        prefs.edit().putString("specific_fallback_key", key).apply()
    }

    /**
     * Determines whether a station is classified as 100% Music (no news/ads).
     * Checks user overrides first, then model flag / heuristics.
     */
    fun isStationPureMusic(station: Station): Boolean {
        val key = getStationKey(station)
        pureMusicOverrides[key]?.let { return it }
        return station.isPureMusicStation()
    }

    /**
     * Toggles 100% Music status for a station.
     */
    fun togglePureMusic(station: Station): Boolean {
        val key = getStationKey(station)
        val current = isStationPureMusic(station)
        val newStatus = !current
        pureMusicOverrides[key] = newStatus
        prefs.edit().putString("pure_music_overrides", gson.toJson(pureMusicOverrides)).apply()
        _pureMusicUpdateCounter.value++
        return newStatus
    }

    /**
     * Checks if a station has a pre-roll ad on connect.
     */
    fun hasPreRollAd(station: Station): Boolean {
        val key = getStationKey(station)
        learnedPreRolls[key]?.let { return it }
        return !station.isAdFree()
    }

    fun recordPreRollObservation(station: Station, hadAdAtStart: Boolean) {
        val key = getStationKey(station)
        learnedPreRolls[key] = hadAdAtStart
        prefs.edit().putString("learned_prerolls", gson.toJson(learnedPreRolls)).apply()
    }

    /**
     * Returns speech probability for a station at a given minute of the hour (0..59).
     */
    fun getSpeechProbabilityAtMinute(station: Station, minuteOfHour: Int): Float {
        val clampedMinute = minuteOfHour.coerceIn(0, 59)
        val key = getStationKey(station)
        val heatmap = hourlyHeatmaps[key]
        if (heatmap != null) {
            return heatmap[clampedMinute]
        }

        // Seed heuristic for unvisited stations
        if (isStationPureMusic(station)) return 0.05f

        // Standard European radio hourly news window (:57 to :04)
        if (clampedMinute in 57..59 || clampedMinute in 0..4) {
            return 0.85f
        }
        // Half-hour news (:29 to :32)
        if (clampedMinute in 29..32) {
            return 0.45f
        }
        return 0.15f
    }

    /**
     * Records a live speech or music observation for the station at the current minute.
     * Uses Exponential Moving Average (EMA) to adapt over days and weeks.
     */
    fun recordSpeechSample(station: Station, minuteOfHour: Int, isSpeech: Boolean) {
        val key = getStationKey(station)
        val clampedMinute = minuteOfHour.coerceIn(0, 59)
        val heatmap = hourlyHeatmaps.getOrPut(key) {
            FloatArray(60) { m ->
                if (isStationPureMusic(station)) 0.05f
                else if (m in 57..59 || m in 0..4) 0.85f
                else 0.15f
            }
        }

        val target = if (isSpeech) 1.0f else 0.0f
        val alpha = 0.12f // Learning rate
        heatmap[clampedMinute] = (1 - alpha) * heatmap[clampedMinute] + alpha * target

        // If high speech is learned around top-of-hour, record news window string
        if (heatmap[0] > 0.6f && heatmap[1] > 0.6f) {
            learnedNewsWindows[key] = ":00 - :04"
        }

        // Save periodically (e.g. on every sample or debounce)
        val serializedMap = hourlyHeatmaps.mapValues { it.value.toList() }
        prefs.edit()
            .putString("hourly_heatmaps", gson.toJson(serializedMap))
            .putString("learned_news_windows", gson.toJson(learnedNewsWindows))
            .apply()
    }

    fun getLearnedNewsWindow(station: Station): String? {
        val key = getStationKey(station)
        return learnedNewsWindows[key] ?: station.learnedNewsWindow
    }

    companion object {
        fun getCurrentMinuteOfHour(): Int {
            return Calendar.getInstance().get(Calendar.MINUTE)
        }
    }
}
