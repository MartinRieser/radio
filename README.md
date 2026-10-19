# 📻 PulseCast — Modern Web Radio Player & 3D Globe Radar

A sleek, modern web radio player powered by the free community **[Radio Browser API](https://www.radio-browser.info/)** (over 40,000+ free online radio stations worldwide) featuring an **interactive 3D World Globe Radar**, handpicked featured presets, real-time search, sleep timer, favorites, and live audio visualizers.

---

## 🌐 Free Radio Stream Providers

When listening to web radio, you don't need a paid service. The best free providers delivering direct audio stream URLs are:

1. **[Radio-Browser.info](https://www.radio-browser.info/) (Built-in via API)**:
   - A free, open-source, community-driven database of **40,000+ live stations**.
   - Provides free JSON APIs with no API key required and CORS enabled.
   - Allows searching by genre/tag (Jazz, Chillout, Rock, Ambient, Pop, News), country, codec, and popularity.
   - Over **1,000+ stations with exact geolocation coordinates (`geo_lat`, `geo_long`)** plotted directly onto our 3D Globe!

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

## ✨ Features & Views

### 1. 🌍 Interactive 3D World Globe Radar (Radio Garden Inspired)
- **Deep Zoom to City Level**: Zoom all the way from deep space down to street/city altitude (distance 103.8 above the 100-radius Earth sphere).
- **Double-Click Dive**: Double-click any location or dot on the globe to swoop straight in close to that city.
- **Center Targeting Reticle**: Radio Garden style reticle locks onto stations as you rotate the globe with animated green target lock-on.
- **Multi-Station City Explorer**: When targeting a city (Vienna, Berlin, London, Paris, Tokyo, etc.), it displays all other stations in that area so you can flick between local channels with a single tap.
- **1,500+ Geo-located Pinpoint Dots**: Ultra-crisp pinpoint light dots across every continent with intelligent country & region geolocation mapping.
- **Calibrated Physics**: Non-jerky weighted rotation sensitivity, gentle logarithmic wheel zoom, and stationary default (no unwanted spinning).
- **Region Fly-To Jumps**: Instant one-click fly-to buttons for Europe, North America, South America, Asia, Africa, and Oceania.
- **Live Beacon Pulse**: The currently playing station emits an animated radar wave from its exact coordinates on the globe.

### 2. 🗂️ Card Grid & Search View
- **40,000+ Stations**: Live search by name, genre/tag (Chillout, Lo-Fi, Pop, Rock, Jazz, Classical, Electronic, News), and country.
- **Curated Quick-Picks**: 12 verified high-uptime streams ready to play with one click.
- **Filters & Sorting**: Filter by country or sort by popularity, highest votes, or bitrate.

### 3. 🎵 Glassmorphic Player Bar
- Live animated audio visualizer (Neon wave & frequency bars mode).
- High quality audio playback with buffer and connection status.
- Previous / Next station navigation.
- Volume slider with mute toggle and volume persistence.
- **Favorites System**: Click the ❤️ heart icon to bookmark any station.
- **Sleep Timer**: Auto-off timer (15, 30, 45, 60 minutes) for falling asleep to music.
- **Custom Stream URL**: Paste any direct Icecast, Shoutcast, MP3, or AAC radio URL to stream directly.

---

## ⌨️ Keyboard Shortcuts
- `Space`: Play / Pause
- `Alt + Right`: Next Station
- `Alt + Left`: Previous Station
- `M`: Toggle Mute
