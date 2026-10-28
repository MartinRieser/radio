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
}
