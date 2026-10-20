"""
PulseCast Radio Stream Miner & Pattern Training Engine.
Main entry point for concurrent stream analysis on PC (with RTX 4070 Ti GPU Whisper + 3-Band Spectral VAD).
"""

import argparse
import json
import os
import time

from .pattern_learner import PatternLearner
from .station_worker import StationWorker
from .exporter import IntelligenceExporter
from .dashboard import MinerDashboard
from .semantic_classifier import SemanticBroadcastClassifier


def parse_args():
    parser = argparse.ArgumentParser(description="PulseCast PC Stream Miner & GPU Pattern Trainer")
    parser.add_argument(
        "--stations",
        type=str,
        default=None,
        help="Path to stations JSON file (default: stream_miner/stations.json or app assets)"
    )
    parser.add_argument(
        "--max-streams",
        type=int,
        default=30,
        help="Maximum concurrent streams to evaluate (default: 30)"
    )
    parser.add_argument(
        "--sensitivity",
        type=float,
        default=0.50,
        help="Speech detection sensitivity between 0.30 and 0.70 (default: 0.50)"
    )
    parser.add_argument(
        "--whisper-model",
        type=str,
        default="small",
        help="Faster-Whisper model size for GPU semantic verification (default: small)"
    )
    parser.add_argument(
        "--duration",
        type=int,
        default=None,
        help="Optional duration to run in seconds before automatically saving and exiting"
    )
    parser.add_argument(
        "--no-dashboard",
        action="store_true",
        help="Disable interactive terminal UI (runs with standard text logs)"
    )
    parser.add_argument(
        "--export-only",
        action="store_true",
        help="Export existing checkpoint directly to app assets without streaming"
    )
    return parser.parse_args()


def load_station_list(stations_arg: str, max_streams: int) -> list:
    candidates = []
    if stations_arg and os.path.exists(stations_arg):
        candidates.append(stations_arg)

    local_stations = os.path.join(os.path.dirname(__file__), "stations.json")
    if os.path.exists(local_stations):
        candidates.append(local_stations)

    app_assets_curated = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "curated_stations.json")
    )
    if os.path.exists(app_assets_curated):
        candidates.append(app_assets_curated)

    if not candidates:
        raise FileNotFoundError("Could not find any station configuration JSON.")

    selected_file = candidates[0]
    print(f"Loading stations from: {selected_file}")

    with open(selected_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    stations = []
    for item in data:
        url = item.get("url") or item.get("url_resolved")
        if url:
            stations.append({
                "stationuuid": item.get("stationuuid") or url,
                "name": item.get("name", "Unknown"),
                "url": url,
                "country": item.get("country", ""),
                "tags": item.get("tags", ""),
                "is_pure_music": item.get("is_pure_music", False)
            })

    return stations[:max_streams]


def main():
    args = parse_args()

    checkpoint_path = os.path.join(os.path.dirname(__file__), "training_checkpoint.json")
    learner = PatternLearner(checkpoint_path=checkpoint_path)
    exporter = IntelligenceExporter(pattern_learner=learner)

    if args.export_only:
        print("Exporting trained station intelligence from checkpoint...")
        res = exporter.export_all()
        print(f"Saved: {res}")
        return

    stations = load_station_list(args.stations, args.max_streams)
    print(f"Initializing GPU Semantic Classifier (Whisper-{args.whisper_model} on CUDA FP16)...")
    semantic_classifier = SemanticBroadcastClassifier(model_size=args.whisper_model, device="cuda")

    print(f"Starting Stream Mining for {len(stations)} stations (Sensitivity: {args.sensitivity})...")

    workers = []
    for s in stations:
        st_pattern = learner.get_or_create_station(
            station_uuid=s["stationuuid"],
            name=s["name"],
            stream_url=s["url"]
        )
        if s.get("is_pure_music"):
            st_pattern.is_pure_music = True

        worker = StationWorker(
            s,
            st_pattern,
            sensitivity=args.sensitivity,
            semantic_classifier=semantic_classifier
        )
        workers.append(worker)

    for w in workers:
        w.start()

    def graceful_shutdown():
        print("\nStopping all stream workers and GPU engine...")
        semantic_classifier.stop()
        for w in workers:
            w.request_stop()
        print("Saving training checkpoint...")
        learner.save_checkpoint()
        print("Exporting intelligence for Android app...")
        res = exporter.export_all()
        print("Successfully exported:")
        for k, v in res.items():
            print(f" - {k}: {v}")
        print("Done!")

    if args.no_dashboard:
        print("Stream Miner running in log mode. Press Ctrl+C to stop.")
        try:
            start_t = time.time()
            while True:
                time.sleep(10)
                connected = sum(1 for w in workers if w.is_connected)
                speech = sum(1 for w in workers if w.is_speech_active)
                checks = semantic_classifier.total_classifications
                ms = int(semantic_classifier.avg_inference_ms)
                print(
                    f"[{time.strftime('%H:%M:%S')}] Connected: {connected}/{len(workers)} | "
                    f"Speech active: {speech} | GPU Checks: {checks} (avg {ms}ms)"
                )
                if args.duration and (time.time() - start_t) >= args.duration:
                    break
        except KeyboardInterrupt:
            pass
        finally:
            graceful_shutdown()
    else:
        dashboard = MinerDashboard(workers, learner, exporter, semantic_classifier=semantic_classifier)
        try:
            dashboard.run_live(refresh_interval=1.0, max_seconds=args.duration)
        except KeyboardInterrupt:
            pass
        finally:
            graceful_shutdown()


if __name__ == "__main__":
    main()
