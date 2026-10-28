package com.pulsecast.radio

import com.pulsecast.radio.model.Station
import com.pulsecast.radio.playback.RadioMetadataFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioMetadataFormatterTest {

    private val swr3 = Station(
        name = "SWR3",
        country = "Germany",
        countrycode = "DE",
        tags = "pop, rock, hits"
    )

    private val rockAntenne = Station(
        name = "Rock Antenne",
        country = "Germany",
        countrycode = "DE",
        tags = "rock, classic rock"
    )

    @Test
    fun testArtistAndTitleParsingForSubaruScreen() {
        val result = RadioMetadataFormatter.format("Queen - Bohemian Rhapsody", swr3)

        // Line 1: Song title
        assertEquals("Bohemian Rhapsody", result.title)
        // Line 2: Artist (triggers Gracenote artist image on Subaru if available)
        assertEquals("Queen", result.artist)
        // Line 3: Station indicator
        assertEquals("📻 SWR3", result.album)
        // In-App UI display
        assertEquals("Queen - Bohemian Rhapsody", result.appTrackTitle)
    }

    @Test
    fun testEnDashAndEmDashSupport() {
        val resultEnDash = RadioMetadataFormatter.format("Pink Floyd – Comfortably Numb", rockAntenne)
        assertEquals("Comfortably Numb", resultEnDash.title)
        assertEquals("Pink Floyd", resultEnDash.artist)
        assertEquals("📻 Rock Antenne", resultEnDash.album)
        assertEquals("Pink Floyd - Comfortably Numb", resultEnDash.appTrackTitle)

        val resultEmDash = RadioMetadataFormatter.format("AC/DC — Thunderstruck", rockAntenne)
        assertEquals("Thunderstruck", resultEmDash.title)
        assertEquals("AC/DC", resultEmDash.artist)
        assertEquals("📻 Rock Antenne", resultEmDash.album)
        assertEquals("AC/DC - Thunderstruck", resultEmDash.appTrackTitle)
    }

    @Test
    fun testStationPrefixStripping() {
        val result = RadioMetadataFormatter.format("SWR3 - Queen - Radio Ga Ga", swr3)
        assertEquals("Radio Ga Ga", result.title)
        assertEquals("Queen", result.artist)
        assertEquals("📻 SWR3", result.album)
        assertEquals("Queen - Radio Ga Ga", result.appTrackTitle)

        val resultColon = RadioMetadataFormatter.format("SWR3: Queen - Radio Ga Ga", swr3)
        assertEquals("Radio Ga Ga", resultColon.title)
        assertEquals("Queen", resultColon.artist)
        assertEquals("📻 SWR3", resultColon.album)
    }

    @Test
    fun testStationShowNameWithoutSong() {
        val result = RadioMetadataFormatter.format("SWR3 - Popnacht", swr3)
        assertEquals("Popnacht", result.title)
        assertEquals("SWR3", result.artist)
        assertEquals("Germany", result.album)
        assertEquals("Popnacht", result.appTrackTitle)
    }

    @Test
    fun testSingleInfoText() {
        val result = RadioMetadataFormatter.format("Nachrichten um 8", swr3)
        assertEquals("Nachrichten um 8", result.title)
        assertEquals("SWR3", result.artist)
        assertEquals("Germany", result.album)
        assertEquals("Nachrichten um 8", result.appTrackTitle)
    }

    @Test
    fun testIdleOrNoTrackMetadata() {
        val nullResult = RadioMetadataFormatter.format(null, swr3)
        assertEquals("SWR3", nullResult.title)
        assertEquals("Germany", nullResult.artist)
        assertEquals("PulseCast Radio", nullResult.album)
        assertEquals("", nullResult.appTrackTitle)

        val blankResult = RadioMetadataFormatter.format("   ", swr3)
        assertEquals("SWR3", blankResult.title)
        assertEquals("Germany", blankResult.artist)
        assertEquals("PulseCast Radio", blankResult.album)
        assertEquals("", blankResult.appTrackTitle)

        val sameNameResult = RadioMetadataFormatter.format("SWR3", swr3)
        assertEquals("SWR3", sameNameResult.title)
        assertEquals("Germany", sameNameResult.artist)
        assertEquals("PulseCast Radio", sameNameResult.album)
        assertEquals("", sameNameResult.appTrackTitle)
    }

    @Test
    fun testCleaningPunctuationAndQuotes() {
        val result = RadioMetadataFormatter.format("\"AC/DC - Highway to Hell\";", rockAntenne)
        assertEquals("Highway to Hell", result.title)
        assertEquals("AC/DC", result.artist)
        assertEquals("📻 Rock Antenne", result.album)
        assertEquals("AC/DC - Highway to Hell", result.appTrackTitle)
    }
}

