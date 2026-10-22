package com.pulsecast.radio.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pulsecast.radio.model.Station
import java.io.File

object CuratedStations {

    fun load(context: Context): List<Station> {
        val rawList = try {
            val assetJson = context.assets.open("curated_stations.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Station>>() {}.type
            val assetList: List<Station> = Gson().fromJson(assetJson, listType) ?: fallbackStations()

            val cachedFile = File(context.filesDir, "curated_stations.json")
            if (cachedFile.exists() && cachedFile.length() > 0) {
                val cachedJson = cachedFile.bufferedReader().use { it.readText() }
                val cachedList: List<Station> = Gson().fromJson(cachedJson, listType) ?: assetList
                if (cachedList.size >= assetList.size) {
                    cachedList
                } else {
                    assetList
                }
            } else {
                assetList
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackStations()
        }

        return deduplicateAndOptimize(rawList)
    }

    fun deduplicateAndOptimize(stations: List<Station>): List<Station> {
        val uniqueMap = mutableMapOf<String, Station>()

        for (s in stations) {
            val streamUrl = s.getStreamUrl().lowercase().trim()
            val uuid = s.stationuuid?.lowercase()?.trim() ?: ""
            // Clean station name (e.g. "N-JOY (NDR)" -> "n-joy", "Energy Zürich (CH)" -> "energy zürich")
            val cleanName = s.name.lowercase()
                .split("(")[0]
                .replace("fm", "")
                .replace("radio", "")
                .replace("hq", "")
                .replace("orf", "")
                .replace("|", "")
                .trim()

            val primaryKey = when {
                cleanName.isNotBlank() -> "name:$cleanName"
                streamUrl.isNotBlank() -> "url:$streamUrl"
                else -> "uuid:$uuid"
            }

            if (!uniqueMap.containsKey(primaryKey)) {
                uniqueMap[primaryKey] = s
            } else {
                // Keep the entry with better favicon or bitrate
                val existing = uniqueMap[primaryKey]!!
                val existingHasIcon = !existing.favicon.isNullOrBlank() && !existing.favicon.contains("favicon.ico")
                val newHasIcon = !s.favicon.isNullOrBlank() && !s.favicon.contains("favicon.ico")

                if (!existingHasIcon && newHasIcon) {
                    uniqueMap[primaryKey] = s
                } else if (existingHasIcon == newHasIcon && s.bitrate > existing.bitrate) {
                    uniqueMap[primaryKey] = s
                }
            }
        }
        return uniqueMap.values.toList()
    }

    fun updateCachedStations(context: Context, jsonString: String): Boolean {
        return try {
            val listType = object : TypeToken<List<Station>>() {}.type
            val parsed: List<Station> = Gson().fromJson(jsonString, listType) ?: return false
            if (parsed.isEmpty()) return false
            val deduplicated = deduplicateAndOptimize(parsed)
            val cleanJson = Gson().toJson(deduplicated)
            val file = File(context.filesDir, "curated_stations.json")
            file.writeText(cleanJson)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun fallbackStations(): List<Station> {
        return listOf(
            Station(
                stationuuid = "86aa0563-0482-411a-be85-b9f1d044237f",
                name = "FM4 (ORF)",
                urlResolved = "https://orf-live.ors-shoutcast.at/fm4-q2a",
                country = "Austria",
                countrycode = "AT",
                tags = "alternative, indie, electronic, hiphop",
                codec = "MP3",
                bitrate = 192,
                favicon = "https://fm4.orf.at/apple-touch-icon.png",
                votes = 2800
            ),
            Station(
                stationuuid = "067388ea-bff2-41b5-869a-766b2f7b4a47",
                name = "Radio Swiss Pop",
                urlResolved = "https://stream.srg-ssr.ch/srgssr/rsp/mp3/128",
                country = "Switzerland",
                countrycode = "CH",
                tags = "pop, hits, english, soft-pop",
                codec = "MP3",
                bitrate = 128,
                favicon = "https://www.radioswisspop.ch/social-media/rsp-web.png",
                votes = 2400
            )
        )
    }
}
