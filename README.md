# 📻 PulseCast — Modern Web Radio Player

A sleek, modern web radio player powered by the free community **[Radio Browser API](https://www.radio-browser.info/)** (over 40,000+ free online radio stations worldwide) featuring an **ultra-minimalist interface**, **Pre-Roll ad detection**, verified ad-free streams, handpicked presets, real-time search, sleep timer, favorites, and live audio visualizers.

---

## 🌐 Free Radio Stream Providers & Ad-Free Listening

When listening to web radio, you don't need a paid service. The best free providers delivering direct audio stream URLs are:

1. **[Radio-Browser.info](https://www.radio-browser.info/) (Built-in via API)**:
   - A free, open-source, community-driven database of **40,000+ live stations**.
   - Provides free JSON APIs with no API key required and CORS enabled.
   - Allows searching by genre/tag (Chillout, Lo-Fi, Pop, Rock, Jazz, Classical, Electronic, News), country, codec, and popularity.

2. **Public Broadcasters & Independent Curators (100% Werbefrei / 0% Pre-Roll)**:
   - **SRG SSR / Swiss Satellite Radio** (Radio Swiss Pop, Swiss Jazz, Swiss Classic — pristine 128 kbps commercial-free streams)
   - **SomaFM** (Groove Salad, Drone Zone, Secret Agent, etc. — 100% listener supported, free commercial-free streams)
   - **WDR / ARD & Deutschlandfunk** (Public service German stations)
   - **ORF / FM4 & Ö3** (Austrian public service radio)
   - **BBC World Service & BBC Radio 1** (Global news, music, and talk)
   - **Radio France / FIP** (Eclectic, groove, and jazz without interruptions)
   - **KEXP 90.3 FM Seattle** (Listener-powered indie & alternative music)

---

## 🛡️ Pre-Roll Ad Detection

PulseCast automatically inspects radio streaming endpoints and identifies:
- **🛡️ Werbefrei (0% Pre-Roll)**: Guaranteed commercial-free public service and non-profit community stations.
- **⚠️ Pre-Roll Spot möglich**: Commercial broadcasters utilizing Server-Side Ad Insertion (SSAI) networks (*Triton Digital, AdsWizz, StreamTheWorld, TargetSpot*).
- **Direct Stream (Standard)**: Direct Icecast / Shoutcast streams.
- **"🛡️ Werbefrei" Filter Pill**: One-click filter in the genre bar to immediately browse guaranteed ad-free streams.

---

## 🚀 How to Run the App

You have two easy ways to use the app:

### Option 1: Open Directly in Your Browser (Instant)
Simply double-click [`index.html`](file:///c:/Users/Martin%20Rieser/radio/index.html) or right-click it and choose **"Open with..." > Chrome / Edge / Firefox**.

### Option 2: Run via Local Dev Server
Open PowerShell in this directory (`c:\Users\Martin Rieser\radio`) and run:

```bash
# Using Python (built-in):
python -m http.server 8088

# Or using Node.js:
npx serve .
```
Then visit **`http://localhost:8088`** in your browser.

---

## ✨ Features & Interface

### 1. 🗂️ Clean Card Grid & Search
- **Ultra-Minimalist Design**: Subtle ambient glow, uncluttered cards, clean genres, and responsive layout.
- **40,000+ Stations**: Live search by name, genre/tag, and country.
- **Curated Quick-Picks**: Verified high-uptime streams ready to play with one click.
- **Filters & Sorting**: Filter by country or sort by popularity, highest votes, or bitrate.

### 2. 🎵 Floating Glassmorphic Player Bar
- Island pill player bar with smooth backdrop blur.
- Live Pre-Roll status badge and bitrate/codec indicator.
- Animated audio visualizer (Neon wave & frequency bars mode).
- Previous / Next station navigation.
- Volume slider with mute toggle and volume persistence.
- **Favorites System**: Click the ❤️ heart icon to bookmark any station. Instant access via the **Favorites** button or shortcut `F`.
- **Sleep Timer**: Auto-off timer (15, 30, 45, 60 minutes) for falling asleep to music.
- **Custom Stream URL**: Paste any direct Icecast, Shoutcast, MP3, or AAC radio URL to stream directly.

### 3. 🔄 Stream Links Manager (On-Demand & Automatic Updates)
- **On-Demand Update**: Synchronize and verify all stream URLs against the live Radio Browser API database.
- **Automatic Startup Sync**: Background check ensuring saved favorites and featured presets have fresh, working stream links.
- **Self-Healing on Stream Error**: Re-queries the API for an updated working endpoint and resumes playback seamlessly if a link expires.

---

## ⌨️ Keyboard Shortcuts
- `Space`: Play / Pause
- `Alt + Right`: Next Station
- `Alt + Left`: Previous Station
- `M`: Toggle Mute
- `F`: Show / Toggle My Favorite Stations
- `U`: Update / Refresh Station Links On Demand
- `Esc`: Close Modals / Menus
