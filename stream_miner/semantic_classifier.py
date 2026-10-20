"""
GPU-Accelerated Semantic Broadcast Classifier using Faster-Whisper (CUDA float16).
Runs on NVIDIA RTX 4070 Ti to:
1. Eliminate Singing/Rap false-positives (distinguishes song vocals from real spoken broadcast).
2. Classify spoken radio segments into exact broadcast categories:
   - 📰 Nachrichten (News)
   - 🌦️ Wetter (Weather)
   - 🚗 Verkehr (Traffic / Speed Cameras)
   - 📢 Werbung (Commercials / Ads)
   - 🎙️ Moderation (Studio Host / Talk)
"""

import os
import queue
import re
import threading
import time
import numpy as np

# Register NVIDIA CUDA 12 DLL directories on Windows before importing ctranslate2/faster_whisper
try:
    import nvidia.cublas
    import nvidia.cudnn
    for mod in (nvidia.cublas, nvidia.cudnn):
        bin_dir = os.path.join(list(mod.__path__)[0], "bin")
        if os.path.exists(bin_dir):
            if hasattr(os, "add_dll_directory"):
                os.add_dll_directory(bin_dir)
            os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")
except Exception:
    pass

os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"


class SemanticBroadcastClassifier:
    """
    Singleton-style asynchronous GPU worker that transcribes 4-second audio buffers
    from all 30 stations and invokes a callback with the semantic classification.
    """

    NEWS_KEYWORDS = {
        "nachrichten", "meldungen", "politik", "regierung", "polizei", "kanzler",
        "präsident", "minister", "parlament", "gericht", "prozess", "krieg",
        "ukraine", "nahost", "europa", "bundestag", "nationalrat", "wahl",
        "wirtschaft", "inflation", "streik", "unfall", "feuerwehr", "rettung",
        "news", "headlines", "breaking", "report", "government", "police", "minister"
    }

    WEATHER_KEYWORDS = {
        "wetter", "grad", "temperaturen", "sonne", "regen", "wolken", "höchstwerte",
        "tiefstwerte", "vorhersage", "schauer", "gewitter", "nebel", "wind", "föhn",
        "frost", "schnee", "wetterbericht", "aussichten", "heiter", "bewölkt",
        "niederschlag", "grad plus", "grad minus", "weather", "forecast", "degrees", "rain"
    }

    TRAFFIC_KEYWORDS = {
        "verkehr", "stau", "autobahn", "richtung", "fahrbahn", "anschlussstelle",
        "gefahr", "blitzer", "radar", "stockender", "verkehrsservice", "gute fahrt",
        "sperre", "gesperrt", "baustelle", "unfallstelle", "kilometer", "zeitverlust",
        "geisterfahrer", "gegenstände", "rheintal", "pfändertunnel", "arlberg",
        "a1", "a2", "a3", "a5", "a6", "a7", "a8", "a9", "a12", "a14", "a81", "a96",
        "traffic", "motorway", "junction", "delays"
    }

    AD_KEYWORDS = {
        "werbung", "angebot", "prozent", "rabatt", "sparen", "euro", "franken",
        "jetzt bei", "online", "shop", "apotheke", "arzt", "apotheker", "risiken",
        "nebenwirkungen", "packungsbeilage", "möbel", "autohaus", "leasing",
        "versicherung", "kredit", "präsentiert von", "sponsor", "aktion", "gratis",
        "bestellen", "supermarkt", "filiale", "wochenende", "preis", "günstig",
        "www", ".de", ".at", ".ch", "komfort", "qualität", "kunden", "discount"
    }

    SINGING_HALLUCINATIONS = {
        "untertitel", "amara.org", "copyright", "all rights reserved",
        "musik", "music", "applaus", "instrumental", "lyrics", "chorus"
    }

    def __init__(self, model_size: str = "small", device: str = "cuda"):
        self.model_size = model_size
        self.device = device
        self.model = None
        self.is_ready = False
        self.device_label = "Initialisiere GPU..."
        self.task_queue = queue.Queue(maxsize=64)
        self.stop_event = threading.Event()
        self.total_classifications = 0
        self.avg_inference_ms = 0.0

        self.worker_thread = threading.Thread(target=self._worker_loop, daemon=True)
        self.worker_thread.start()

    def submit_audio(self, station_id: str, pcm_float32: np.ndarray, callback):
        """
        Submits a ~3.5 - 4.5s mono 16kHz float32 audio snippet for GPU analysis.
        Non-blocking: drops if queue is full.
        """
        if not self.is_ready or self.stop_event.is_set():
            return False
        try:
            self.task_queue.put_nowait((station_id, pcm_float32.copy(), callback))
            return True
        except queue.Full:
            return False

    def stop(self):
        self.stop_event.set()

    def _load_model(self):
        from faster_whisper import WhisperModel
        try:
            self.model = WhisperModel(self.model_size, device="cuda", compute_type="float16")
            self.device_label = f"RTX 4070 Ti (CUDA FP16 - Whisper-{self.model_size})"
            self.is_ready = True
        except Exception as e:
            try:
                self.model = WhisperModel("base", device="cpu", compute_type="int8")
                self.device_label = "CPU INT8 (Whisper-base Fallback)"
                self.is_ready = True
            except Exception as e2:
                self.device_label = f"Whisper Fehler: {e2}"
                self.is_ready = False

    def _worker_loop(self):
        self._load_model()
        if not self.is_ready:
            return

        while not self.stop_event.is_set():
            try:
                station_id, audio, callback = self.task_queue.get(timeout=0.5)
            except queue.Empty:
                continue

            t0 = time.time()
            try:
                result = self._analyze_audio(audio)
                elapsed_ms = (time.time() - t0) * 1000.0
                self.total_classifications += 1
                self.avg_inference_ms = 0.85 * self.avg_inference_ms + 0.15 * elapsed_ms
                callback(result)
            except Exception:
                pass
            finally:
                self.task_queue.task_done()

    def _analyze_audio(self, audio: np.ndarray) -> dict:
        segments, info = self.model.transcribe(
            audio,
            beam_size=1,
            best_of=1,
            without_timestamps=True,
            condition_on_previous_text=False,
            vad_filter=False
        )

        seg_list = list(segments)
        if not seg_list:
            return {
                "is_true_speech": False,
                "category": "🎵 Musik / Gesang",
                "short_category": "musik",
                "text": ""
            }

        full_text = " ".join(s.text.strip() for s in seg_list).strip()
        avg_no_speech = float(np.mean([s.no_speech_prob for s in seg_list]))
        avg_logprob = float(np.mean([s.avg_logprob for s in seg_list]))

        text_lower = full_text.lower()

        # 1. Check for musical notes or non-speech hallucinations
        if (
            "♪" in full_text
            or "♫" in full_text
            or len(full_text) < 6
            or avg_no_speech > 0.68
            or avg_logprob < -1.35
        ):
            return {
                "is_true_speech": False,
                "category": "🎵 Musik / Gesang",
                "short_category": "musik",
                "text": ""
            }

        for hall in self.SINGING_HALLUCINATIONS:
            if hall in text_lower and len(text_lower) < 35:
                return {
                    "is_true_speech": False,
                    "category": "🎵 Musik / Gesang",
                    "short_category": "musik",
                    "text": ""
                }

        # Check repetitive words typical of song choruses (e.g. "baby baby baby", "yeah yeah")
        words = re.findall(r"\b\w+\b", text_lower)
        if len(words) >= 4:
            unique_ratio = len(set(words)) / len(words)
            if unique_ratio < 0.45:
                return {
                    "is_true_speech": False,
                    "category": "🎵 Musik / Gesang",
                    "short_category": "musik",
                    "text": ""
                }

        # 2. Classify true spoken broadcast content into categories
        scores = {
            "news": 0,
            "weather": 0,
            "traffic": 0,
            "ads": 0
        }

        for kw in self.WEATHER_KEYWORDS:
            if kw in text_lower:
                scores["weather"] += 2

        for kw in self.TRAFFIC_KEYWORDS:
            if kw in text_lower:
                scores["traffic"] += 2

        for kw in self.AD_KEYWORDS:
            if kw in text_lower:
                scores["ads"] += 2

        for kw in self.NEWS_KEYWORDS:
            if kw in text_lower:
                scores["news"] += 1.5

        best_cat = max(scores, key=scores.get)
        best_score = scores[best_cat]

        if best_score >= 2:
            if best_cat == "weather":
                category = "🌦️ Wetter"
                short_cat = "weather"
            elif best_cat == "traffic":
                category = "🚗 Verkehr"
                short_cat = "traffic"
            elif best_cat == "ads":
                category = "📢 Werbung"
                short_cat = "ads"
            else:
                category = "📰 Nachrichten"
                short_cat = "news"
        else:
            category = "🎙️ Moderation"
            short_cat = "moderation"

        return {
            "is_true_speech": True,
            "category": category,
            "short_category": short_cat,
            "text": full_text[:70]
        }
