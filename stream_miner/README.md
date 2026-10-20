# 📡 PulseCast PC Stream Miner & Multi-Stream Trainer

Ein performantes Python-Tool zur **gleichzeitigen Analyse von bis zu ca. 30 Radiostreams** auf Ihrem PC. 

Anstatt mühsam und akkuintensiv auf dem Smartphone jeden Sender einzeln über Wochen anzuhören, streamt dieses Tool parallel alle gewünschten Sender auf dem PC, erkennt präzise **wann Sprache/Nachrichten/Werbung startet und aufhört**, lernt typische **Stunden-Heatmaps & Jingle-Fingerprints** und hinterlegt das Trainingsergebnis direkt in der Android Radio App.

---

## 🚀 Kernfunktionen

1. **Gleichzeitiges Streaming von bis zu 30+ Sendern**:
   - Parallele Audio-Dekodierung (MP3, AAC, HLS) via nativem C/C++ `PyAV` (FFmpeg) bei minimaler CPU-Last.
   - Automatische Reconnection bei Stream-Abbrüchen.
   - In-Stream ICY-Metadaten-Auswertung (Songtitel, Künstler, Werbe-Keywords).

2. **Hybrid Voice Activity Detection (VAD) & DSP**:
   - **Silero Neural VAD (ONNX)**: Erkennt menschliche Sprache mit >98% Zuverlässigkeit und trennt Sprache von Gesang/Musik.
   - **Acoustical DSP Features**: RMS Dynamic Range, Zero-Crossing-Rate (ZCR) Varianz und Low-Energy Frame Rate (exakt abgestimmt auf die App-Logik).
   - **Hysterese & Anti-Flicker**: Erkennt saubere Start- und Stopp-Zeitpunkte von Sprachblöcken ohne Flackern.

3. **Muster- & Verhaltens-Erkennung**:
   - **Stunden-Heatmaps (0..59)**: Prozentuale Sprachwahrscheinlichkeit pro Minute jeder Stunde.
   - **Nachrichten-Fenster-Erkennung**: Automatische Identifikation wiederkehrender Nachrichtenblöcke (z.B. `:58 - :03` oder `:29 - :32`).
   - **Jingle- & Stinger-Mining**: Schneidet die letzten 2–5 Sekunden vor jedem Sprachbeginn um die volle Stunde mit und extrahiert 16-Band-Energie-Fingerprints (kompatibel mit dem Android `StationSignalCollector`).
   - **Pre-Roll Werbe-Erkennung**: Erkennt vorgeschaltete Audio-Spots direkt nach Verbindungsaufbau.
   - **100% Musik Klassifizierung**: Erkennt Sender mit < 2.5% Sprachanteil automatisch als werbe- und nachrichtenfreie Musiksender.

4. **Nahtlose Integration in die Android-App**:
   - Exportiert die Ergebnisse als `station_intelligence.json` direkt in den Assets-Ordner der Android-App (`app/src/main/assets/station_intelligence.json`).
   - `StationIntelligenceRepository` in der App lädt diese Vorkonfiguration sofort beim Start – die App ist vom ersten Moment an für alle 30 Sender vortrainiert!

5. **Interaktives Live-Dashboard (Rich UI)**:
   - Übersichtliche Echtzeit-Tabelle aller 30 Sender mit Status (🎵 Musik, 🗣️ Sprache mit Live-Sekundenzähler), erkannter News-Zeit, Jingle-Hash und aktuellem Titel.

---

## 🛠️ Schnellstart auf dem PC

### 1. Start per Doppelklick:
Im Projektordner einfach auf **`run_miner.bat`** doppelklicken.

### 2. Oder manuell per Terminal:
```bash
# Startet das Training für die vorkonfigurierten 30 Sender mit Live-Dashboard:
python -m stream_miner.main --stations stream_miner/stations.json --max-streams 30

# Nach gewünschter Zeit (z.B. 1 Stunde oder über Nacht):
# Drücken Sie einfach Ctrl+C im Terminal.
```

### 3. Weitere Parameter:
```bash
# Zeitgesteuert laufen lassen (z.B. 3600 Sekunden = 1 Stunde):
python -m stream_miner.main --duration 3600

# Als einfacher Hintergrund-Logger ohne Terminal-GUI:
python -m stream_miner.main --no-dashboard

# Bestehenden Trainings-Checkpoint sofort für die Android-App exportieren:
python -m stream_miner.main --export-only
```

---

## 📁 Dateien & Verzeichnisstruktur

```
stream_miner/
├── stations.json                # Liste der 30 vorkonfigurierten Radiosender
├── silero_vad.onnx              # Ultraleichtes neuronales VAD-Modell (~1.5 MB)
├── audio_detector.py            # Neural VAD + Akustik-DSP-Klassifikator
├── fingerprinter.py             # 16-Band Spektral-Jingle-Fingerprinter
├── station_worker.py            # Parallele Stream-Worker (PyAV / FFmpeg)
├── pattern_learner.py           # Heatmap-, News-Window- & Jingle-Lernengine
├── exporter.py                  # JSON- & Markdown-Report-Generator
├── dashboard.py                 # Live Rich-Terminal-UI
├── main.py                      # CLI-Einstiegspunkt
└── output/
    ├── station_intelligence.json # Exportierte Trainingsdaten
    └── training_report.md       # Detaillierter Markdown-Analysebericht
```

---

## 📱 Wie das Ergebnis in die Radio-App gelangt

1. Das Tool speichert das Ergebnis automatisch in:
   `app/src/main/assets/station_intelligence.json`
2. Beim nächsten Bauen oder Starten der App lädt [`StationIntelligenceRepository`](../app/src/main/java/com/pulsecast/radio/data/StationIntelligenceRepository.kt) diese Daten automatisch ein.
3. Die App weiß sofort:
   - Wann welcher Sender typischerweise Nachrichten oder Werbung sendet.
   - Welche Jingle-Muster vor den Nachrichten ertönen.
   - Welche Sender 100% Musik spielen und sich als unterbrechungsfreie Fallback-Stationen eignen.
