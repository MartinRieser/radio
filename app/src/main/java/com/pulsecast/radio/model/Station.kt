package com.pulsecast.radio.model

import com.google.gson.annotations.SerializedName

data class Station(
    @SerializedName("stationuuid")
    val stationuuid: String? = null,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("url_resolved")
    val urlResolved: String? = null,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("homepage")
    val homepage: String? = null,

    @SerializedName("favicon")
    val favicon: String? = null,

    @SerializedName("tags")
    val tags: String? = null,

    @SerializedName("country")
    val country: String? = null,

    @SerializedName("countrycode")
    val countrycode: String? = null,

    @SerializedName("codec")
    val codec: String? = null,

    @SerializedName("bitrate")
    val bitrate: Int = 0,

    @SerializedName("votes")
    val votes: Int = 0,

    @SerializedName("clickcount")
    val clickcount: Int = 0,

    @SerializedName("geo_lat")
    val geoLat: Double? = null,

    @SerializedName("geo_long")
    val geoLong: Double? = null,

    // Local state fields
    val isFavorite: Boolean = false,
    val lastPlayed: Long? = null,
    val playDuration: Int = 0,
    @SerializedName("is_pure_music")
    val isPureMusic: Boolean = false,
    val learnedNewsWindow: String? = null
) {
    /**
     * Checks if this station is dedicated to continuous music with no news/commercial breaks.
     */
    fun isPureMusicStation(): Boolean {
        if (isPureMusic) return true
        val n = name.lowercase()
        val t = (tags ?: "").lowercase()
        if (n.contains("news") || n.contains("info") || n.contains("talk") || n.contains("world service")) {
            return false
        }
        val pureKeywords = listOf("somafm", "swiss pop", "swiss jazz", "swiss classic", "lofi cafe", "drone zone", "fip")
        if (pureKeywords.any { n.contains(it) }) return true
        if (t.contains("lo-fi") || t.contains("ambient") || t.contains("chillout") || t.contains("downtempo")) {
            return true
        }
        return false
    }

    /**
     * Resolves the primary streaming URL (fallback to url if url_resolved is null)
     */
    fun getStreamUrl(): String {
        return urlResolved?.takeIf { it.isNotBlank() } ?: (url ?: "")
    }

    /**
     * Pre-roll advertising detection:
     * Known commercial stream aggregators (TargetSpot, AdsWizz, Triton, StreamTheWorld, etc.)
     * inject unskippable 30s audio ads before the live stream connects.
     */
    fun isAdFree(): Boolean {
        val streamUrl = getStreamUrl().lowercase()
        val hp = (homepage ?: "").lowercase()
        val stationName = name.lowercase()

        // Explicit ad-free public / community broadcasters
        val adFreeKeywords = listOf(
            "somafm", "orf", "srg", "srg-ssr", "swr", "br", "wdr",
            "radio france", "fip", "bbc", "deutschlandfunk", "dlf", "kexp"
        )
        if (adFreeKeywords.any { stationName.contains(it) || hp.contains(it) || streamUrl.contains(it) }) {
            return true
        }

        // Known ad-injecting networks
        val adNetworkKeywords = listOf(
            "adswizz", "streamtheworld", "tritondigital", "targetspot",
            "audioaddict", "audacy", "iheart"
        )
        if (adNetworkKeywords.any { streamUrl.contains(it) }) {
            return false
        }

        // Bitrates >= 192 kbps without commercial tags are usually direct streams
        return bitrate >= 192
    }

    /**
     * Formats country code to emoji flag
     */
    fun getCountryFlag(): String {
        val code = countrycode?.uppercase()?.trim() ?: return "🌐"
        if (code.length != 2) return "🌐"
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}
