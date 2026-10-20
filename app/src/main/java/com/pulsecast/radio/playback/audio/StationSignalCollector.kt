package com.pulsecast.radio.playback.audio

import com.pulsecast.radio.data.StationIntelligenceRepository
import com.pulsecast.radio.model.Station
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import kotlin.math.abs

/**
 * Automated Station Signal Mining & Live Jingle Recognition Engine.
 * 1. Matches live incoming 2-second audio frames against PC-learned jingle signatures for instant switching.
 * 2. Extracts acoustic spectral fingerprints around top-of-hour transitions.
 */
class StationSignalCollector(
    private val intelligenceRepository: StationIntelligenceRepository
) {
    private var activeStation: Station? = null
    private var isMiningActive = false

    // 16-band energy accumulator buffer (~100ms frames)
    private val frameEnergyHistory = mutableListOf<FloatArray>()
    private var sampleCounter = 0
    private val bandEnergies = FloatArray(16)

    @Volatile
    var isJingleMatched: Boolean = false
        private set

    fun onStationChanged(station: Station?) {
        activeStation = station
        frameEnergyHistory.clear()
        sampleCounter = 0
        isJingleMatched = false
    }

    /**
     * Process live PCM buffer for live jingle recognition and automated signal mining.
     */
    fun processPcmBuffer(buffer: ByteBuffer, sampleRate: Int, channelCount: Int) {
        val station = activeStation ?: return

        if (buffer.remaining() < 2) return
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = buffer.asShortBuffer()
        val totalSamples = shortBuffer.remaining()
        val step = (channelCount * (sampleRate / 16000)).coerceAtLeast(1)

        var i = 0
        while (i < totalSamples) {
            val sample = abs(shortBuffer.get(i).toFloat() / 32768.0f)
            val bandIndex = (i / step) % 16
            bandEnergies[bandIndex] += sample
            sampleCounter++
            i += step
        }

        if (sampleCounter >= 1600) {
            val frameBands = FloatArray(16) { b -> bandEnergies[b] / 100f }
            frameEnergyHistory.add(frameBands)
            bandEnergies.fill(0f)
            sampleCounter = 0

            // Keep sliding window of last 30 seconds (~300 subframes)
            if (frameEnergyHistory.size > 300) {
                frameEnergyHistory.removeAt(0)
            }

            // Live Jingle Matching against PC-trained fingerprint every 500ms
            if (frameEnergyHistory.size >= 20 && frameEnergyHistory.size % 5 == 0) {
                val learnedJingle = intelligenceRepository.getLearnedJingleSignature(station)
                if (!learnedJingle.isNullOrBlank() && learnedJingle.length == 10) {
                    val liveSignature = quantizeRecent2Seconds(frameEnergyHistory)
                    isJingleMatched = isSignatureSimilar(liveSignature, learnedJingle)
                } else {
                    isJingleMatched = false
                }
            }

            val mode = intelligenceRepository.miningMode.value
            val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
            val inMiningWindow = when (mode) {
                "continuous" -> true
                "window_broad" -> (currentMinute in 55..59 || currentMinute in 0..5 || currentMinute in 27..33)
                else -> (currentMinute in 57..59 || currentMinute in 0..3)
            }

            if (!inMiningWindow && isMiningActive && frameEnergyHistory.size >= 50) {
                finalizeMiningSession(station)
                isMiningActive = false
            } else if (inMiningWindow) {
                isMiningActive = true
            }
        }
    }

    private fun quantizeRecent2Seconds(history: List<FloatArray>): String {
        val startIdx = (history.size - 20).coerceAtLeast(0)
        val sb = StringBuilder()
        for (j in 0 until 20 step 2) {
            val bands = history[startIdx + j]
            val maxBand = bands.indices.maxByOrNull { bands[it] } ?: 0
            sb.append(maxBand.toString(16))
        }
        return sb.toString()
    }

    private fun isSignatureSimilar(liveHex: String, learnedHex: String): Boolean {
        if (liveHex.length != 10 || learnedHex.length != 10) return false
        var matches = 0
        for (i in 0 until 10) {
            val b1 = liveHex[i].digitToIntOrNull(16) ?: continue
            val b2 = learnedHex[i].digitToIntOrNull(16) ?: continue
            if (abs(b1 - b2) <= 1) {
                matches++
            }
        }
        return matches >= 8 // At least 80% spectral band trajectory agreement
    }

    private fun finalizeMiningSession(station: Station) {
        if (frameEnergyHistory.size < 50) return

        val peakFingerprint = extractPeakFingerprint(frameEnergyHistory)
        if (peakFingerprint.isNotBlank()) {
            intelligenceRepository.recordLearnedJingleSignature(station, peakFingerprint)
        }
        frameEnergyHistory.clear()
    }

    private fun extractPeakFingerprint(history: List<FloatArray>): String {
        var maxVariance = 0f
        var bestIndex = 0

        for (i in 0 until (history.size - 20)) {
            var sum = 0f
            var sumSq = 0f
            for (j in 0 until 20) {
                val energy = history[i + j].sum()
                sum += energy
                sumSq += energy * energy
            }
            val mean = sum / 20f
            val variance = (sumSq / 20f) - (mean * mean)
            if (variance > maxVariance) {
                maxVariance = variance
                bestIndex = i
            }
        }

        if (maxVariance < 0.05f) return ""

        val sb = StringBuilder()
        for (j in 0 until 20 step 2) {
            val bands = history[bestIndex + j]
            val maxBand = bands.indices.maxByOrNull { bands[it] } ?: 0
            sb.append(maxBand.toString(16))
        }
        return sb.toString()
    }
}
