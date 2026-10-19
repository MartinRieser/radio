# 📻 PulseCast — Modern Web Radio Player

A sleek, modern web radio player powered by the free community **[Radio Browser API](https://www.radio-browser.info/)** (over 40,000+ free online radio stations worldwide) with handpicked featured presets, real-time search, sleep timer, favorites, and live audio visualizers.

---

## 🌐 Free Radio Stream Providers

When listening to web radio, you don't need a paid service. The best free providers delivering direct audio stream URLs are:

1. **[Radio-Browser.info](https://www.radio-browser.info/) (Built-in via API)**:
   - A free, open-source, community-driven database of **40,000+ live stations**.
   - Provides free JSON APIs with no API key required and CORS enabled.
   - Allows searching by genre/tag (Jazz, Chillout, Rock, Ambient, Pop, News), country, codec, and popularity.
   - Built directly into PulseCast!

2. **Public Broadcasters & Independent Curators**:
   - **SomaFM** (Groove Salad, Drone Zone, Secret Agent, etc. — 100% listener supported, free commercial-free streams)
   - **SRG SSR / Swiss Satellite Radio** (Radio Swiss Pop, Swiss Jazz, Swiss Classic — pristine 128 kbps commercial-free streams)
   - **BBC World Service** & **NPR** (Global news and talk)
   - **KEXP 90.3 FM Seattle** (Listener-powered indie & alternative music)

---

## 🚀 How to Run the App

You have two easy ways to use the app:

### Option 1: Open Directly in Your Browser (Instant)
Simply double-click [`index.html`](file:///c:/Users/Martin%20Rieser/radio/index.html) or right-click it and choose **"Open with..." > Chrome / Edge / Firefox**.

### Option 2: Run via Local Dev Server
Open PowerShell in this directory (`c:\Users\Martin Rieser\radio`) and run:

```bash
# Using Python (built-in):
python -m http.server 3000

# Or using Node.js:
npx serve .
```
Then visit **`http://localhost:3000`** in your browser.

---

## ✨ Features

- **40,000+ Stations**: Live search by name, genre/tag (Chillout, Lo-Fi, Pop, Rock, Jazz, Classical, Electronic, News), and country.
- **Instant Quick-Picks**: 12 curated, high-uptime streams ready to play with one click.
- **Glassmorphic Player Bar**:
  - Live animated visualizer (Neon wave & frequency bars mode).
  - High quality audio playback with buffer and connection status.
  - Previous / Next station navigation.
  - Volume control with mute toggle and volume persistence.
- **Favorites System**: Click the ❤️ heart icon to bookmark any station into your persistent favorites list.
- **Sleep Timer**: Auto-off timer (15, 30, 45, 60 minutes) for falling asleep to music.
- **Custom Stream URL**: Paste any direct Icecast, Shoutcast, MP3, or AAC radio URL to stream directly.
- **Keyboard Shortcuts**:
  - `Space`: Play / Pause
  - `Alt + Right`: Next Station
  - `Alt + Left`: Previous Station
  - `M`: Toggle Mute
