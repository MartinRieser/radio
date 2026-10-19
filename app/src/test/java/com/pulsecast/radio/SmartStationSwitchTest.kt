package com.pulsecast.radio

import com.pulsecast.radio.model.Station
import com.pulsecast.radio.playback.audio.AudioContentClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class SmartStationSwitchTest {

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
            tags = "pop, hits, chart, news",
            learnedNewsWindow = ":00 - :04"
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
    fun testAudioClassifierMetadataSignals() {
        val classifier = AudioContentClassifier()

        // 1. Commercial / Ad Keyword
        classifier.setStationContext(trackTitle = "Werbung", isAdFree = false)
        assertTrue("Speech should be detected when track title is 'Werbung'", classifier.isSpeechDetected)
        assertTrue("Speech confidence should be high for ad title", classifier.speechConfidence >= 0.55f)

        // 2. News Keyword
        classifier.setStationContext(trackTitle = "Nachrichten", isAdFree = false)
        assertTrue("Speech should be detected for 'Nachrichten'", classifier.isSpeechDetected)

        // 3. Traffic / Stau Keyword
        classifier.setStationContext(trackTitle = "Verkehr & Stau", isAdFree = false)
        assertTrue("Speech should be detected for traffic report", classifier.isSpeechDetected)

        // 4. Legitimate Song Metadata
        classifier.setStationContext(trackTitle = "Coldplay - Viva La Vida", isAdFree = false)
        assertFalse("Music should not trigger speech detection for valid song title", classifier.isSpeechDetected)
        assertTrue("Music confidence should be high for valid song title", classifier.musicConfidence > 0.5f)
    }

    @Test
    fun testAudioClassifierPcmHarmonicTone() {
        val classifier = AudioContentClassifier()
        classifier.setStationContext(trackTitle = "Instrumental Track", isAdFree = true)

        // Generate 1 second of continuous 440 Hz sine wave (pure music tone)
        val sampleRate = 16000
        val numSamples = sampleRate
        val byteBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * 440.0 * i / sampleRate
            val sample = (sin(angle) * 16000).toInt().toShort()
            byteBuffer.putShort(sample)
        }
        byteBuffer.flip()

        classifier.processPcmBuffer(byteBuffer, sampleRate, 1)

        // Pure continuous tone has sustained energy and no speech syllabic pauses
        assertFalse("Continuous tone should not be classified as speech", classifier.isSpeechDetected)
        assertTrue("Music confidence should remain high for continuous tone", classifier.musicConfidence >= 0.5f)
    }
}
