package com.pulsecast.radio.playback.audio

/**
 * Global bridge allowing RadioPlaybackService to stream decoded PCM audio
 * directly into the AudioContentClassifier used by PlaybackController and MainViewModel.
 */
object AudioBridge {
    @Volatile
    var classifier: AudioContentClassifier? = null
}
