package com.pulsecast.radio.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pulsecast.radio.model.Station

object CuratedStations {

    fun load(context: Context): List<Station> {
        return try {
            val jsonString = context.assets.open("curated_stations.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Station>>() {}.type
            Gson().fromJson<List<Station>>(jsonString, listType) ?: fallbackStations()
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackStations()
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
                urlResolved = "https://stream.srg-ssr.ch/m/rsp/mp3_128",
                country = "Switzerland",
                countrycode = "CH",
                tags = "pop, hits, english, soft-pop",
                codec = "MP3",
                bitrate = 128,
                favicon = "https://www.radioswisspop.ch/dist/images/touch-icon-192x192.png",
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
                favicon = "https://www.br.de/static/br_de/images/touch-icons/bayern3-touch-icon-180x180.png",
                votes = 2150
            ),
            Station(
                stationuuid = "240d28b9-7858-48d2-a816-9cf8e1875fe8",
                name = "SWR3",
                urlResolved = "https://liveradio.swr.de/sw282p3/swr3/play.mp3",
                country = "Germany",
                countrycode = "DE",
                tags = "pop, rock, hits, comedy, info",
                codec = "MP3",
                bitrate = 128,
                favicon = "https://www.swr3.de/static/swr3/assets/icons/apple-touch-icon.png",
                votes = 2200
            ),
            Station(
                stationuuid = "0596bdc5-77fc-48ba-8653-e5e34bb86568",
                name = "SomaFM Groove Salad",
                urlResolved = "https://ice1.somafm.com/groovesalad-256-mp3",
                country = "United States",
                countrycode = "US",
                tags = "ambient, chillout, downtempo, electronic",
                codec = "MP3",
                bitrate = 256,
                favicon = "https://somafm.com/img3/groovesalad120.png",
                votes = 3200
            )
        )
    }
}
