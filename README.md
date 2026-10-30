# PulseCast – Native Android Radio App

[![Build Android APK & GitHub Release](https://github.com/MartinRieser/radio/actions/workflows/build-apk.yml/badge.svg)](https://github.com/MartinRieser/radio/actions/workflows/build-apk.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-brightgreen.svg)](https://android.com)

Eine moderne, native Android-Radio-App entwickelt mit **Kotlin**, **Jetpack Compose** und **AndroidX Media3 (ExoPlayer)** für unterbrechungsfreies, erstklassiges Internetradio im Alltag, im Auto und im Hintergrund.

---

## ✨ Highlights & Kernfunktionen

### 1. 🛑 Direkte Play/Stop-Steuerung (Keine Hintergrund-Pufferung)
- **Echte Live-Radio-Kontrolle**: Ist ein Stream aktiv, zeigen die Senderkarte, die Mini-Player-Leiste und der Automodus sofort den **Stop-Button** (`Icons.Default.Stop`). Ist kein Stream aktiv, erscheint der **Play-Button** (`Icons.Default.PlayArrow`).
- **Keine Datenverschwendung durch Pause-Pufferung**: Ein Antippen von Stop beendet die Netzwerkverbindung unmittelbar und verwirft Zwischenpuffer. Beim erneuten Starten verbindet sich PulseCast direkt frisch mit der aktuellen Live-Kante des Senders – ohne Verzögerung und ohne unbemerktes Weiterpuffernd im Hintergrund.

### 2. 💎 Drei separate Favoritenlisten (Herz, Diamant, Stern)
- **Organisierte Favoriten**: Sender können flexibel in bis zu drei separaten Listen verwaltet werden:
  - ❤️ **Favoriten 1 (Herz)**
  - 💎 **Favoriten 2 (Diamant)**
  - ⭐ **Favoriten 3 (Stern)**
- **Intuitive Zuweisung**: Über das Menü an jeder Senderkarte lässt sich mit einem Fingertipp zwischen den Listen wechseln oder ein Sender entfernen.
- **Schnellfilter & Zähler**: Filter-Chips mit Live-Zählern (z. B. `❤️ Herz (20)`, `💎 Diamant (5)`) erlauben blitzschnellen Zugriff auf die gewünschte Sammlung.

### 3. 🚗 Automodus & Android Auto
- **Große Bedienelemente (Car Drive Mode)**: Speziell für sichere Bedienung im Fahrzeug mit extragroßen Schaltflächen, senderübergreifender Favoritenauswahl und integrierter Geschwindigkeitsanzeige (`km/h`).
- **Native Android Auto Unterstützung**: Vollständige Integration als Android Auto Audio-App über `MediaLibraryService` (`RadioPlaybackService`) inklusive durchsuchbarer Senderlisten und Fahrzeugdisplay-Cover-Art.
- **Lenkrad- & Bluetooth-Steuerung**: Hardware-Tasten (Nächster/Vorheriger Sender, Play/Pause/Mute, Headset-Hooks) werden auf Senderweiterschaltung und Stream-Steuerung gemappt.

### 4. 🔋 Zuverlässiger Standby-Betrieb
- **Unterbrechungsfreier Hintergrundbetrieb**: Dank `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` und `WIFI_MODE_FULL_LOW_LATENCY` läuft der Stream auch bei ausgeschaltetem Bildschirm stabil weiter.
- **Ausnahme von der Akku-Optimierung**: Erkennt moderne Android Doze-Modi und bittet um Deaktivierung der Akku-Einschränkungen, damit der Stream nicht vom System gekillt wird.
- **Automatische Wiederverbindung**: Bei Netzwechseln (z. B. Wechsel von WLAN auf Mobilfunk) stellt PulseCast die Verbindung automatisch wieder her.

### 5. 📻 Riesiges Senderverzeichnis & Live-Metadaten
- **Kuratierte Sender**: Handverlesene Top-Sender aus Deutschland, Österreich, der Schweiz und weltweit mit geprüften HTTPS-Streams und Bitraten bis 320 kbps.
- **Radio-Browser API Integration**: Globale Suche nach Sendernamen, Genres und Ländern mit automatischer Deduplizierung.
- **ICY Live-Metadaten**: Formatierte Anzeige von aktuellem Titel und Interpret in Echtzeit.

### 6. 🔄 Integrierter In-App Updater
- PulseCast prüft automatisch auf neue Releases auf GitHub und ermöglicht den direkten Download und die Installation neuer APK-Versionen direkt aus der App heraus.

---

## 🛠️ Architektur & Tech-Stack

```
app/src/main/java/com/pulsecast/radio/
├── MainActivity.kt               # Entry-Activity, Edge-to-Edge & Berechtigungs-Management
├── PulseCastApp.kt               # Application-Klasse & Media-Notification-Channel
├── model/
│   └── Station.kt                # Sender-Modell, URL-Validierung, Deduplizierung & Flags
├── data/
│   ├── CuratedStations.kt        # Lokale kuratierte Sender-Datenbank (curated_stations.json)
│   ├── RadioBrowserApi.kt        # Retrofit-Schnittstelle zur Radio-Browser API
│   ├── ApiClient.kt              # HTTP-Client mit resilienter Server-Rotation
│   └── HistoryAndFavoritesRepository.kt # Persistenz der 3 Favoritenlisten
├── playback/
│   ├── RadioPlaybackService.kt   # MediaLibraryService mit ForwardingPlayer & Bluetooth/Car Controls
│   ├── PlaybackController.kt     # Client-Controller für UI-Interaktion & Live-Timer
│   ├── RadioPlaybackBridge.kt    # Schnittstelle für Car-Mode & Service-Synchronisation
│   └── RadioMetadataFormatter.kt # Bereinigung & Formatierung von ICY-Metadaten
├── updater/
│   └── AppUpdateManager.kt       # GitHub Release Check & In-App APK Download
└── ui/
    ├── theme/                    # Material 3 Neon-Dark Theme & Typografie
    ├── components/
    │   ├── StationCard.kt        # Senderkarte mit Play/Stop-Logik & Favoriten-Popup
    │   ├── PlayerBar.kt          # Schlanke Mini-Player-Leiste mit Live-Status
    │   └── AppUpdateDialog.kt    # Update-Benachrichtigung & Download-Dialog
    ├── screens/
    │   ├── HomeScreen.kt         # Hauptansicht (Suche, Favoriten-Tabs, Senderliste)
    │   └── CarDriveModeScreen.kt # Kontraststarke Großtasten-Ansicht fürs Auto
    └── viewmodel/
        └── MainViewModel.kt      # StateHolder & Business Logic für die UI
```

---

## 🚀 Bauen & Installieren

### Voraussetzungen
- Android SDK 35
- JDK 17
- Gradle 9+ (über den mitgelieferten Gradle Wrapper `gradlew`)

### 1. APK über die Kommandozeile bauen
```bash
# Debug APK erstellen:
./gradlew assembleDebug

# Die generierte APK befindet sich unter:
# app/build/outputs/apk/debug/app-debug.apk
```

### 2. Auf verbundenem Android-Smartphone installieren
```bash
# Per ADB direkt auf das Gerät aufspielen:
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Alternativ das beiliegende Windows-Skript verwenden:
update_and_install.bat
```

### 3. Automatische GitHub Actions Builds
Jeder Push auf den Branch `android-native` oder `main` baut über `.github/workflows/build-apk.yml` automatisch die APK und erstellt ein GitHub Release mit Download-Link.

---

## 🧪 Tests ausführen

```bash
# Unit-Tests starten:
./gradlew testDebugUnitTest
```

---

## 📄 Lizenz

Dieses Projekt ist unter der **Apache 2.0 Lizenz** lizenziert – siehe die [LICENSE](LICENSE) Datei für Details.
