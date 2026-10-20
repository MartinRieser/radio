package com.pulsecast.radio.playback.audio

import com.pulsecast.radio.data.StationIntelligenceRepository
import com.pulsecast.radio.model.Station
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import kotlin.math.abs

/**
 * Automated Station Signal Mining & Jingle Learning Engine.
 * Automatically records audio streams around top-of-hour transitions (:59:40 to :00:10),
 * extracts acoustic spectral fingerprints, clusters recurring station idents/jingles,
 * and trains the live classifier on-the-fly.
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

    fun onStationChanged(station: Station?) {
        activeStation = station
        frameEnergyHistory.clear()
        sampleCounter = 0
    }

    /**
     * Process live PCM buffer for automated signal mining.
     */
    fun processPcmBuffer(buffer: ByteBuffer, sampleRate: Int, channelCount: Int) {
        val station = activeStation ?: return
        val mode = intelligenceRepository.miningMode.value
        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)

        // Configurable Mining Window: Continuous (100%), Broad (:55-:05 & :27-:33), Standard (:57-:03)
        val inMiningWindow = when (mode) {
            "continuous" -> true // Continuous training (100% of the time while listening)
            "window_broad" -> (currentMinute in 55..59 || currentMinute in 0..5 || currentMinute in 27..33)
            else -> (currentMinute in 57..59 || currentMinute in 0..3)
        }

        if (!inMiningWindow) {
            if (isMiningActive && frameEnergyHistory.isNotEmpty()) {
                // Finalize mining session for this period
                finalizeMiningSession(station)
            }
            isMiningActive = false
            return
        }

        isMiningActive = true

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
        }
    }

    private fun finalizeMiningSession(station: Station) {
        if (frameEnergyHistory.size < 50) return

        // Extract peak energy fingerprint signature
        val peakFingerprint = extractPeakFingerprint(frameEnergyHistory)
        if (peakFingerprint.isNotBlank()) {
            intelligenceRepository.recordLearnedJingleSignature(station, peakFingerprint)
        }
        frameEnergyHistory.clear()
    }

    private fun extractPeakFingerprint(history: List<FloatArray>): String {
        // Find 2-second burst with highest energy modulation (typical jingle / stinger)
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

        // Quantize best 20 subframes into a compact hash string
        val sb = StringBuilder()
        for (j in 0 until 20 step 2) {
            val bands = history[bestIndex + j]
            val maxBand = bands.indices.maxByOrNull { bands[it] } ?: 0
            sb.append(maxBand.toString(16))
        }
        return sb.toString()
    }
}
