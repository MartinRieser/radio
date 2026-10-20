"""
Live Dashboard using Rich.
Displays real-time monitoring of up to 30 radio streams simultaneously,
including GPU Faster-Whisper semantic categorization and live speech transcripts.
"""

from datetime import datetime
import time
from rich.console import Console
from rich.table import Table
from rich.live import Live
from rich.panel import Panel
from rich.layout import Layout
from rich.text import Text


class MinerDashboard:
    def __init__(self, workers: list, learner, exporter, semantic_classifier=None):
        self.workers = workers
        self.learner = learner
        self.exporter = exporter
        self.semantic_classifier = semantic_classifier
        self.console = Console()
        self.start_time = time.time()

    def generate_table(self) -> Table:
        table = Table(title="📡 PulseCast GPU Stream Mining & Broadcast-KI Terminal", expand=True)

        table.add_column("#", justify="right", style="cyan", no_wrap=True, width=3)
        table.add_column("Sender", style="bold white", min_width=18, max_width=23)
        table.add_column("Live Status (KI + VAD)", justify="left", min_width=24)
        table.add_column("Sprache %", justify="right", style="yellow", width=10)
        table.add_column("News-Fenster", justify="center", style="magenta", width=13)
        table.add_column("Jingle", justify="center", style="green", width=12)
        table.add_column("Aktueller Songtitel / Live KI-Transkript", style="dim", overflow="ellipsis")

        for idx, w in enumerate(self.workers, 1):
            s = w.get_summary()
            name = s["name"]
            status = s["status"]
            score_pct = int(s.get("speech_score", 0.0) * 100)

            if any(k in status for k in ("Sprache", "Nachrichten", "Werbung", "Moderation")):
                status_text = Text(f"{status} [{score_pct}%]", style="bold red")
            elif any(k in status for k in ("Wetter", "Verkehr")):
                status_text = Text(f"{status} [{score_pct}%]", style="bold yellow")
            elif "Musik" in status:
                status_text = Text(f"{status} [{score_pct}%]", style="bold green")
            elif "Verbunden" in status:
                status_text = Text(status, style="blue")
            else:
                status_text = Text(status, style="yellow")

            ratio_pct = f"{s['speech_ratio'] * 100:.1f}%"
            if s["is_pure_music"]:
                ratio_pct += " (100%)"

            news = s["learned_window"] or "-"
            jingle = f"`{s['learned_jingle']}`" if s["learned_jingle"] else "-"
            title = s["title"] or "-"

            table.add_row(
                str(idx),
                name,
                status_text,
                ratio_pct,
                news,
                jingle,
                title
            )

        return table

    def generate_header(self) -> Panel:
        now = datetime.now()
        elapsed = int(time.time() - self.start_time)
        m, s = divmod(elapsed, 60)
        h, m = divmod(m, 60)

        connected_count = sum(1 for w in self.workers if w.is_connected)
        speech_count = sum(1 for w in self.workers if w.is_speech_active)
        music_count = sum(1 for w in self.workers if w.is_connected and not w.is_speech_active)

        gpu_info = "Deaktiviert"
        if self.semantic_classifier is not None:
            checks = self.semantic_classifier.total_classifications
            ms = int(self.semantic_classifier.avg_inference_ms)
            gpu_info = f"{self.semantic_classifier.device_label} ({checks} Checks, Ø {ms}ms)"

        info = (
            f"[bold cyan]Laufzeit:[/bold cyan] {h:02d}:{m:02d}:{s:02d} | "
            f"[bold cyan]Uhrzeit:[/bold cyan] {now.strftime('%H:%M:%S')} (:{now.minute:02d}) | "
            f"[bold green]Streams:[/bold green] {connected_count}/{len(self.workers)} | "
            f"[bold red]🗣️ Sprache:[/bold red] {speech_count} | "
            f"[bold green]🎵 Musik:[/bold green] {music_count} | "
            f"[bold magenta]🧠 KI:[/bold magenta] {gpu_info}"
        )
        return Panel(info, style="white on dark_blue", subtitle="[bold yellow]Strg+C zum Stoppen & Exportieren[/bold yellow]")

    def run_live(self, refresh_interval: float = 1.0, max_seconds: int = None):
        """Runs the live terminal dashboard."""
        with Live(console=self.console, refresh_per_second=int(1.0 / refresh_interval), screen=True) as live:
            last_checkpoint = time.time()
            while True:
                layout = Layout()
                layout.split_column(
                    Layout(self.generate_header(), size=3),
                    Layout(self.generate_table())
                )
                live.update(layout)

                # Periodic checkpoint every 5 minutes
                if time.time() - last_checkpoint >= 300:
                    self.learner.save_checkpoint()
                    self.exporter.export_all()
                    last_checkpoint = time.time()

                if max_seconds and (time.time() - self.start_time) >= max_seconds:
                    break

                time.sleep(refresh_interval)
