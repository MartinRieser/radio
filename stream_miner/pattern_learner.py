"""
Pattern Learner Engine for Radio Streams.
Learns:
1. 60-Minute speech & ad heatmap (minutes 0..59) with GPU false-positive rollback.
2. Recurring news/ad/weather windows (e.g. :58 - :03).
3. Pre-roll ad detection on stream connect.
4. Category-specific station jingle/stinger fingerprints (News, Ads, Weather, Traffic).
5. Pure music ("100% Musik") classification.
"""

from collections import Counter
import json
import os
import numpy as np


class StationPattern:
    def __init__(self, station_uuid: str, name: str, stream_url: str):
        self.station_uuid = station_uuid
        self.name = name
        self.stream_url = stream_url

        # 60-minute counters (index 0..59)
        self.minute_speech_sec = [0.0] * 60
        self.minute_total_sec = [0.0] * 60

        # Jingle fingerprint frequency counters (overall + per semantic category)
        self.jingle_counter = Counter()
        self.category_jingles = {
            "news": Counter(),
            "ads": Counter(),
            "weather": Counter(),
            "traffic": Counter()
        }
        self.category_counts = Counter()

        # State tracking
        self.total_observed_sec = 0.0
        self.total_speech_sec = 0.0
        self.pre_roll_detected = False
        self.initial_window_done = False
        self.last_jingle_candidate = None

        # Segment statistics
        self.speech_segments = []  # list of durations in seconds
        self.music_segments = []

        # Learned results
        self.learned_news_window = None
        self.learned_jingle = None
        self.is_pure_music = False

    def record_subframe(self, minute_of_hour: int, is_speech: bool, duration_sec: float):
        """Records a single subframe observation at the given minute."""
        m = minute_of_hour % 60
        self.minute_total_sec[m] += duration_sec
        self.total_observed_sec += duration_sec

        if is_speech:
            self.minute_speech_sec[m] += duration_sec
            self.total_speech_sec += duration_sec

        # Check for pre-roll in first 20 seconds of stream
        if not self.initial_window_done:
            if self.total_observed_sec <= 20.0 and is_speech:
                self.pre_roll_detected = True
            elif self.total_observed_sec > 20.0:
                self.initial_window_done = True

    def rollback_false_speech(self, minute_of_hour: int, duration_sec: float):
        """
        Rolls back false-positive speech seconds when GPU Whisper identifies song vocals/singing.
        Ensures the hourly heatmap stays 100% clean from singing false alarms.
        """
        m = minute_of_hour % 60
        rollback = min(duration_sec, self.minute_speech_sec[m])
        self.minute_speech_sec[m] = max(0.0, self.minute_speech_sec[m] - rollback)
        self.total_speech_sec = max(0.0, self.total_speech_sec - duration_sec)
        if self.total_observed_sec <= 25.0 and self.total_speech_sec < 1.0:
            self.pre_roll_detected = False

    def record_semantic_category(self, short_category: str):
        """Records semantic category identified by GPU Whisper (news, weather, traffic, ads, moderation)."""
        if short_category and short_category != "musik":
            self.category_counts[short_category] += 1
            if self.last_jingle_candidate and short_category in self.category_jingles:
                self.category_jingles[short_category][self.last_jingle_candidate] += 1

    def record_speech_segment(self, duration_sec: float):
        if duration_sec >= 1.0:
            self.speech_segments.append(round(duration_sec, 1))

    def record_music_segment(self, duration_sec: float):
        if duration_sec >= 1.0:
            self.music_segments.append(round(duration_sec, 1))

    def record_jingle(self, fingerprint: str):
        if fingerprint and len(fingerprint) == 10:
            self.last_jingle_candidate = fingerprint
            self.jingle_counter[fingerprint] += 1
            most_common = self.jingle_counter.most_common(1)
            if most_common:
                self.learned_jingle = most_common[0][0]

    def compute_heatmap(self) -> list:
        """Returns 60-element array of speech probability for minutes 0..59."""
        heatmap = []
        for m in range(60):
            tot = self.minute_total_sec[m]
            if tot >= 5.0:
                prob = min(1.0, max(0.0, self.minute_speech_sec[m] / tot))
            else:
                if self.is_pure_music:
                    prob = 0.05
                elif m in (58, 59, 0, 1, 2, 3):
                    prob = 0.70
                elif m in (29, 30, 31):
                    prob = 0.40
                else:
                    prob = 0.15
            heatmap.append(round(float(prob), 3))
        return heatmap

    def evaluate_patterns(self):
        """Re-evaluates news window, pure music status, and jingles."""
        heatmap = self.compute_heatmap()

        # 1. Pure music check: only after at least 60 minutes (3600 seconds) observed
        if self.total_observed_sec >= 3600.0:
            speech_ratio = (self.total_speech_sec / self.total_observed_sec) if self.total_observed_sec > 0 else 0
            if speech_ratio < 0.015:
                self.is_pure_music = True
            elif speech_ratio > 0.05:
                self.is_pure_music = False

        # 2. News window detection around :00
        top_indices = [55, 56, 57, 58, 59, 0, 1, 2, 3, 4, 5]
        active_news_minutes = [m for m in top_indices if heatmap[m] >= 0.45]

        if len(active_news_minutes) >= 2:
            start_m = active_news_minutes[0]
            end_m = active_news_minutes[-1]
            self.learned_news_window = f":{start_m:02d} - :{end_m:02d}"
        elif self.is_pure_music:
            self.learned_news_window = None

        # 3. Dominant jingle
        if self.jingle_counter:
            self.learned_jingle = self.jingle_counter.most_common(1)[0][0]

    def to_dict(self) -> dict:
        self.evaluate_patterns()
        avg_speech = float(np.mean(self.speech_segments)) if self.speech_segments else 0.0
        avg_music = float(np.mean(self.music_segments)) if self.music_segments else 0.0

        cat_jingles_out = {}
        for cat, cnt in self.category_jingles.items():
            if cnt:
                cat_jingles_out[cat] = cnt.most_common(1)[0][0]

        return {
            "name": self.name,
            "stationuuid": self.station_uuid,
            "stream_url": self.stream_url,
            "hourly_heatmap": self.compute_heatmap(),
            "learned_news_window": self.learned_news_window,
            "learned_jingle": self.learned_jingle,
            "category_jingles": cat_jingles_out,
            "category_counts": dict(self.category_counts),
            "is_pure_music": self.is_pure_music,
            "has_preroll": self.pre_roll_detected,
            "total_observed_sec": round(self.total_observed_sec, 1),
            "total_speech_sec": round(self.total_speech_sec, 1),
            "speech_ratio": round((self.total_speech_sec / self.total_observed_sec) if self.total_observed_sec > 0 else 0.0, 3),
            "avg_speech_duration_sec": round(avg_speech, 1),
            "avg_music_duration_sec": round(avg_music, 1),
            "speech_segments_count": len(self.speech_segments),
            "jingles_recorded_count": sum(self.jingle_counter.values())
        }

    def load_dict(self, data: dict):
        self.total_observed_sec = data.get("total_observed_sec", 0.0)
        self.total_speech_sec = data.get("total_speech_sec", 0.0)
        self.pre_roll_detected = data.get("has_preroll", False)
        self.is_pure_music = data.get("is_pure_music", False)
        self.learned_news_window = data.get("learned_news_window")
        self.learned_jingle = data.get("learned_jingle")
        if "category_counts" in data and isinstance(data["category_counts"], dict):
            self.category_counts.update(data["category_counts"])
        hm = data.get("hourly_heatmap", [])
        if len(hm) == 60:
            for m in range(60):
                self.minute_total_sec[m] = 100.0
                self.minute_speech_sec[m] = hm[m] * 100.0


class PatternLearner:
    def __init__(self, checkpoint_path: str = "training_checkpoint.json"):
        self.stations = {}
        self.active_keys = set()
        self.checkpoint_path = checkpoint_path
        self.load_checkpoint()

    def get_or_create_station(self, station_uuid: str, name: str, stream_url: str) -> StationPattern:
        key = station_uuid or stream_url
        self.active_keys.add(key)
        if key not in self.stations:
            self.stations[key] = StationPattern(station_uuid, name, stream_url)
        return self.stations[key]

    def save_checkpoint(self):
        try:
            # 1. Read latest checkpoint on disk so concurrent instances never overwrite each other's stations
            merged = {}
            if os.path.exists(self.checkpoint_path):
                try:
                    with open(self.checkpoint_path, "r", encoding="utf-8") as f:
                        merged = json.load(f) or {}
                except Exception:
                    merged = {}

            # 2. Merge ONLY the stations actively streamed by this instance
            keys_to_save = self.active_keys if self.active_keys else set(self.stations.keys())
            own_data = {k: self.stations[k].to_dict() for k in keys_to_save if k in self.stations}
            merged.update(own_data)

            # 3. Also save an instance-specific backup file so zero data is ever lost
            base_dir = os.path.dirname(self.checkpoint_path)
            backup_path = os.path.join(base_dir, f"training_checkpoint_active_{os.getpid()}.json")
            with open(backup_path, "w", encoding="utf-8") as f:
                json.dump(own_data, f, indent=2, ensure_ascii=False)

            # 4. Atomic write to main checkpoint
            tmp_path = f"{self.checkpoint_path}.tmp.{os.getpid()}"
            with open(tmp_path, "w", encoding="utf-8") as f:
                json.dump(merged, f, indent=2, ensure_ascii=False)
            os.replace(tmp_path, self.checkpoint_path)
        except Exception as e:
            print(f"Error saving checkpoint: {e}")

    def load_checkpoint(self):
        if os.path.exists(self.checkpoint_path):
            try:
                with open(self.checkpoint_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    for k, sdata in data.items():
                        st = StationPattern(sdata.get("stationuuid", k), sdata.get("name", ""), sdata.get("stream_url", ""))
                        st.load_dict(sdata)
                        self.stations[k] = st
            except Exception as e:
                print(f"Error loading checkpoint: {e}")
