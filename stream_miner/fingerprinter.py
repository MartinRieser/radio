"""
Jingle & Stinger Fingerprint Extractor.
Extracts 16-band spectral energy modulation signatures from audio preceding speech onsets.
Fully compatible with PulseCast Android's StationSignalCollector quantization.
"""

from collections import deque
import numpy as np

class JingleFingerprinter:
    def __init__(self, history_seconds: float = 10.0):
        # Rolling buffer of 16-band energy frames (~100ms per frame)
        self.max_frames = int(history_seconds * 10) # 10 frames per second
        self.frame_history = deque(maxlen=self.max_frames)

    def add_subframe(self, pcm16_mono: np.ndarray):
        """
        Computes 16-band energy for a ~100ms PCM frame and adds to rolling history.
        """
        if len(pcm16_mono) == 0:
            return

        if pcm16_mono.dtype == np.int16:
            samples = np.abs(pcm16_mono.astype(np.float32) / 32768.0)
        else:
            samples = np.abs(pcm16_mono.astype(np.float32))

        # Split 1600 samples into 16 bands
        band_size = max(1, len(samples) // 16)
        band_energies = np.zeros(16, dtype=np.float32)

        for b in range(16):
            start = b * band_size
            end = min(len(samples), (b + 1) * band_size)
            if start < len(samples):
                band_energies[b] = np.mean(samples[start:end])

        self.frame_history.append(band_energies)

    def extract_peak_fingerprint(self, lookback_frames: int = 50) -> str:
        """
        Finds the 2-second burst (~20 frames) with the highest energy variance in the
        recent history and quantizes it into a compact 10-hex string.
        Matches Android StationSignalCollector.extractPeakFingerprint().
        """
        history = list(self.frame_history)
        if len(history) < 20:
            return ""

        # Use recent lookback window
        window = history[-lookback_frames:] if len(history) > lookback_frames else history
        if len(window) < 20:
            return ""

        max_variance = 0.0
        best_index = 0

        for i in range(len(window) - 20):
            block = window[i:i + 20]
            energies = [np.sum(frame) for frame in block]
            mean = float(np.mean(energies))
            var = float(np.var(energies))
            if var > max_variance:
                max_variance = var
                best_index = i

        # Threshold to avoid fingerprinting silence or flat noise
        if max_variance < 0.002:
            return ""

        # Quantize 10 points (step 2) across 20 subframes into hex string
        hex_chars = []
        for j in range(0, 20, 2):
            bands = window[best_index + j]
            max_band = int(np.argmax(bands))
            hex_chars.append(f"{max_band:x}")

        return "".join(hex_chars)
