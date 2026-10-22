package com.pulsecast.radio.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pulsecast.radio.model.Station
import java.io.File

object CuratedStations {

    fun load(context: Context): List<Station> {
        return try {
            val assetJson = context.assets.open("curated_stations.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Station>>() {}.type
            val assetList: List<Station> = Gson().fromJson(assetJson, listType) ?: fallbackStations()

            val cachedFile = File(context.filesDir, "curated_stations.json")
            if (cachedFile.exists() && cachedFile.length() > 0) {
                val cachedJson = cachedFile.bufferedReader().use { it.readText() }
                val cachedList: List<Station> = Gson().fromJson(cachedJson, listType) ?: assetList
                if (cachedList.size >= assetList.size) {
                    return cachedList
                }
            }
            assetList
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackStations()
        }
    }

    fun updateCachedStations(context: Context, jsonString: String): Boolean {
        return try {
            val listType = object : TypeToken<List<Station>>() {}.type
            val parsed: List<Station> = Gson().fromJson(jsonString, listType) ?: return false
            if (parsed.isEmpty()) return false
            val file = File(context.filesDir, "curated_stations.json")
            file.writeText(jsonString)
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
            ),
            Station(
                stationuuid = "0de82079-04e7-407b-b616-e0726eba5244",
                name = "Bayern 3",
                urlResolved = "https://dispatcher.rndfnk.com/br/br3/live/mp3/mid",
                country = "Germany",
                countrycode = "DE",
                tags = "pop, hits, bavaria, adult contemporary",
                codec = "MP3",
                bitrate = 128,
                favicon = "https://api.ardmediathek.de/image-service/images/urn:ard:image:52fd25fc45de7c6a?w=512",
                votes = 2150
            )
        )
    }
}
