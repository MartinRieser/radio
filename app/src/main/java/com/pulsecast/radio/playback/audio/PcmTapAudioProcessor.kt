package com.pulsecast.radio.playback.audio

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Transparent ExoPlayer AudioProcessor tap that captures real-time decoded PCM audio
 * buffers and feeds them to the AudioContentClassifier without interfering with playback.
 */
@OptIn(UnstableApi::class)
class PcmTapAudioProcessor(
    private val onPcmBufferReceived: (buffer: ByteBuffer, sampleRate: Int, channelCount: Int) -> Unit
) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Audio format is passed through unchanged
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Provide non-destructive read-only view to classifier
        val readOnlyCopy = inputBuffer.asReadOnlyBuffer()
        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount

        if (sampleRate > 0 && channels > 0) {
            onPcmBufferReceived(readOnlyCopy, sampleRate, channels)
        }

        // Pass PCM directly through to the ExoPlayer audio sink
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }
}
