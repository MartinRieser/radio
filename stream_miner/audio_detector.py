"""
Broadcast Audio Content & Voice Activity Detector (VAD) for Stream Mining.
Combines:
1. Silero Neural VAD v5 (ONNX with 64-sample recurrent context)
2. 3-Band FFT Spectral Broadcast Analysis (Bass 40-180Hz, Vocal 250-3800Hz, High 4500-8000Hz)
3. Syllabic Envelope Modulation & Zero-Crossing Dynamics (separates singing from spoken moderation/ads)
4. GPU Faster-Whisper feedback loop (eliminates vocal song false-positives & tags News/Weather/Traffic/Ads)
"""

import os
import numpy as np
import onnxruntime as ort


class AudioDetector:
    def __init__(self, model_path: str = None, sensitivity: float = 0.50):
        if model_path is None:
            model_path = os.path.join(os.path.dirname(__file__), "silero_vad.onnx")

        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Silero VAD ONNX model not found at {model_path}")

        opts = ort.SessionOptions()
        opts.inter_op_num_threads = 1
        opts.intra_op_num_threads = 1
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

        self.session = ort.InferenceSession(model_path, opts, providers=["CPUExecutionProvider"])
        self.sensitivity = sensitivity
        self.reset()

    def reset(self):
        """Resets recurrent VAD state and sliding acoustic windows."""
        self.state = np.zeros((2, 1, 128), dtype=np.float32)
        self.context = np.zeros((1, 64), dtype=np.float32)
        self.sr = np.array(16000, dtype=np.int64)

        # Sliding window of 15 acoustic subframes (~1.5s evaluation window)
        self.window_size = 15
        self.subframe_rms = [0.0] * self.window_size
        self.subframe_zcr = [0.0] * self.window_size
        self.subframe_ai = [0.0] * self.window_size
        self.subframe_vocal_ratio = [0.0] * self.window_size
        self.subframe_bass_ratio = [0.0] * self.window_size
        self.frame_index = 0

        # State tracking
        self.smoothed_speech_score = 0.1
        self.is_speech_active = False
        self.speech_duration_sec = 0.0
        self.music_duration_sec = 0.0
        self.consecutive_speech_frames = 0
        self.consecutive_music_frames = 0
        self.singing_cooldown_frames = 0
        self.detection_reason = "Musik"
        self.semantic_category = "🎙️ Sprache"

    def reject_false_speech(self):
        """
        Called by GPU Faster-Whisper when a suspected speech block turns out to be
        singing/vocals in a music track. Immediately resets to Music and sets a brief cooldown.
        """
        self.is_speech_active = False
        self.speech_duration_sec = 0.0
        self.consecutive_speech_frames = 0
        self.smoothed_speech_score = 0.10
        self.singing_cooldown_frames = 35  # 3.5s cooldown against chorus vocals
        self.detection_reason = "Musik (Gesang gefiltert)"

    def confirm_semantic_category(self, category_label: str):
        """Updates the active broadcast category identified by GPU Whisper."""
        self.semantic_category = category_label
        if self.is_speech_active:
            self.detection_reason = category_label

    def process_subframe(self, pcm16_mono: np.ndarray) -> dict:
        """
        Processes a ~100ms subframe (1600 samples of 16kHz mono int16 or float32).
        """
        if len(pcm16_mono) == 0:
            return {"is_speech": False, "score": 0.0, "event": None}

        if pcm16_mono.dtype == np.int16:
            samples = pcm16_mono.astype(np.float32) / 32768.0
        else:
            samples = pcm16_mono.astype(np.float32)

        if self.singing_cooldown_frames > 0:
            self.singing_cooldown_frames -= 1

        # 1. Silero VAD v5 inference in 512-sample chunks with 64-sample context
        vad_scores = []
        chunk_size = 512
        for i in range(0, len(samples) - chunk_size + 1, chunk_size):
            chunk = samples[i:i + chunk_size].reshape(1, -1)
            inp = np.concatenate([self.context, chunk], axis=1)
            out, self.state = self.session.run(None, {
                "input": inp,
                "state": self.state,
                "sr": self.sr
            })
            self.context = inp[:, -64:]
            vad_scores.append(float(out[0][0]))

        ai_score = float(np.mean(vad_scores)) if vad_scores else 0.0

        # 2. 3-Band Spectral FFT & Acoustical DSP Analysis
        rms = float(np.sqrt(np.mean(samples ** 2)))
        zero_crossings = int(np.sum(samples[:-1] * samples[1:] < 0))
        zcr = float(zero_crossings / len(samples)) if len(samples) > 0 else 0.0

        # Fast Hann-windowed RFFT on the 1600 samples (10Hz bin resolution at 16kHz)
        windowed = samples * np.hanning(len(samples))
        mag = np.abs(np.fft.rfft(windowed))
        freqs = np.fft.rfftfreq(len(samples), d=1.0 / 16000.0)
        total_spec_energy = float(np.sum(mag)) + 1e-7

        # Band energies: Bass (40-190Hz), Vocal Formants (250-3800Hz)
        bass_mask = (freqs >= 40) & (freqs <= 190)
        vocal_mask = (freqs >= 250) & (freqs <= 3800)
        bass_ratio = float(np.sum(mag[bass_mask])) / total_spec_energy
        vocal_ratio = float(np.sum(mag[vocal_mask])) / total_spec_energy

        idx = self.frame_index % self.window_size
        self.subframe_rms[idx] = rms
        self.subframe_zcr[idx] = zcr
        self.subframe_ai[idx] = ai_score
        self.subframe_vocal_ratio[idx] = vocal_ratio
        self.subframe_bass_ratio[idx] = bass_ratio
        self.frame_index += 1

        avg_rms = float(np.mean(self.subframe_rms))
        avg_zcr = float(np.mean(self.subframe_zcr))
        avg_ai = float(np.mean(self.subframe_ai))
        avg_vocal = float(np.mean(self.subframe_vocal_ratio))
        avg_bass = float(np.mean(self.subframe_bass_ratio))

        if avg_rms < 0.004:
            raw_speech = 0.05
            reason = "Stille"
        else:
            # Syllabic envelope dynamics (speech pauses between words/syllables)
            low_energy_frames = sum(1 for r in self.subframe_rms if r < avg_rms * 0.55)
            low_energy_rate = low_energy_frames / self.window_size

            zcr_var = float(np.var(self.subframe_zcr))
            rms_std = float(np.std(self.subframe_rms))
            rms_cv = (rms_std / avg_rms) if avg_rms > 0.001 else 0.0

            # Broadcast speech characteristics (works for both dry speech and speech over music bed)
            has_syllabic_mod = (rms_cv >= 0.22) or (low_energy_rate >= 0.20)
            has_consonant_flux = (zcr_var >= 0.0022) or (avg_zcr >= 0.14 and zcr_var >= 0.0012)
            has_vocal_dominance = avg_vocal >= 0.52 and (avg_vocal > avg_bass * 1.35)

            dsp_score = 0.0
            if has_syllabic_mod and has_consonant_flux and has_vocal_dominance:
                dsp_score = 0.90
            elif has_vocal_dominance and (has_syllabic_mod or has_consonant_flux):
                dsp_score = 0.65
            elif has_syllabic_mod or has_consonant_flux:
                dsp_score = 0.35

            # Penalize heavy steady sub-bass typical of pop/dance/rock music when syllabic modulation is flat
            music_bass_penalty = 0.0
            if avg_bass > 0.36 and rms_cv < 0.18 and low_energy_rate < 0.15:
                music_bass_penalty = 0.25

            if self.singing_cooldown_frames > 0:
                raw_speech = 0.10
            elif avg_ai >= 0.55:
                raw_speech = max(0.0, (0.75 * avg_ai + 0.25 * dsp_score) - music_bass_penalty)
            elif avg_ai >= 0.25:
                raw_speech = max(0.0, (0.65 * avg_ai + 0.35 * dsp_score) - music_bass_penalty)
            else:
                raw_speech = (0.60 * avg_ai) + (0.25 * dsp_score)

            reason = self.semantic_category if raw_speech >= 0.50 else "Musik"

        # Temporal Exponential Moving Average (EMA)
        alpha = 0.32
        self.smoothed_speech_score = (1.0 - alpha) * self.smoothed_speech_score + alpha * raw_speech

        # Hysteresis state machine
        on_thresh = float(np.clip(self.sensitivity, 0.30, 0.70))
        off_thresh = max(0.18, on_thresh - 0.16)

        event = None
        subframe_duration = len(samples) / 16000.0

        if self.smoothed_speech_score >= on_thresh and self.singing_cooldown_frames == 0:
            self.consecutive_speech_frames += 1
            self.consecutive_music_frames = 0
            # Require 4 consecutive frames (~400ms) to confirm speech start
            if not self.is_speech_active and self.consecutive_speech_frames >= 4:
                self.is_speech_active = True
                self.speech_duration_sec = self.consecutive_speech_frames * subframe_duration
                self.semantic_category = "🗣️ Sprache"
                event = "speech_start"
            elif self.is_speech_active:
                self.speech_duration_sec += subframe_duration
        else:
            self.consecutive_music_frames += 1
            self.consecutive_speech_frames = 0
            # Require 6 consecutive music frames (~600ms) to confirm speech end
            if self.is_speech_active and self.consecutive_music_frames >= 6:
                self.is_speech_active = False
                self.speech_duration_sec = 0.0
                self.music_duration_sec = self.consecutive_music_frames * subframe_duration
                event = "speech_end"
            elif not self.is_speech_active:
                self.music_duration_sec += subframe_duration

        self.detection_reason = self.semantic_category if self.is_speech_active else "Musik"

        return {
            "is_speech": self.is_speech_active,
            "speech_score": round(self.smoothed_speech_score, 3),
            "raw_ai_score": round(ai_score, 3),
            "rms": round(avg_rms, 4),
            "speech_duration_sec": round(self.speech_duration_sec, 1),
            "music_duration_sec": round(self.music_duration_sec, 1),
            "reason": self.detection_reason,
            "event": event
        }
