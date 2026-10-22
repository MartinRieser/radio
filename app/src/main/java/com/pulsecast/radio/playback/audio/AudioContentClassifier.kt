package com.pulsecast.radio.playback.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time on-device audio content classifier.
 * Discriminates between speech/commercials/news and continuous music by combining:
 * 1. On-Device TensorFlow Lite YAMNet Neural Audio Classifier (98%+ accuracy on Speech vs Music)
 * 2. Acoustical DSP features (Low-energy frame ratio, Zero-crossing rate variance, RMS dynamic flux)
 * 3. ICY stream metadata heuristics (news/ad keywords vs artist-song patterns)
 * 4. Station schedule and minute-of-the-hour prior
 */
class AudioContentClassifier(context: Context? = null) {

    private var currentTrackTitle: String = ""
    private var isAdFreeStation: Boolean = false

    // TensorFlow Lite YAMNet Neural Audio Classifier
    private var tfliteClassifier: AudioClassifier? = null
    private var isTfliteLoaded: Boolean = false
    private var latestAiSpeechScore: Float = 0f
    private var latestAiMusicScore: Float = 0f

    // Sliding window of acoustic feature frames (each ~100ms)
    private val windowSize = 10 // ~1 second evaluation window
    private val subFrameRms = FloatArray(windowSize)
    private val subFrameZcr = FloatArray(windowSize)
    private var frameIndex = 0

    // Buffer for TFLite PCM conversion
    private val pcmSampleBuffer = FloatArray(1600)
    private var pcmSampleIndex = 0

    // Downsampling buffer for incoming PCM bytes
    private var sampleAccumulator = 0f
    private var sampleCount = 0
    private var lastSign = 0
    private var zeroCrossings = 0

    // Temporal smoothing & state hysteresis (anti-flicker)
    private var smoothedSpeechScore: Float = 0.1f

    @Volatile
    var sensitivityThreshold: Float = 0.35f

    @Volatile
    var stationMinutePrior: Float? = null

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
    var isPureSpeech: Boolean = false
        private set

    @Volatile
    var hasAdMetadata: Boolean = false
        private set

    @Volatile
    var detectionReason: String = "Musik"
        private set

    init {
        context?.let { ctx ->
            try {
                tfliteClassifier = AudioClassifier.createFromFile(ctx, "yamnet.tflite")
                isTfliteLoaded = true
                Log.d("PulseCastAudio", "YAMNet TFLite Audio Classifier loaded successfully")
            } catch (e: Exception) {
                Log.e("PulseCastAudio", "Failed to load YAMNet TFLite Audio Classifier: ${e.message}")
                isTfliteLoaded = false
            }
        }
    }

    private val adKeywordsRegex = Regex(
        """\b(werbung|commercial|ads?|preroll|pre-roll|spot|nachrichten|news|verkehr|traffic|stau|blitzer|wetter|weather|jingle|gewinnspiel|report)\b""",
        RegexOption.IGNORE_CASE
    )

    fun setStationContext(trackTitle: String, isAdFree: Boolean) {
        val titleChanged = this.currentTrackTitle != trackTitle
        this.currentTrackTitle = trackTitle
        this.isAdFreeStation = isAdFree
        if (titleChanged) {
            val title = trackTitle.trim()
            val hasAdKeyword = adKeywordsRegex.containsMatchIn(title)
            val isSongPattern = (title.contains(" - ") || title.contains(" feat. ") || title.contains(" vs. ")) && !hasAdKeyword

            if (!hasAdKeyword && isSongPattern) {
                // Instantly reset speech score when a clear song title is broadcast
                smoothedSpeechScore = 0.1f
            }
        }
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

            if (pcmSampleIndex < pcmSampleBuffer.size) {
                pcmSampleBuffer[pcmSampleIndex++] = sample
            }

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

            // On-Device Neural YAMNet Classification
            if (isTfliteLoaded && tfliteClassifier != null) {
                try {
                    val classifier = tfliteClassifier!!
                    val tensorAudio = classifier.createInputTensorAudio()
                    tensorAudio.load(pcmSampleBuffer, 0, pcmSampleIndex)
                    val results = classifier.classify(tensorAudio)

                    var aiSpeech = 0f
                    var aiMusic = 0f

                    for (classification in results) {
                        for (category in classification.categories) {
                            val label = category.label.lowercase()
                            val score = category.score
                            if (score > 0.10f) {
                                when {
                                    label.contains("speech") || label.contains("talk") ||
                                    label.contains("conversation") || label.contains("narration") ||
                                    label.contains("monologue") || label.contains("shout") -> {
                                        if (score > aiSpeech) aiSpeech = score
                                    }
                                    label.contains("music") || label.contains("singing") ||
                                    label.contains("song") || label.contains("instrument") -> {
                                        if (score > aiMusic) aiMusic = score
                                    }
                                }
                            }
                        }
                    }

                    if (aiSpeech > 0f || aiMusic > 0f) {
                        latestAiSpeechScore = aiSpeech
                        latestAiMusicScore = aiMusic
                    }
                } catch (e: Exception) {
                    Log.w("PulseCastAudio", "TFLite classification exception: ${e.message}")
                }
            }

            pcmSampleIndex = 0
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
        val title = currentTrackTitle.trim()
        val hasAdKeyword = adKeywordsRegex.containsMatchIn(title)
        hasAdMetadata = hasAdKeyword

        val isSongPattern = (title.contains(" - ") || title.contains(" feat. ") || title.contains(" vs. ")) &&
                            !hasAdKeyword

        if (hasAdKeyword) {
            smoothedSpeechScore = 0.95f
            speechConfidence = 0.95f
            musicConfidence = 0.05f
            isSpeechDetected = true
            isPureSpeech = true
            detectionReason = "Werbung / News (Metadaten)"
            Log.d("PulseCastAudio", "Metadata Ad Keyword detected in title: '$currentTrackTitle'")
            return
        }

        // --- 2. Song Pattern Protection (Only discounts when AI speech score is very low) ---
        if (isSongPattern && latestAiSpeechScore < 0.25f) {
            smoothedSpeechScore = (0.70f * smoothedSpeechScore) + (0.30f * 0.05f)
            speechConfidence = smoothedSpeechScore
            musicConfidence = 1.0f - speechConfidence
            isSpeechDetected = false
            isPureSpeech = false
            detectionReason = "Musik ($currentTrackTitle)"
            return
        }

        // --- 3. Acoustic & AI Classification ---
        val avgRms = subFrameRms.average().toFloat()
        if (avgRms < 0.005f) {
            // Near silence / stream pause -> don't trigger speech false-positive
            smoothedSpeechScore = (0.8f * smoothedSpeechScore) + (0.2f * 0.1f)
            speechConfidence = smoothedSpeechScore
            musicConfidence = 1.0f - speechConfidence
            isSpeechDetected = false
            isPureSpeech = false
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
        var rmsVarianceSum = 0f
        for (rms in subFrameRms) {
            val diff = rms - avgRms
            rmsVarianceSum += diff * diff
        }
        val rmsStdDev = sqrt(rmsVarianceSum / windowSize)
        val rmsCoeffVar = if (avgRms > 0.001f) (rmsStdDev / avgRms) else 0f

        val hasSpeechEnvelope = (rmsCoeffVar >= 0.25f) || (lowEnergyRate >= 0.25f) || (rmsCoeffVar >= 0.20f && lowEnergyRate >= 0.18f)
        val hasSpeechConsonants = (zcrVariance >= 0.0030f) || (avgZcr >= 0.14f && zcrVariance >= 0.0018f)

        var acousticSpeechScore = 0.0f
        if (hasSpeechEnvelope && hasSpeechConsonants) {
            acousticSpeechScore = 0.85f
        } else if (hasSpeechEnvelope) {
            acousticSpeechScore = 0.40f
        } else if (hasSpeechConsonants) {
            acousticSpeechScore = 0.30f
        }

        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
        val learnedPrior = stationMinutePrior
        var timePrior = if (learnedPrior != null) {
            (learnedPrior * 0.40f).coerceIn(0.0f, 0.40f)
        } else if (currentMinute in 57..59 || currentMinute in 0..4) {
            0.25f
        } else if (currentMinute in 29..32) {
            0.15f
        } else {
            0.0f
        }

        var rawSpeech = if (isTfliteLoaded && (latestAiSpeechScore > 0f || latestAiMusicScore > 0f)) {
            val netSpeechAi = latestAiSpeechScore - latestAiMusicScore
            val neuralSpeech = when {
                latestAiSpeechScore >= 0.35f && netSpeechAi >= 0.10f -> 0.95f
                latestAiSpeechScore >= 0.25f && netSpeechAi >= 0.0f -> 0.70f
                latestAiSpeechScore >= 0.18f && netSpeechAi >= -0.10f -> 0.45f
                else -> 0.05f
            }
            (neuralSpeech * 0.75f) + (acousticSpeechScore * 0.15f) + (timePrior * 0.10f)
        } else {
            (acousticSpeechScore * 0.70f) + (timePrior * 0.30f)
        }

        rawSpeech = rawSpeech.coerceIn(0.0f, 1.0f)

        smoothedSpeechScore = (0.65f * smoothedSpeechScore) + (0.35f * rawSpeech)

        speechConfidence = smoothedSpeechScore
        musicConfidence = 1.0f - smoothedSpeechScore

        val onThreshold = sensitivityThreshold.coerceIn(0.20f, 0.50f)
        val offThreshold = (onThreshold - 0.12f).coerceAtLeast(0.12f)

        isSpeechDetected = if (isSpeechDetected) {
            smoothedSpeechScore >= offThreshold
        } else {
            smoothedSpeechScore >= onThreshold
        }

        isPureSpeech = (latestAiSpeechScore >= 0.35f && latestAiMusicScore < 0.25f) || (smoothedSpeechScore >= 0.50f)

        detectionReason = when {
            hasAdKeyword -> "Werbung / News (Metadaten)"
            isPureSpeech -> "Nachrichten / Talk (Reine Sprache)"
            isTfliteLoaded && latestAiSpeechScore >= 0.18f -> "Sprache / Talk (YAMNet AI)"
            isSpeechDetected -> "Sprache / Talk"
            isSongPattern -> "Musik ($currentTrackTitle)"
            else -> "Musik"
        }

        Log.d(
            "PulseCastAudio",
            "Title: '$currentTrackTitle' | AiSpeech: ${String.format("%.2f", latestAiSpeechScore)} | AiMusic: ${String.format("%.2f", latestAiMusicScore)} | Acoustic: ${String.format("%.2f", acousticSpeechScore)} | Smoothed: ${String.format("%.2f", smoothedSpeechScore)} | isSpeech: $isSpeechDetected | isPure: $isPureSpeech | Reason: $detectionReason"
        )
    }

    fun reset() {
        subFrameRms.fill(0f)
        subFrameZcr.fill(0f)
        frameIndex = 0
        sampleAccumulator = 0f
        sampleCount = 0
        lastSign = 0
        zeroCrossings = 0
        latestAiSpeechScore = 0f
        latestAiMusicScore = 0f
        smoothedSpeechScore = 0.1f
        speechConfidence = 0.1f
        musicConfidence = 0.9f
        isSpeechDetected = false
        isPureSpeech = false
        hasAdMetadata = false
        detectionReason = "Musik"
    }
}
