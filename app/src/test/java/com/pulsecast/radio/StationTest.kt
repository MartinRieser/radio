package com.pulsecast.radio

import com.pulsecast.radio.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationTest {

    @Test
    fun testStationPureMusicClassification() {
        val swissPop = Station(
            name = "Radio Swiss Pop",
            url = "https://stream.srg-ssr.ch/rsp",
            isPureMusic = true
        )
        assertTrue("Radio Swiss Pop should be classified as pure music", swissPop.isPureMusicStation())

        val somaFm = Station(
            name = "SomaFM Groove Salad",
            url = "https://ice1.somafm.com/groovesalad",
            tags = "ambient, chillout"
        )
        assertTrue("SomaFM should be recognized as pure music", somaFm.isPureMusicStation())

        val lofiCafe = Station(
            name = "Lofi Cafe Radio",
            url = "https://streams.ilovemusic.de/lofi",
            tags = "lo-fi, chillhop"
        )
        assertTrue("Lofi Cafe should be recognized as pure music", lofiCafe.isPureMusicStation())

        val oe3 = Station(
            name = "Hitradio Ö3",
            url = "https://orf.at/oe3",
            tags = "pop, hits, chart, news"
        )
        assertFalse("Hitradio Ö3 has news and should not be pure music", oe3.isPureMusicStation())

        val bbcWorld = Station(
            name = "BBC World Service",
            url = "https://bbc.co.uk/stream",
            tags = "news, talk, world"
        )
        assertFalse("BBC World Service is talk/news and should not be pure music", bbcWorld.isPureMusicStation())
    }

    @Test
    fun testStreamUrlResolution() {
        val station = Station(
            name = "Test Radio",
            url = "https://stream.example.com/live",
            urlResolved = "https://stream-resolved.example.com/live"
        )
        assertEquals("https://stream-resolved.example.com/live", station.getStreamUrl())

        val fallbackStation = Station(
            name = "Fallback Radio",
            url = "https://stream.example.com/live"
        )
        assertEquals("https://stream.example.com/live", fallbackStation.getStreamUrl())
    }

    @Test
    fun testCountryFlagFormatting() {
        val germanStation = Station(name = "Radio DE", countrycode = "DE")
        assertEquals("🇩🇪", germanStation.getCountryFlag())

        val swissStation = Station(name = "Radio CH", countrycode = "CH")
        assertEquals("🇨🇭", swissStation.getCountryFlag())

        val austrianStation = Station(name = "Radio AT", countrycode = "AT")
        assertEquals("🇦🇹", austrianStation.getCountryFlag())
    }

    @Test
    fun testFavoriteTypeClassification() {
        val defaultStation = Station(name = "Station Default")
        assertEquals(0, defaultStation.getEffectiveFavoriteType())

        val fav1Station = Station(name = "Station Fav 1", favoriteType = 1)
        assertEquals(1, fav1Station.getEffectiveFavoriteType())

        val fav2Station = Station(name = "Station Fav 2", favoriteType = 2)
        assertEquals(2, fav2Station.getEffectiveFavoriteType())

        val fav3Station = Station(name = "Station Fav 3", favoriteType = 3)
        assertEquals(3, fav3Station.getEffectiveFavoriteType())

        val legacyFavStation = Station(name = "Station Legacy Fav", isFavorite = true, favoriteType = 0)
        assertEquals(1, legacyFavStation.getEffectiveFavoriteType())
    }

    @Test
    fun testIsSameStationAndNullUuidSafety() {
        val stationA = Station(stationuuid = null, name = "Energy (Zürich)", url = "https://stream.energy.ch/zh")
        val stationB = Station(stationuuid = null, name = "Energy (Basel)", url = "https://stream.energy.ch/bs")
        val stationACopy = Station(stationuuid = null, name = "Energy (Zürich)", url = "https://stream.energy.ch/zh")

        assertFalse("Different stations with null stationuuid must not match", stationA.isSameStation(stationB))
        assertTrue("Identical stations with null stationuuid must match by URL", stationA.isSameStation(stationACopy))
        assertFalse("Comparison with null must be false", stationA.isSameStation(null))
        assertTrue("matchesIdOrUrl should match stream URL", stationA.matchesIdOrUrl("https://stream.energy.ch/zh"))
        assertFalse("matchesIdOrUrl should not match null", stationA.matchesIdOrUrl(null))
    }
}
