# PulseCast – Native Android Radio App

Eine moderne, native Android-Radio-App mit **Kotlin**, **Jetpack Compose** und **AndroidX Media3 (ExoPlayer)** für hochqualitatives, unterbrechungsfreies Internetradio im Hintergrund und auf dem Sperrbildschirm.

---

## 🚀 Kernfunktionen

1. **Modernste Audio-Engine (AndroidX Media3 & ExoPlayer)**:
   - Robuste Wiedergabe von MP3, AAC, OGG, HLS (`.m3u8`) und Shoutcast/Icecast-Streams.
   - **Hintergrund-Wiedergabe & Foreground Service**: Der Stream reißt nicht ab, wenn der Bildschirm ausgeschaltet oder andere Apps genutzt werden.
   - **Sperrbildschirm & Benachrichtigungs-Steuerung**: Volle Integration in die Android-Mediensteuerung (Play/Pause, Vorheriger, Nächster).
   - **Automatisches Audio-Focus-Management**: Pausiert bei eingehenden Anrufen und dämpft („ducking“) bei Navigationsansagen.
   - **Headphones Becoming Noisy**: Pausiert automatisch beim Abziehen von Kopfhörern oder Trennen von Bluetooth.
   - **ICY Live-Metadaten**: Zeigt den aktuellen Songtitel und Interpreten in Echtzeit an.

2. **Wiedergabeverlauf (> 1 Minute Regel)**:
   - Ein Sender wird **nur dann** im Verlauf gespeichert, wenn die Abspielzeit mindestens **1 Minute (60 Sekunden)** beträgt.
   - Kürzere Abspielzeiten werden streng ignoriert.
   - **Unbegrenzte Kapazität** – keine Beschränkung der Senderanzahl.
   - Zuletzt gehörte Sender stehen ganz oben mit relativer Zeitangabe (*„vor 5 Min.“*, *„Gestern“*).
   - Eigene Schaltfläche zum Leeren des Verlaufs.

3. **Pre-Roll Werbe-Erkennung**:
   - Automatische Erkennung von Werbenetzwerken (AdsWizz, TargetSpot, StreamTheWorld) vs. werbefreien öffentlich-rechtlichen & Community-Sendern (SomaFM, ORF, SRG SSR, SWR, BR, WDR, BBC, FIP).
   - Badges: `🛡️ Werbefrei` vs `⚠️ Pre-Roll`.

4. **Kategorien & Sender-Verzeichnis**:
   - **Featured**: 19 handverlesene europäische & weltweite Top-Sender (offline sofort verfügbar).
   - **Trending / Top Click**: Die beliebtesten weltweiten Sender über die Radio-Browser-API.
   - **Genre-Tabs**: Chillout, Pop, Rock, Jazz, Electronic.
   - **Live-Suche**: Schnelle Echtzeit-Suche nach Sendernamen, Genres und Ländern.

5. **Sleep Timer**:
   - Schlummerfunktion für 15, 30, 45, 60 oder 90 Minuten mit Countdown-Anzeige.

---

## 🛠️ Architektur & Tech-Stack

```
app/src/main/java/com/pulsecast/radio/
├── MainActivity.kt               # Entry-Activity & Permission Request (POST_NOTIFICATIONS)
├── PulseCastApp.kt               # Application-Klasse (Notification-Channels)
├── model/
│   └── Station.kt                # Sender-Datenmodell & Hilfsmethoden
├── data/
│   ├── CuratedStations.kt        # Handverlesene Sender aus assets/curated_stations.json
│   ├── RadioBrowserApi.kt        # Retrofit-API für das Radio-Browser-Netzwerk
│   ├── ApiClient.kt              # HTTP-Client mit Spiegelserver-Rotation
│   └── HistoryAndFavoritesRepository.kt # Persistierung von Favoriten & 60s-Verlauf
├── playback/
│   ├── RadioPlaybackService.kt   # MediaSessionService (Media3 + ExoPlayer im Hintergrund)
│   └── PlaybackController.kt     # Verbindung zur UI, Live-Timer & 60s-Verlaufstracker
└── ui/
    ├── theme/                    # PulseCast Neon-Dark-Theme (Material 3)
    ├── components/
    │   ├── StationCard.kt        # Senderkarte mit Equalizer-Animation & Badges
    │   ├── PlayerBar.kt          # Feste Mini-Player-Leiste am unteren Bildschirmrand
    │   └── SleepTimerDialog.kt   # Dialog zur Auswahl des Sleep-Timers
    ├── viewmodel/
    │   └── MainViewModel.kt      # Zentraler StateHolder für Compose
    └── screens/
        └── HomeScreen.kt         # Hauptbildschirm mit Suche, Pills & Senderliste
```

---

## 📱 Projekt in Android Studio öffnen & bauen

1. Starten Sie **Android Studio**.
2. Wählen Sie **File > Open** und wählen Sie das geklonte Projektverzeichnis `radio` aus.
3. Android Studio erkennt das Gradle-Projekt automatisch und lädt die Abhängigkeiten herunter.
4. Schließen Sie Ihr Android-Smartphone per USB an (mit aktiviertem **USB-Debugging**) oder starten Sie einen Emulator.
5. Drücken Sie den grünen **Play-Button (Run 'app')** in Android Studio.

---

## 📦 APK bauen ohne Android Studio (Kommandozeile)

Wenn Java / JDK auf Ihrem Rechner installiert ist:

```bash
# Debug-APK erstellen:
./gradlew assembleDebug

# Die fertige APK liegt unter:
# app/build/outputs/apk/debug/app-debug.apk
```

### Installation auf dem Smartphone (Sideloading ohne Play Store):

1. **Per USB (ADB)**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. **Per Smartphone-Download**:
   - Starten Sie am PC einen lokalen Server:
     ```bash
     python -m http.server 8088
     ```
   - Öffnen Sie am Smartphone-Browser: `http://<IP-Ihres-PCs>:8088/app/build/outputs/apk/debug/app-debug.apk`.
   - Datei antippen und installieren.


---

## 📄 Lizenz

Dieses Projekt ist unter der **Apache 2.0 Lizenz** lizenziert – siehe die [LICENSE](LICENSE) Datei für Details.
