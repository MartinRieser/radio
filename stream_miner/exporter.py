"""
Exporter for Radio Intelligence.
Generates:
1. station_intelligence.json (for Android assets)
2. training_report.md (Detailed analytics and GPU semantic insights)
"""

from datetime import datetime
import json
import os


class IntelligenceExporter:
    def __init__(self, pattern_learner, output_dir: str = None):
        self.learner = pattern_learner
        if output_dir is None:
            output_dir = os.path.join(os.path.dirname(__file__), "output")
        self.output_dir = output_dir
        os.makedirs(self.output_dir, exist_ok=True)

    def export_all(self, target_assets_dir: str = None) -> dict:
        """Exports JSON files and report."""
        summary = {}
        hourly_heatmaps = {}
        learned_news = {}
        learned_jingles = {}
        pure_music = {}
        learned_prerolls = {}

        stations_dict = {}

        # Load any other stations from disk checkpoint first (multi-instance support)
        if hasattr(self.learner, "checkpoint_path") and os.path.exists(self.learner.checkpoint_path):
            try:
                with open(self.learner.checkpoint_path, "r", encoding="utf-8") as f:
                    disk_data = json.load(f) or {}
                    stations_dict.update(disk_data)
            except Exception:
                pass

        # Update with this instance's active stations
        active_keys = getattr(self.learner, "active_keys", None) or set(self.learner.stations.keys())
        for key in active_keys:
            if key in self.learner.stations:
                stations_dict[key] = self.learner.stations[key].to_dict()

        for key, st_data in stations_dict.items():
            hourly_heatmaps[key] = st_data["hourly_heatmap"]
            if st_data.get("learned_news_window"):
                learned_news[key] = st_data["learned_news_window"]
            if st_data.get("learned_jingle"):
                learned_jingles[key] = st_data["learned_jingle"]
            if st_data.get("is_pure_music"):
                pure_music[key] = True
            if st_data.get("has_preroll"):
                learned_prerolls[key] = True

        export_data = {
            "version": 2,
            "generated_at": datetime.now().isoformat(),
            "station_count": len(stations_dict),
            "hourly_heatmaps": hourly_heatmaps,
            "learned_news_windows": learned_news,
            "learned_jingles": learned_jingles,
            "pure_music_overrides": pure_music,
            "learned_prerolls": learned_prerolls,
            "stations": stations_dict
        }

        # 1. Save to local output directory
        json_path = os.path.join(self.output_dir, "station_intelligence.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(export_data, f, indent=2, ensure_ascii=False)
        summary["intelligence_json"] = json_path

        # 2. Copy directly to Android app assets if directory specified or exists
        if target_assets_dir is None:
            default_assets = os.path.abspath(
                os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
            )
            if os.path.exists(default_assets):
                target_assets_dir = default_assets

        if target_assets_dir and os.path.exists(target_assets_dir):
            asset_target = os.path.join(target_assets_dir, "station_intelligence.json")
            with open(asset_target, "w", encoding="utf-8") as f:
                json.dump(export_data, f, indent=2, ensure_ascii=False)
            summary["copied_to_android_assets"] = asset_target

        # 3. Generate Markdown Report
        report_path = os.path.join(self.output_dir, "training_report.md")
        self._write_markdown_report(report_path, stations_dict)
        summary["report_markdown"] = report_path

        return summary

    def _write_markdown_report(self, path: str, stations: dict):
        with open(path, "w", encoding="utf-8") as f:
            f.write("# 📡 PulseCast GPU Stream Mining & Broadcast-KI Report\n\n")
            f.write(f"- **Generiert am**: `{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}`\n")
            f.write(f"- **Ausgewertete Sender**: {len(stations)}\n")
            f.write("- **KI-Pipeline**: Silero VAD v5 + 3-Band FFT Spektralanalyse + Faster-Whisper (RTX 4070 Ti CUDA FP16)\n\n")
            f.write("---\n\n")

            f.write("## 📊 Übersicht aller Stationen\n\n")
            f.write("| Sender | Typ | Beobachtet | Sprach-Anteil | News-Fenster | Jingle | Erkannte Inhalte (KI) | Pre-Roll |\n")
            f.write("|---|---|---|---|---|---|---|---|\n")

            for key, s in stations.items():
                name = s.get("name", key)
                typ = "🎵 100% Musik" if s.get("is_pure_music") else "📻 Regulär"
                obs_min = round(s.get("total_observed_sec", 0) / 60.0, 1)
                speech_pct = round(s.get("speech_ratio", 0) * 100, 1)
                news = s.get("learned_news_window") or "-"
                jingle = f"`{s.get('learned_jingle')}`" if s.get("learned_jingle") else "-"
                preroll = "⚠️ Ja" if s.get("has_preroll") else "🛡️ Nein"

                cats = s.get("category_counts", {})
                cat_parts = []
                if cats.get("news"):
                    cat_parts.append(f"📰{cats['news']}")
                if cats.get("ads"):
                    cat_parts.append(f"📢{cats['ads']}")
                if cats.get("weather"):
                    cat_parts.append(f"🌦️{cats['weather']}")
                if cats.get("traffic"):
                    cat_parts.append(f"🚗{cats['traffic']}")
                if cats.get("moderation"):
                    cat_parts.append(f"🎙️{cats['moderation']}")
                cat_str = " ".join(cat_parts) if cat_parts else "-"

                f.write(f"| **{name}** | {typ} | {obs_min} Min | {speech_pct}% | {news} | {jingle} | {cat_str} | {preroll} |\n")

            f.write("\n---\n\n")
            f.write("## 🔍 Detaillierte Stunden-Heatmaps (Minuten 0..59)\n\n")
            f.write("Die Heatmaps erfassen die gemessene Sprachwahrscheinlichkeit pro Minute jeder Stunde:\n\n")

            for key, s in stations.items():
                if s.get("total_observed_sec", 0) < 60:
                    continue
                f.write(f"### {s.get('name')}\n")
                f.write(f"- **Durchschnittliche Sprachdauer**: {s.get('avg_speech_duration_sec', 0)} Sek.\n")
                f.write(f"- **News-Fenster**: `{s.get('learned_news_window') or 'Keines erkannt'}`\n\n")

                f.write("```\n")
                f.write("Minuten-Heatmap um die volle Stunde (:55 - :05):\n")
                hm = s.get("hourly_heatmap", [])
                for m in [55, 56, 57, 58, 59, 0, 1, 2, 3, 4, 5]:
                    prob = hm[m] if m < len(hm) else 0.0
                    bar_len = int(prob * 30)
                    bar = "█" * bar_len + "░" * (30 - bar_len)
                    pct = int(prob * 100)
                    f.write(f":{m:02d} | {bar} {pct}%\n")
                f.write("```\n\n")
