package com.pulsecast.radio.playback.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time on-device audio content classifier.
 * Discriminates between speech/commercials/news and continuous music by combining:
 * 1. Acoustical DSP features (Low-energy frame ratio, Zero-crossing rate variance, RMS dynamic flux)
 * 2. ICY stream metadata heuristics (news/ad keywords vs artist-song patterns)
 * 3. Station schedule and minute-of-the-hour prior
 */
class AudioContentClassifier {

    private var currentTrackTitle: String = ""
    private var isAdFreeStation: Boolean = false

    // Sliding window of acoustic feature frames (each ~100ms)
    private val windowSize = 10 // ~1 second evaluation window
    private val subFrameRms = FloatArray(windowSize)
    private val subFrameZcr = FloatArray(windowSize)
    private var frameIndex = 0

    // Downsampling buffer for incoming PCM bytes
    private var sampleAccumulator = 0f
    private var sampleCount = 0
    private var lastSign = 0
    private var zeroCrossings = 0

    @Volatile
    var speechConfidence: Float = 0.1f
        private set

    @Volatile
    var musicConfidence: Float = 0.9f
        private set

    @Volatile
    var isSpeechDetected: Boolean = false
        private set

    @Volatile
    var detectionReason: String = "Musik"
        private set

    fun setStationContext(trackTitle: String, isAdFree: Boolean) {
        this.currentTrackTitle = trackTitle
        this.isAdFreeStation = isAdFree
        evaluateClassification()
    }

    /**
     * Processes raw 16-bit PCM bytes received from PcmTapAudioProcessor.
     */
    fun processPcmBuffer(buffer: ByteBuffer, sampleRate: Int, channelCount: Int) {
        if (buffer.remaining() < 2) return

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = buffer.asShortBuffer()
        val totalSamples = shortBuffer.remaining()

        var sumSquare = 0.0
        val step = (channelCount * (sampleRate / 16000)).coerceAtLeast(1) // Downsample to ~16kHz mono

        var i = 0
        while (i < totalSamples) {
            val sample = shortBuffer.get(i).toFloat() / 32768.0f // Normalized [-1.0, 1.0]
            sumSquare += (sample * sample)

            val currentSign = if (sample >= 0) 1 else -1
            if (lastSign != 0 && currentSign != lastSign) {
                zeroCrossings++
            }
            lastSign = currentSign

            sampleAccumulator += abs(sample)
            sampleCount++
            i += step
        }

        // Subframe evaluation every ~1600 samples (~100ms)
        if (sampleCount >= 1600) {
            val rms = sqrt(sumSquare / sampleCount).toFloat()
            val zcr = zeroCrossings.toFloat() / sampleCount

            subFrameRms[frameIndex % windowSize] = rms
            subFrameZcr[frameIndex % windowSize] = zcr
            frameIndex++

            sampleAccumulator = 0f
            sampleCount = 0
            zeroCrossings = 0

            if (frameIndex >= windowSize) {
                evaluateClassification()
            }
        }
    }

    private fun evaluateClassification() {
        // --- 1. ICY Metadata Analysis ---
        val title = currentTrackTitle.lowercase().trim()
        val adKeywords = listOf(
            "werbung", "commercial", "ad", "ads", "spot", "nachrichten", "news",
            "verkehr", "traffic", "stau", "blitzer", "wetter", "weather",
            "jingle", "gewinnspiel", "station id"
        )

        val hasAdKeyword = adKeywords.any { keyword ->
            title.contains("\\b$keyword\\b".toRegex()) || title == keyword
        }

        // Strong sign: ICY title is clearly an artist - track format
        val isSongPattern = title.contains(" - ") || title.contains(" feat. ") || title.contains(" vs. ")

        if (hasAdKeyword) {
            speechConfidence = 0.95f
            musicConfidence = 0.05f
            isSpeechDetected = true
            detectionReason = "Werbung / News (Metadaten)"
            return
        }

        // --- 2. Acoustic DSP Analysis ---
        val avgRms = subFrameRms.average().toFloat()
        if (avgRms < 0.005f) {
            // Near silence / stream pause -> don't trigger speech false-positive
            speechConfidence = 0.1f
            musicConfidence = 0.8f
            isSpeechDetected = false
            detectionReason = if (isSongPattern) "Musik ($currentTrackTitle)" else "Stille / Pause"
            return
        }

        // Low-Energy Frame Rate: Fraction of frames with RMS < 0.5 * avgRms
        var lowEnergyFrames = 0
        for (rms in subFrameRms) {
            if (rms < avgRms * 0.5f) {
                lowEnergyFrames++
            }
        }
        val lowEnergyRate = lowEnergyFrames.toFloat() / windowSize

        // Zero Crossing Rate Variance & Mean
        val avgZcr = subFrameZcr.average().toFloat()
        var zcrVariance = 0f
        for (z in subFrameZcr) {
            val diff = z - avgZcr
            zcrVariance += diff * diff
        }
        zcrVariance /= windowSize

        // RMS Coefficient of Variation (CV = StdDev / Mean RMS)
        // Detects rapid syllabic voice fluctuations even when background music is present
        var rmsVarianceSum = 0f
        for (rms in subFrameRms) {
            val diff = rms - avgRms
            rmsVarianceSum += diff * diff
        }
        val rmsStdDev = sqrt(rmsVarianceSum / windowSize)
        val rmsCoeffVar = if (avgRms > 0.001f) (rmsStdDev / avgRms) else 0f

        var acousticSpeechScore = 0.0f

        // 1. Syllabic pauses in pure speech OR energy fluctuations of speech over background music
        if (rmsCoeffVar >= 0.22f || lowEnergyRate >= 0.28f) {
            acousticSpeechScore += 0.55f // High speech / voiceover probability
        } else if (rmsCoeffVar >= 0.15f || lowEnergyRate >= 0.18f) {
            acousticSpeechScore += 0.35f // Moderate speech over music
        }

        // 2. Fricative Consonants & Speech ZCR Dynamics
        if (zcrVariance > 0.004f || avgZcr > 0.16f) {
            acousticSpeechScore += 0.40f
        } else if (zcrVariance > 0.002f || avgZcr > 0.10f) {
            acousticSpeechScore += 0.20f
        }

        // Only apply song pattern discount if acoustic speech score is very low
        var metadataAdScore = 0.0f
        if (isSongPattern && acousticSpeechScore < 0.30f) {
            metadataAdScore = -0.15f
        }

        // --- 3. Minute-of-Hour Temporal Prior ---
        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
        var timePrior = 0.0f
        if (currentMinute in 57..59 || currentMinute in 0..4) {
            // Top of the hour: prime commercial and news window
            timePrior = 0.20f
        } else if (currentMinute in 29..32) {
            // Half hour news window
            timePrior = 0.10f
        }

        // Acoustic audio waveform is the primary source of truth (75% weight)
        var totalSpeech = (acousticSpeechScore * 0.75f) + (metadataAdScore * 0.10f) + (timePrior * 0.15f)
        totalSpeech = totalSpeech.coerceIn(0.0f, 1.0f)

        speechConfidence = totalSpeech
        musicConfidence = 1.0f - totalSpeech

        val wasSpeech = isSpeechDetected
        isSpeechDetected = speechConfidence >= 0.40f

        detectionReason = when {
            hasAdKeyword -> "Werbung / News (Metadaten)"
            isSpeechDetected && (currentMinute in 57..59 || currentMinute in 0..4) -> "Nachrichten / Talk (:00)"
            isSpeechDetected -> "Sprache / Talk"
            isSongPattern -> "Musik ($currentTrackTitle)"
            else -> "Musik"
        }
    }

    fun reset() {
        subFrameRms.fill(0f)
        subFrameZcr.fill(0f)
        frameIndex = 0
        sampleAccumulator = 0f
        sampleCount = 0
        lastSign = 0
        zeroCrossings = 0
        speechConfidence = 0.1f
        musicConfidence = 0.9f
        isSpeechDetected = false
        detectionReason = "Musik"
    }
}
