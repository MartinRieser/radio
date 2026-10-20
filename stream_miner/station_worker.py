"""
Station Worker Thread.
Handles connection, decoding, resampling, real-time VAD + 3-Band Spectral processing,
GPU Faster-Whisper semantic verification, jingle fingerprinting, and metadata parsing.
"""

from collections import deque
from datetime import datetime
import threading
import time
import av
import numpy as np
from .audio_detector import AudioDetector
from .fingerprinter import JingleFingerprinter
from .pattern_learner import StationPattern


class StationWorker(threading.Thread):
    def __init__(self, station_config: dict, pattern: StationPattern, sensitivity: float = 0.50, semantic_classifier=None):
        super().__init__(daemon=True)
        self.station_config = station_config
        self.pattern = pattern
        self.station_uuid = station_config.get("stationuuid") or station_config.get("url")
        self.name = station_config.get("name", "Unknown Station")
        self.stream_url = station_config.get("url") or station_config.get("url_resolved")

        self.sensitivity = sensitivity
        self.semantic_classifier = semantic_classifier
        self.detector = AudioDetector(sensitivity=sensitivity)
        self.fingerprinter = JingleFingerprinter(history_seconds=10.0)

        # Rolling 4.0-second audio buffer (40 x 100ms chunks) for GPU Whisper classification
        self.recent_pcm_chunks = deque(maxlen=40)
        self.last_semantic_submit_time = 0.0
        self.latest_transcript = ""

        self.stop_requested = threading.Event()
        self.is_connected = False
        self.current_title = ""
        self.latest_status = "Initialisiere..."
        self.latest_score = 0.0
        self.speech_duration_sec = 0.0
        self.music_duration_sec = 0.0
        self.is_speech_active = False
        self.error_message = None
        self.reconnect_count = 0
        self.processed_frames = 0

    def request_stop(self):
        self.stop_requested.set()

    def run(self):
        retry_delay = 2.0
        while not self.stop_requested.is_set():
            try:
                self.latest_status = "Verbinde..."
                self.is_connected = False
                self._stream_loop()
                retry_delay = 2.0
            except Exception as e:
                self.error_message = str(e)
                self.latest_status = f"Fehler ({e.__class__.__name__})"
                self.reconnect_count += 1
                time.sleep(retry_delay)
                retry_delay = min(15.0, retry_delay * 1.5)

    def _stream_loop(self):
        options = {
            "timeout": "8000000",
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PulseCast/1.0",
            "reconnect": "1",
            "reconnect_streamed": "1",
            "reconnect_delay_max": "5"
        }

        container = av.open(self.stream_url, mode="r", options=options, timeout=10)
        self.is_connected = True
        self.latest_status = "Verbunden"

        if container.metadata:
            title = container.metadata.get("StreamTitle") or container.metadata.get("title")
            if title:
                self.current_title = str(title).strip()

        resampler = av.AudioResampler(format="s16", layout="mono", rate=16000)
        sample_buffer = bytearray()

        for frame in container.decode(audio=0):
            if self.stop_requested.is_set():
                break

            if hasattr(frame, "metadata") and frame.metadata:
                t = frame.metadata.get("StreamTitle")
                if t and t != self.current_title:
                    self.current_title = str(t).strip()

            resampled_frames = resampler.resample(frame)
            if not resampled_frames:
                continue

            for rf in resampled_frames:
                sample_buffer.extend(rf.to_ndarray().tobytes())

                while len(sample_buffer) >= 3200:
                    chunk_bytes = sample_buffer[:3200]
                    del sample_buffer[:3200]

                    pcm16 = np.frombuffer(chunk_bytes, dtype=np.int16)
                    self._process_subframe(pcm16)

        try:
            container.close()
        except Exception:
            pass

    def _on_semantic_result(self, res: dict):
        if not res.get("is_true_speech", False):
            # GPU Whisper detected song vocals / singing false-positive!
            rollback_sec = min(self.speech_duration_sec, 4.5)
            self.detector.reject_false_speech()
            self.pattern.rollback_false_speech(datetime.now().minute, rollback_sec)
            self.is_speech_active = False
            self.latest_transcript = ""
        else:
            # Confirmed spoken broadcast segment (News, Weather, Traffic, Ads, Moderation)
            category_label = res.get("category", "🎙️ Moderation")
            short_cat = res.get("short_category", "moderation")
            self.detector.confirm_semantic_category(category_label)
            self.pattern.record_semantic_category(short_cat)
            if res.get("text"):
                self.latest_transcript = f"[{category_label}] \"{res['text']}\""

    def _process_subframe(self, pcm16: np.ndarray):
        self.processed_frames += 1
        now = datetime.now()
        minute_of_hour = now.minute

        # Store normalized float32 chunk in rolling 4.0s buffer for GPU Whisper
        pcm_f32 = pcm16.astype(np.float32) / 32768.0
        self.recent_pcm_chunks.append(pcm_f32)

        # 1. Update rolling fingerprint buffer
        self.fingerprinter.add_subframe(pcm16)

        # 2. Run Audio VAD & 3-Band Spectral Detector
        result = self.detector.process_subframe(pcm16)

        is_speech = result["is_speech"]
        score = result["speech_score"]
        event = result["event"]
        self.latest_score = score
        self.is_speech_active = is_speech
        self.speech_duration_sec = result["speech_duration_sec"]
        self.music_duration_sec = result["music_duration_sec"]

        # 3. Handle Transitions
        if event == "speech_start":
            fp = self.fingerprinter.extract_peak_fingerprint(lookback_frames=40)
            if fp:
                self.pattern.record_jingle(fp)
            self.last_semantic_submit_time = 0.0

        elif event == "speech_end":
            self.pattern.record_speech_segment(result["speech_duration_sec"])
            self.latest_transcript = ""

        # 4. Submit 4-second snippet to GPU Faster-Whisper for semantic verification & categorization
        if (
            is_speech
            and self.semantic_classifier is not None
            and len(self.recent_pcm_chunks) >= 30
            and self.speech_duration_sec >= 2.0
        ):
            now_ts = time.time()
            if (now_ts - self.last_semantic_submit_time) >= 4.5:
                self.last_semantic_submit_time = now_ts
                audio_4s = np.concatenate(list(self.recent_pcm_chunks))
                self.semantic_classifier.submit_audio(
                    self.station_uuid,
                    audio_4s,
                    self._on_semantic_result
                )

        # 5. Record subframe observation (100ms = 0.1s)
        self.pattern.record_subframe(minute_of_hour, is_speech, duration_sec=0.1)

        # 6. Update Status Label
        if is_speech:
            sec = int(self.speech_duration_sec)
            m, s = divmod(sec, 60)
            cat_label = self.detector.semantic_category
            self.latest_status = f"{cat_label} ({m:02d}:{s:02d})"
        else:
            sec = int(self.music_duration_sec)
            m, s = divmod(sec, 60)
            self.latest_status = f"🎵 Musik ({m:02d}:{s:02d})"

    def get_summary(self) -> dict:
        display_meta = self.latest_transcript if (self.is_speech_active and self.latest_transcript) else self.current_title
        return {
            "name": self.name,
            "status": self.latest_status,
            "is_speech": self.is_speech_active,
            "speech_score": self.latest_score,
            "speech_dur": self.speech_duration_sec,
            "music_dur": self.music_duration_sec,
            "is_connected": self.is_connected,
            "title": display_meta,
            "learned_window": self.pattern.learned_news_window,
            "learned_jingle": self.pattern.learned_jingle,
            "is_pure_music": self.pattern.is_pure_music,
            "speech_ratio": (self.pattern.total_speech_sec / self.pattern.total_observed_sec) if self.pattern.total_observed_sec > 0 else 0.0
        }
