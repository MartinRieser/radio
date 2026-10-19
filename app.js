/**
 * PulseCast Web Radio
 * Modern Web Radio player powered by the free Radio Browser API
 * Featuring Grid View & 3D Interactive World Globe Radar View
 */

(function () {
  'use strict';

  // --- Configuration & Endpoints ---
  const API_SERVERS = [
    'https://de1.api.radio-browser.info',
    'https://nl1.api.radio-browser.info',
    'https://at1.api.radio-browser.info'
  ];
  let currentServerIndex = 0;

  function getApiBase() {
    return API_SERVERS[currentServerIndex];
  }

  function rotateServer() {
    currentServerIndex = (currentServerIndex + 1) % API_SERVERS.length;
    console.warn('Rotated to backup API server:', getApiBase());
  }

  // --- Geolocation Reference Table for 55+ Countries & Regions ---
  const COUNTRY_COORDS = {
    AT: { lat: 47.5162, lon: 14.5501, spread: 1.4, name: 'Austria' },
    DE: { lat: 51.1657, lon: 10.4515, spread: 2.2, name: 'Germany' },
    CH: { lat: 46.8182, lon: 8.2275, spread: 1.0, name: 'Switzerland' },
    FR: { lat: 46.6033, lon: 1.8883, spread: 2.8, name: 'France' },
    IT: { lat: 41.8719, lon: 12.5674, spread: 2.5, name: 'Italy' },
    ES: { lat: 40.4637, lon: -3.7492, spread: 2.5, name: 'Spain' },
    GB: { lat: 54.0037, lon: -2.5479, spread: 2.2, name: 'United Kingdom' },
    UK: { lat: 54.0037, lon: -2.5479, spread: 2.2, name: 'United Kingdom' },
    US: { lat: 39.8283, lon: -98.5795, spread: 7.0, name: 'United States' },
    CA: { lat: 56.1304, lon: -106.3468, spread: 6.5, name: 'Canada' },
    NL: { lat: 52.1326, lon: 5.2913, spread: 0.8, name: 'Netherlands' },
    BE: { lat: 50.5039, lon: 4.4699, spread: 0.6, name: 'Belgium' },
    PL: { lat: 51.9194, lon: 19.1451, spread: 2.0, name: 'Poland' },
    CZ: { lat: 49.8175, lon: 15.4730, spread: 1.0, name: 'Czech Republic' },
    SK: { lat: 48.6690, lon: 19.6990, spread: 0.9, name: 'Slovakia' },
    HU: { lat: 47.1625, lon: 19.5033, spread: 1.0, name: 'Hungary' },
    SE: { lat: 60.1282, lon: 18.6435, spread: 3.5, name: 'Sweden' },
    NO: { lat: 60.4720, lon: 8.4689, spread: 3.5, name: 'Norway' },
    DK: { lat: 56.2639, lon: 9.5018, spread: 0.9, name: 'Denmark' },
    FI: { lat: 61.9241, lon: 25.7482, spread: 2.8, name: 'Finland' },
    IE: { lat: 53.4129, lon: -8.2439, spread: 1.0, name: 'Ireland' },
    PT: { lat: 39.3999, lon: -8.2245, spread: 1.5, name: 'Portugal' },
    GR: { lat: 39.0742, lon: 21.8243, spread: 1.5, name: 'Greece' },
    RO: { lat: 45.9432, lon: 24.9668, spread: 1.8, name: 'Romania' },
    BG: { lat: 42.7339, lon: 25.4858, spread: 1.2, name: 'Bulgaria' },
    HR: { lat: 45.1000, lon: 15.2000, spread: 1.0, name: 'Croatia' },
    SI: { lat: 46.1512, lon: 14.9955, spread: 0.6, name: 'Slovenia' },
    RS: { lat: 44.0165, lon: 21.0059, spread: 1.2, name: 'Serbia' },
    UA: { lat: 48.3794, lon: 31.1656, spread: 3.0, name: 'Ukraine' },
    RU: { lat: 55.7558, lon: 37.6173, spread: 7.0, name: 'Russia' },
    TR: { lat: 38.9637, lon: 35.2433, spread: 3.0, name: 'Turkey' },
    IL: { lat: 31.0461, lon: 34.8516, spread: 0.8, name: 'Israel' },
    JP: { lat: 36.2048, lon: 138.2529, spread: 2.8, name: 'Japan' },
    AU: { lat: -25.2744, lon: 133.7751, spread: 6.5, name: 'Australia' },
    NZ: { lat: -40.9006, lon: 174.8860, spread: 2.5, name: 'New Zealand' },
    BR: { lat: -14.2350, lon: -51.9253, spread: 6.0, name: 'Brazil' },
    AR: { lat: -38.4161, lon: -63.6167, spread: 4.5, name: 'Argentina' },
    MX: { lat: 23.6345, lon: -102.5528, spread: 4.5, name: 'Mexico' },
    CO: { lat: 4.5709, lon: -74.2973, spread: 2.5, name: 'Colombia' },
    CL: { lat: -35.6751, lon: -71.5430, spread: 3.5, name: 'Chile' },
    ZA: { lat: -30.5595, lon: 22.9375, spread: 3.5, name: 'South Africa' },
    EG: { lat: 26.8206, lon: 30.8025, spread: 2.5, name: 'Egypt' },
    IN: { lat: 20.5937, lon: 78.9629, spread: 5.0, name: 'India' },
    KR: { lat: 35.9078, lon: 127.7669, spread: 1.2, name: 'South Korea' }
  };

  function hashString(str) {
    let hash = 0;
    if (!str) return 0;
    for (let i = 0; i < str.length; i++) {
      hash = ((hash << 5) - hash) + str.charCodeAt(i);
      hash |= 0;
    }
    return Math.abs(hash);
  }

  function getStationCoords(station) {
    let lat = parseFloat(station.geo_lat);
    let lon = parseFloat(station.geo_long);

    // If already has valid GPS coordinates
    if (!isNaN(lat) && !isNaN(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && (lat !== 0 || lon !== 0)) {
      return { lat, lon };
    }

    const cc = (station.countrycode || '').toUpperCase();
    const country = COUNTRY_COORDS[cc];
    if (country) {
      const idStr = station.stationuuid || station.name || 'station';
      const h1 = (hashString(idStr) % 1000) / 1000;
      const h2 = (hashString(idStr + cc) % 1000) / 1000;
      const spread = country.spread || 1.5;

      const angle = h1 * Math.PI * 2;
      const dist = Math.sqrt(h2) * spread;
      const dLat = Math.sin(angle) * dist;
      const cosLat = Math.cos((country.lat * Math.PI) / 180) || 1;
      const dLon = (Math.cos(angle) * dist) / Math.abs(cosLat);

      return {
        lat: Math.max(-85, Math.min(85, country.lat + dLat)),
        lon: Math.max(-180, Math.min(180, country.lon + dLon))
      };
    }

    return null;
  }

  // --- Handpicked Featured High-Uptime Stations with Geolocation ---
  const CURATED_STATIONS = [
    {
      stationuuid: 'curated-oe3',
      name: 'Hitradio Ö3',
      url_resolved: 'https://orf-live.ors-shoutcast.at/oe3-q2a',
      country: 'Austria',
      countrycode: 'AT',
      tags: 'pop, hits, chart, news, austria',
      codec: 'MP3',
      bitrate: 192,
      favicon: 'https://oe3.orf.at/apple-touch-icon.png',
      votes: 3500,
      geo_lat: 48.2082,
      geo_long: 16.3738
    },
    {
      stationuuid: 'curated-fm4',
      name: 'FM4 (ORF)',
      url_resolved: 'https://orf-live.ors-shoutcast.at/fm4-q2a',
      country: 'Austria',
      countrycode: 'AT',
      tags: 'alternative, indie, electronic, hiphop, news',
      codec: 'MP3',
      bitrate: 192,
      favicon: 'https://fm4.orf.at/apple-touch-icon.png',
      votes: 2800,
      geo_lat: 48.2100,
      geo_long: 16.3630
    },
    {
      stationuuid: 'curated-swisspop',
      name: 'Radio Swiss Pop',
      url_resolved: 'https://stream.srg-ssr.ch/m/rsp/mp3_128',
      country: 'Switzerland',
      countrycode: 'CH',
      tags: 'pop, hits, english, soft-pop',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.radioswisspop.ch/dist/images/touch-icon-192x192.png',
      votes: 2400,
      geo_lat: 46.9480,
      geo_long: 7.4474
    },
    {
      stationuuid: 'curated-bayern3',
      name: 'Bayern 3',
      url_resolved: 'https://dispatcher.rndfnk.com/br/br3/live/mp3/mid',
      country: 'Germany',
      countrycode: 'DE',
      tags: 'pop, hits, bavaria, adult contemporary',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.br.de/static/br_de/images/touch-icons/bayern3-touch-icon-180x180.png',
      votes: 2150,
      geo_lat: 48.1351,
      geo_long: 11.5820
    },
    {
      stationuuid: 'curated-swr3',
      name: 'SWR3',
      url_resolved: 'https://liveradio.swr.de/sw282p3/swr3/play.mp3',
      country: 'Germany',
      countrycode: 'DE',
      tags: 'pop, rock, hits, comedy, info',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.swr3.de/static/swr3/assets/icons/apple-touch-icon.png',
      votes: 2200,
      geo_lat: 48.7606,
      geo_long: 8.2400
    },
    {
      stationuuid: 'curated-1live',
      name: '1LIVE',
      url_resolved: 'https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/128/stream.mp3',
      country: 'Germany',
      countrycode: 'DE',
      tags: 'pop, chart, dance, hiphop, youth',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www1.wdr.de/radio/1live/apple-touch-icon.png',
      votes: 2050,
      geo_lat: 50.9375,
      geo_long: 6.9603
    },
    {
      stationuuid: 'curated-fip',
      name: 'FIP (Radio France)',
      url_resolved: 'https://icecast.radiofrance.fr/fip-midfi.mp3',
      country: 'France',
      countrycode: 'FR',
      tags: 'eclectic, jazz, groove, indie, world',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.radiofrance.fr/assets/favicon/fip-apple-touch-icon.png',
      votes: 2600,
      geo_lat: 48.8566,
      geo_long: 2.3522
    },
    {
      stationuuid: 'curated-groovesalad',
      name: 'SomaFM Groove Salad',
      url_resolved: 'https://ice2.somafm.com/groovesalad-128-mp3',
      country: 'United States',
      countrycode: 'US',
      tags: 'ambient, chillout, downtempo, electronic',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://somafm.com/img3/groovesalad120.png',
      votes: 2900,
      geo_lat: 37.7749,
      geo_long: -122.4194
    },
    {
      stationuuid: 'curated-kexp',
      name: 'KEXP 90.3 FM Seattle',
      url_resolved: 'https://kexp-mp3-128.streamguys1.com/kexp128.mp3',
      country: 'United States',
      countrycode: 'US',
      tags: 'indie, alternative, rock, live',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://kexp.org/static/assets/img/kexp-touch-icon-180x180.png',
      votes: 1850,
      geo_lat: 47.6062,
      geo_long: -122.3321
    },
    {
      stationuuid: 'curated-lofi',
      name: 'Lofi Cafe Radio',
      url_resolved: 'https://streams.ilovemusic.de/iloveradio17.mp3',
      country: 'Germany',
      countrycode: 'DE',
      tags: 'lo-fi, chillhop, study, beats',
      codec: 'MP3',
      bitrate: 192,
      favicon: 'https://ilovemusic.de/app/uploads/2021/03/iloveradio-lofi.png',
      votes: 1650,
      geo_lat: 52.5200,
      geo_long: 13.4050
    },
    {
      stationuuid: 'curated-swissclassic',
      name: 'Radio Swiss Classic',
      url_resolved: 'https://stream.srg-ssr.ch/m/rsc_de/mp3_128',
      country: 'Switzerland',
      countrycode: 'CH',
      tags: 'classical, orchestra, symphony, relax',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.radioswissclassic.ch/dist/images/touch-icon-192x192.png',
      votes: 1380,
      geo_lat: 47.5596,
      geo_long: 7.5886
    },
    {
      stationuuid: 'curated-dronezone',
      name: 'SomaFM Drone Zone',
      url_resolved: 'https://ice2.somafm.com/dronezone-128-mp3',
      country: 'United States',
      countrycode: 'US',
      tags: 'ambient, space, drone, sleep',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://somafm.com/img3/dronezone120.png',
      votes: 1420,
      geo_lat: 37.7849,
      geo_long: -122.4094
    },
    {
      stationuuid: 'curated-swissjazz',
      name: 'Radio Swiss Jazz',
      url_resolved: 'https://stream.srg-ssr.ch/m/rsj/mp3_128',
      country: 'Switzerland',
      countrycode: 'CH',
      tags: 'jazz, blues, soul, swing',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.radioswissjazz.ch/dist/images/touch-icon-192x192.png',
      votes: 1540,
      geo_lat: 47.3769,
      geo_long: 8.5417
    },
    {
      stationuuid: 'curated-ibizaglobal',
      name: 'Ibiza Global Radio',
      url_resolved: 'https://inhouse.ibizaglobalradio.com:8024/ibizaglobalradio.mp3',
      country: 'Spain',
      countrycode: 'ES',
      tags: 'electronic, house, deep house, techno',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://ibizaglobalradio.com/favicon.ico',
      votes: 1740,
      geo_lat: 38.9067,
      geo_long: 1.4206
    },
    {
      stationuuid: 'curated-antenneby',
      name: 'Antenne Bayern',
      url_resolved: 'https://stream.antenne.de/antenne/stream/mp3',
      country: 'Germany',
      countrycode: 'DE',
      tags: 'pop, hits, bavaria, adult contemporary',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.antenne.de/apple-touch-icon.png',
      votes: 1690,
      geo_lat: 48.1451,
      geo_long: 11.5720
    },
    {
      stationuuid: 'curated-secretagent',
      name: 'SomaFM Secret Agent',
      url_resolved: 'https://ice2.somafm.com/secretagent-128-mp3',
      country: 'United States',
      countrycode: 'US',
      tags: 'spy, surf, lounge, vintage',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://somafm.com/img3/secretagent120.png',
      votes: 1150,
      geo_lat: 37.7649,
      geo_long: -122.4294
    },
    {
      stationuuid: 'curated-rockantenne',
      name: 'Rock Antenne Heavy Metal',
      url_resolved: 'https://stream.rockantenne.de/heavy-metal/stream/mp3',
      country: 'Germany',
      countrycode: 'DE',
      tags: 'rock, metal, hard rock, classic',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.rockantenne.de/apple-touch-icon.png',
      votes: 1070,
      geo_lat: 48.1551,
      geo_long: 11.5620
    },
    {
      stationuuid: 'curated-bbc1',
      name: 'BBC Radio 1',
      url_resolved: 'https://stream.live.vc.bbcmedia.co.uk/bbc_radio_one',
      country: 'United Kingdom',
      countrycode: 'GB',
      tags: 'pop, dance, top40, uk',
      codec: 'MP3',
      bitrate: 128,
      favicon: 'https://www.bbc.co.uk/favicon.ico',
      votes: 2300,
      geo_lat: 51.5200,
      geo_long: -0.1400
    },
    {
      stationuuid: 'curated-bbcworld',
      name: 'BBC World Service',
      url_resolved: 'https://stream.live.vc.bbcmedia.co.uk/bbc_world_service',
      country: 'United Kingdom',
      countrycode: 'GB',
      tags: 'news, talk, information, world',
      codec: 'MP3',
      bitrate: 96,
      favicon: 'https://www.bbc.co.uk/favicon.ico',
      votes: 2700,
      geo_lat: 51.5074,
      geo_long: -0.1278
    }
  ];

  // --- App State ---
  let currentView = 'grid'; // 'grid' | 'globe'
  let stationsList = [];
  let geoStationsList = [];
  let currentStationIndex = -1;
  let currentStation = null;
  let isPlaying = false;
  let searchDebounceTimer = null;
  let activeGenre = 'featured';
  let liveDurationTimer = null;
  let playbackSeconds = 0;
  let sleepTimerInterval = null;
  let sleepTimeRemaining = 0;
  let favorites = [];

  // --- DOM Elements ---
  const audio = document.getElementById('audio-player');
  const stationsGrid = document.getElementById('stations-grid');
  const searchInput = document.getElementById('search-input');
  const clearSearchBtn = document.getElementById('clear-search-btn');
  const genrePills = document.getElementById('genre-pills');
  const countrySelect = document.getElementById('country-select');
  const sortSelect = document.getElementById('sort-select');
  const sectionTitle = document.getElementById('section-title');
  const resultsCount = document.getElementById('results-count');
  const loadingSpinner = document.getElementById('loading-spinner');
  const emptyState = document.getElementById('empty-state');
  const resetFilterBtn = document.getElementById('reset-filter-btn');

  // View Containers & Switcher
  const gridViewContainer = document.getElementById('grid-view-container');
  const globeViewContainer = document.getElementById('globe-view-container');
  const viewGridBtn = document.getElementById('view-grid-btn');
  const viewGlobeBtn = document.getElementById('view-globe-btn');

  // Player DOM
  const playPauseBtn = document.getElementById('play-pause-btn');
  const playIcon = document.getElementById('play-icon');
  const pauseIcon = document.getElementById('pause-icon');
  const bufferingSpinner = document.getElementById('buffering-spinner');
  const prevStationBtn = document.getElementById('prev-station-btn');
  const nextStationBtn = document.getElementById('next-station-btn');
  const currentStationName = document.getElementById('current-station-name');
  const currentStationTags = document.getElementById('current-station-tags');
  const currentStationImg = document.getElementById('current-station-img');
  const currentStationAvatar = document.getElementById('current-station-avatar');
  const currentStationCodec = document.getElementById('current-station-codec');
  const playerStatusBadge = document.getElementById('player-status-badge');
  const playbackTimer = document.getElementById('playback-timer');
  const liveDot = document.querySelector('.live-dot');
  const audioBitrateLabel = document.getElementById('audio-bitrate-label');
  const currentFavBtn = document.getElementById('current-fav-btn');

  // Volume
  const volumeSlider = document.getElementById('volume-slider');
  const volumePct = document.getElementById('volume-pct');
  const muteBtn = document.getElementById('mute-btn');
  const volumeIconHigh = document.getElementById('volume-icon-high');
  const volumeIconMuted = document.getElementById('volume-icon-muted');
  let previousVolume = 0.8;

  // Sleep Timer
  const sleepTimerBtn = document.getElementById('sleep-timer-btn');
  const sleepTimerBadge = document.getElementById('sleep-timer-badge');
  const sleepTimerMenu = document.getElementById('sleep-timer-menu');

  // Custom Stream Modal
  const openCustomModalBtn = document.getElementById('open-custom-modal-btn');
  const closeCustomModalBtn = document.getElementById('close-custom-modal-btn');
  const cancelCustomBtn = document.getElementById('cancel-custom-btn');
  const customModal = document.getElementById('custom-stream-modal');
  const customForm = document.getElementById('custom-stream-form');

  // Toast
  const toast = document.getElementById('toast');
  const toastMsg = document.getElementById('toast-message');

  // Visualizer Canvas
  const canvas = document.getElementById('visualizer-canvas');
  const ctx = canvas.getContext('2d');
  let visualizerAnimFrame = null;
  let visualizerMode = 0; // 0 = neon waves, 1 = frequency bars

  // --- Initializer ---
  function init() {
    loadFavorites();
    initAudioEvents();
    initVisualizer();
    setupEventListeners();

    // Default volume
    const savedVol = localStorage.getItem('pulse_volume');
    if (savedVol !== null) {
      audio.volume = parseFloat(savedVol);
      volumeSlider.value = savedVol;
      volumePct.textContent = `${Math.round(savedVol * 100)}%`;
    } else {
      audio.volume = 0.8;
    }

    // Load featured stations on launch
    loadCuratedStations();

    // Preload large catalog of geo stations in background
    loadGeoStations();
  }

  // --- Storage & Favorites ---
  function loadFavorites() {
    try {
      const stored = localStorage.getItem('pulse_favorites');
      favorites = stored ? JSON.parse(stored) : [];
    } catch (e) {
      favorites = [];
    }
    updateFavoritesBadge();
  }

  function saveFavorites() {
    try {
      localStorage.setItem('pulse_favorites', JSON.stringify(favorites));
    } catch (e) {
      console.error('Failed to save favorites to localStorage', e);
    }
    updateFavoritesBadge();
  }

  function updateFavoritesBadge() {
    const favCountEl = document.getElementById('fav-count');
    if (favCountEl) favCountEl.textContent = favorites.length;
  }

  function isFavorite(station) {
    if (!station) return false;
    return favorites.some(f => (f.stationuuid && f.stationuuid === station.stationuuid) || (f.url_resolved && f.url_resolved === station.url_resolved));
  }

  function toggleFavorite(station) {
    if (!station) return;
    const index = favorites.findIndex(f => (f.stationuuid && f.stationuuid === station.stationuuid) || (f.url_resolved && f.url_resolved === station.url_resolved));
    if (index >= 0) {
      favorites.splice(index, 1);
      showToast(`Removed "${station.name}" from favorites`);
    } else {
      favorites.unshift(station);
      showToast(`Added "${station.name}" to favorites ❤️`);
    }
    saveFavorites();
    updatePlayerFavButton();
    renderCards(stationsList);
  }

  function updatePlayerFavButton() {
    if (!currentStation) {
      currentFavBtn.classList.remove('is-fav');
      return;
    }
    if (isFavorite(currentStation)) {
      currentFavBtn.classList.add('is-fav');
    } else {
      currentFavBtn.classList.remove('is-fav');
    }
  }

  // --- Toast Notifications ---
  let toastTimer = null;
  function showToast(message) {
    toastMsg.textContent = message;
    toast.classList.remove('hidden');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toast.classList.add('hidden');
    }, 2800);
  }

  // --- Helper Functions ---
  function getCountryFlag(countryCode) {
    if (!countryCode || countryCode.length !== 2) return '🌐';
    const code = countryCode.toUpperCase();
    return String.fromCodePoint(...[...code].map(c => 0x1F1E6 + c.charCodeAt(0) - 65));
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g, tag => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }[tag] || tag));
  }

  function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  // --- API Fetching ---
  async function fetchRadioStations(endpoint, params = {}) {
    loadingSpinner.classList.remove('hidden');
    stationsGrid.innerHTML = '';
    emptyState.classList.add('hidden');

    const url = new URL(`${getApiBase()}${endpoint}`);
    Object.keys(params).forEach(key => {
      if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
        url.searchParams.append(key, params[key]);
      }
    });

    try {
      const response = await fetch(url.toString(), {
        headers: { 'Accept': 'application/json' }
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      loadingSpinner.classList.add('hidden');

      return data.filter(s => s && (s.url_resolved || s.url));
    } catch (err) {
      console.warn('API error, attempting fallback server:', err);
      rotateServer();
      try {
        const fallbackUrl = new URL(`${getApiBase()}${endpoint}`);
        Object.keys(params).forEach(key => {
          if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
            fallbackUrl.searchParams.append(key, params[key]);
          }
        });
        const res = await fetch(fallbackUrl.toString());
        const data = await res.json();
        loadingSpinner.classList.add('hidden');
        return data.filter(s => s && (s.url_resolved || s.url));
      } catch (fallbackErr) {
        loadingSpinner.classList.add('hidden');
        console.error('All radio API mirrors failed:', fallbackErr);
        showToast('Network issue connecting to Radio Browser API. Using curated list.');
        return CURATED_STATIONS;
      }
    }
  }

  function loadCuratedStations() {
    stationsList = CURATED_STATIONS;
    sectionTitle.textContent = 'Featured Stations';
    resultsCount.textContent = `${stationsList.length} handpicked streams`;
    renderCards(stationsList);
  }

  async function loadTopStations() {
    sectionTitle.textContent = 'Trending Global Stations';
    resultsCount.textContent = 'Loading top streams...';
    const stations = await fetchRadioStations('/json/stations/topclick/60');
    stationsList = stations;
    resultsCount.textContent = `${stations.length} stations found`;
    renderCards(stationsList);
  }

  async function loadGenreStations(genre) {
    sectionTitle.textContent = `${genre.toUpperCase()} Stations`;
    resultsCount.textContent = 'Searching stations...';
    const sort = sortSelect.value || 'clickcount';
    const country = countrySelect.value || '';

    const params = {
      tag: genre,
      limit: 60,
      order: sort,
      reverse: 'true',
      hidebroken: 'true'
    };
    if (country) params.countrycode = country;

    const stations = await fetchRadioStations('/json/stations/search', params);
    stationsList = stations;
    resultsCount.textContent = `${stations.length} stations found`;
    renderCards(stationsList);
  }

  async function performSearch(query) {
    if (!query || query.trim() === '') {
      if (activeGenre === 'featured') {
        loadCuratedStations();
      } else if (activeGenre === 'topclick') {
        loadTopStations();
      } else if (activeGenre === 'favorites') {
        renderFavorites();
      } else {
        loadGenreStations(activeGenre);
      }
      return;
    }

    sectionTitle.textContent = `Search results for "${query}"`;
    resultsCount.textContent = 'Searching...';

    const sort = sortSelect.value || 'clickcount';
    const country = countrySelect.value || '';

    const params = {
      name: query.trim(),
      limit: 60,
      order: sort,
      reverse: 'true',
      hidebroken: 'true'
    };
    if (country) params.countrycode = country;

    const stations = await fetchRadioStations('/json/stations/search', params);
    stationsList = stations;
    resultsCount.textContent = `${stations.length} stations found`;
    renderCards(stationsList);
  }

  function renderFavorites() {
    stationsList = favorites;
    sectionTitle.textContent = 'Your Saved Favorites';
    resultsCount.textContent = `${favorites.length} stations`;
    if (favorites.length === 0) {
      stationsGrid.innerHTML = '';
      emptyState.classList.remove('hidden');
      document.getElementById('empty-title').textContent = 'No favorites yet';
      document.getElementById('empty-desc').textContent = 'Click the heart icon on any station card or in the player bar to save your favorites!';
    } else {
      emptyState.classList.add('hidden');
      renderCards(stationsList);
    }
  }

  // --- Multi-Source Geo Stations Loader (1,500+ Stations Coverage) ---
  async function loadGeoStations() {
    try {
      const statsBadge = document.getElementById('globe-stats-badge');
      if (statsBadge) statsBadge.textContent = 'Indexing 1,500+ global streams...';

      // Fetch topclick + topvote + explicit geo stations in parallel for huge coverage
      const endpoints = [
        `${getApiBase()}/json/stations/topclick/1000`,
        `${getApiBase()}/json/stations/topvote/1000`,
        `${getApiBase()}/json/stations/search?has_geo_info=true&limit=600&order=clickcount&reverse=true`
      ];

      const responses = await Promise.allSettled(
        endpoints.map(url => fetch(url).then(r => r.ok ? r.json() : []))
      );

      const stationMap = new Map();

      // Add all curated stations first
      CURATED_STATIONS.forEach(s => {
        stationMap.set(s.stationuuid, s);
      });

      // Merge fetched results
      responses.forEach(res => {
        if (res.status === 'fulfilled' && Array.isArray(res.value)) {
          res.value.forEach(s => {
            if (s && s.stationuuid && (s.url_resolved || s.url) && !stationMap.has(s.stationuuid)) {
              stationMap.set(s.stationuuid, s);
            }
          });
        }
      });

      // Process coordinates for all stations
      const validGeoStations = [];
      for (const station of stationMap.values()) {
        const coords = getStationCoords(station);
        if (coords) {
          station.geo_lat = coords.lat;
          station.geo_long = coords.lon;
          validGeoStations.push(station);
        }
      }

      geoStationsList = validGeoStations;
      console.log(`Loaded ${geoStationsList.length} geo-positioned radio stations worldwide.`);
    } catch (e) {
      console.warn('Failed to load full geo catalog, using curated list:', e);
      geoStationsList = CURATED_STATIONS;
    }

    if (GlobeRadar.isInitialized) {
      GlobeRadar.updateMarkers(geoStationsList);
    }
  }

  // --- Rendering UI Cards ---
  function renderCards(stations) {
    stationsGrid.innerHTML = '';

    if (!stations || stations.length === 0) {
      emptyState.classList.remove('hidden');
      return;
    }
    emptyState.classList.add('hidden');

    stations.forEach((station, index) => {
      const card = document.createElement('div');
      card.className = 'station-card';
      if (currentStation && (currentStation.stationuuid === station.stationuuid || currentStation.url_resolved === station.url_resolved)) {
        card.classList.add('is-active');
      }

      const flag = getCountryFlag(station.countrycode);
      const isFav = isFavorite(station);

      // Clean tags
      const tagsArray = station.tags ? station.tags.split(',').map(t => t.trim()).filter(Boolean).slice(0, 3) : [];
      const tagsHtml = tagsArray.map(t => `<span class="station-tag">#${escapeHtml(t)}</span>`).join('');

      const bitrateStr = station.bitrate && station.bitrate > 0 ? `${station.bitrate} kbps` : (station.codec || 'MP3');
      const isCurrentPlaying = card.classList.contains('is-active') && isPlaying;

      card.innerHTML = `
        <div class="station-card-top">
          <div class="station-art-box">
            ${station.favicon ? `<img src="${escapeHtml(station.favicon)}" alt="" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">` : ''}
            <div class="station-art-placeholder" style="${station.favicon ? 'display:none;' : ''}">📻</div>
            ${isCurrentPlaying ? `
              <div class="card-equalizer">
                <span></span><span></span><span></span><span></span>
              </div>
            ` : ''}
          </div>
          <div class="station-info">
            <h3 class="station-name" title="${escapeHtml(station.name)}">${escapeHtml(station.name)}</h3>
            <div class="station-sub">
              <span>${flag}</span>
              <span>${escapeHtml(station.country || 'Global')}</span>
            </div>
          </div>
          <button class="card-fav-btn ${isFav ? 'is-fav' : ''}" title="Favorite" aria-label="Favorite">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
            </svg>
          </button>
        </div>

        ${tagsArray.length > 0 ? `<div class="station-tags-row">${tagsHtml}</div>` : ''}

        <div class="station-card-bottom">
          <span class="station-meta-badge">${escapeHtml(bitrateStr)}</span>
          <button class="btn-card-play">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
              <polygon points="6 3 20 12 6 21 6 3"></polygon>
            </svg>
            <span>${card.classList.contains('is-active') && isPlaying ? 'Playing' : 'Listen'}</span>
          </button>
        </div>
      `;

      card.addEventListener('click', (e) => {
        if (e.target.closest('.card-fav-btn')) {
          e.stopPropagation();
          toggleFavorite(station);
          return;
        }
        playStation(station, index);
      });

      stationsGrid.appendChild(card);
    });
  }

  // --- Audio Playback Engine ---
  function playStation(station, index = -1) {
    if (!station) return;

    if (currentStation && (currentStation.stationuuid === station.stationuuid || currentStation.url_resolved === station.url_resolved)) {
      togglePlayPause();
      return;
    }

    currentStation = station;
    currentStationIndex = index >= 0 ? index : stationsList.findIndex(s => s.stationuuid === station.stationuuid || s.url_resolved === station.url_resolved);

    const streamUrl = station.url_resolved || station.url;
    if (!streamUrl) {
      showToast('Error: Invalid stream URL');
      return;
    }

    // Update bottom player UI
    currentStationName.textContent = station.name || 'Unknown Station';
    const flag = getCountryFlag(station.countrycode);
    currentStationTags.textContent = `${flag} ${station.country || 'Global'} • ${station.tags ? station.tags.split(',').slice(0, 2).join(', ') : 'Live Stream'}`;

    if (station.codec) {
      currentStationCodec.textContent = station.codec.toUpperCase();
      currentStationCodec.classList.remove('hidden');
    } else {
      currentStationCodec.classList.add('hidden');
    }

    if (station.favicon) {
      currentStationImg.src = station.favicon;
      currentStationImg.classList.remove('hidden');
      currentStationAvatar.classList.add('hidden');
      currentStationImg.onerror = () => {
        currentStationImg.classList.add('hidden');
        currentStationAvatar.classList.remove('hidden');
      };
    } else {
      currentStationImg.classList.add('hidden');
      currentStationAvatar.classList.remove('hidden');
    }

    setPlayerStatus('loading', 'CONNECTING');
    audioBitrateLabel.textContent = station.bitrate && station.bitrate > 0 ? `${station.bitrate} kbps ${station.codec || ''}` : 'Connecting...';
    updatePlayerFavButton();

    // Start playback
    audio.pause();
    audio.src = streamUrl;
    audio.load();

    const playPromise = audio.play();
    if (playPromise !== undefined) {
      playPromise
        .then(() => {
          isPlaying = true;
          updatePlaybackUI();
          startLiveTimer();
        })
        .catch(error => {
          console.error('Audio play error:', error);
          setPlayerStatus('error', 'STREAM ERROR');
          bufferingSpinner.classList.add('hidden');
          playIcon.classList.remove('hidden');
          pauseIcon.classList.add('hidden');
          showToast(`Unable to play "${station.name}". Stream might be offline or blocked.`);
        });
    }

    // Update Globe Active Dot Beacon
    if (GlobeRadar.isInitialized) {
      GlobeRadar.setActiveStation(station);
    }

    renderCards(stationsList);
  }

  function togglePlayPause() {
    if (!currentStation) {
      if (stationsList.length > 0) {
        playStation(stationsList[0], 0);
      } else if (geoStationsList.length > 0) {
        playStation(geoStationsList[0], 0);
      }
      return;
    }

    if (audio.paused) {
      const playPromise = audio.play();
      if (playPromise !== undefined) {
        playPromise
          .then(() => {
            isPlaying = true;
            updatePlaybackUI();
            startLiveTimer();
          })
          .catch(e => {
            console.error('Playback error:', e);
            showToast('Stream interrupted. Reconnecting...');
          });
      }
    } else {
      audio.pause();
      isPlaying = false;
      updatePlaybackUI();
      stopLiveTimer();
      setPlayerStatus('ready', 'PAUSED');
    }

    renderCards(stationsList);
  }

  function playNextStation() {
    const list = currentView === 'globe' && geoStationsList.length > 0 ? geoStationsList : stationsList;
    if (list.length === 0) return;
    let nextIdx = (currentStationIndex + 1) % list.length;
    playStation(list[nextIdx], nextIdx);
  }

  function playPrevStation() {
    const list = currentView === 'globe' && geoStationsList.length > 0 ? geoStationsList : stationsList;
    if (list.length === 0) return;
    let prevIdx = (currentStationIndex - 1 + list.length) % list.length;
    playStation(list[prevIdx], prevIdx);
  }

  function updatePlaybackUI() {
    if (isPlaying) {
      playIcon.classList.add('hidden');
      pauseIcon.classList.remove('hidden');
      bufferingSpinner.classList.add('hidden');
      setPlayerStatus('playing', 'ON AIR');
      liveDot.classList.add('active');
    } else {
      playIcon.classList.remove('hidden');
      pauseIcon.classList.add('hidden');
      bufferingSpinner.classList.add('hidden');
      liveDot.classList.remove('active');
    }
  }

  function setPlayerStatus(type, label) {
    playerStatusBadge.className = `stream-status status-${type}`;
    playerStatusBadge.textContent = label;
  }

  function startLiveTimer() {
    stopLiveTimer();
    playbackSeconds = 0;
    playbackTimer.textContent = 'LIVE 00:00';
    liveDurationTimer = setInterval(() => {
      playbackSeconds++;
      playbackTimer.textContent = `LIVE ${formatTime(playbackSeconds)}`;
    }, 1000);
  }

  function stopLiveTimer() {
    if (liveDurationTimer) {
      clearInterval(liveDurationTimer);
      liveDurationTimer = null;
    }
  }

  function initAudioEvents() {
    audio.addEventListener('waiting', () => {
      bufferingSpinner.classList.remove('hidden');
      playIcon.classList.add('hidden');
      pauseIcon.classList.add('hidden');
      setPlayerStatus('loading', 'BUFFERING');
    });

    audio.addEventListener('playing', () => {
      isPlaying = true;
      bufferingSpinner.classList.add('hidden');
      playIcon.classList.add('hidden');
      pauseIcon.classList.remove('hidden');
      setPlayerStatus('playing', 'ON AIR');
      liveDot.classList.add('active');
      if (currentStation) {
        audioBitrateLabel.textContent = currentStation.bitrate && currentStation.bitrate > 0 
          ? `${currentStation.bitrate} kbps ${currentStation.codec || ''}` 
          : 'Live Stream';
      }
    });

    audio.addEventListener('pause', () => {
      isPlaying = false;
      bufferingSpinner.classList.remove('hidden');
      playIcon.classList.remove('hidden');
      pauseIcon.classList.add('hidden');
      liveDot.classList.remove('active');
      setPlayerStatus('ready', 'PAUSED');
    });

    audio.addEventListener('error', (e) => {
      console.error('Audio element error event:', e);
      isPlaying = false;
      bufferingSpinner.classList.add('hidden');
      playIcon.classList.remove('hidden');
      pauseIcon.classList.add('hidden');
      liveDot.classList.remove('active');
      setPlayerStatus('error', 'OFFLINE');
      playbackTimer.textContent = 'OFFLINE';
      stopLiveTimer();
    });
  }

  // --- Sleep Timer ---
  function startSleepTimer(minutes) {
    if (sleepTimerInterval) clearInterval(sleepTimerInterval);

    if (minutes <= 0) {
      sleepTimeRemaining = 0;
      sleepTimerBadge.classList.add('hidden');
      showToast('Sleep timer turned off');
      return;
    }

    sleepTimeRemaining = minutes * 60;
    sleepTimerBadge.textContent = `${minutes}m`;
    sleepTimerBadge.classList.remove('hidden');
    showToast(`Sleep timer set to ${minutes} minutes 🌙`);

    sleepTimerInterval = setInterval(() => {
      sleepTimeRemaining--;
      if (sleepTimeRemaining <= 0) {
        clearInterval(sleepTimerInterval);
        sleepTimerInterval = null;
        sleepTimerBadge.classList.add('hidden');
        audio.pause();
        isPlaying = false;
        updatePlaybackUI();
        showToast('Sleep timer expired. Audio stopped.');
      } else {
        const minsLeft = Math.ceil(sleepTimeRemaining / 60);
        sleepTimerBadge.textContent = `${minsLeft}m`;
      }
    }, 1000);
  }

  // --- Visualizer Engine ---
  function initVisualizer() {
    function resize() {
      canvas.width = canvas.parentElement.clientWidth;
      canvas.height = canvas.parentElement.clientHeight;
    }
    resize();
    window.addEventListener('resize', resize);

    let phase = 0;
    const barCount = 48;
    const barHeights = new Float32Array(barCount);

    function draw() {
      visualizerAnimFrame = requestAnimationFrame(draw);
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      if (!isPlaying) {
        ctx.strokeStyle = 'rgba(79, 172, 254, 0.08)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, canvas.height / 2);
        ctx.lineTo(canvas.width, canvas.height / 2);
        ctx.stroke();
        return;
      }

      phase += 0.04;
      const volMultiplier = audio.volume;

      if (visualizerMode === 0) {
        const grad = ctx.createLinearGradient(0, 0, canvas.width, 0);
        grad.addColorStop(0, 'rgba(0, 242, 254, 0.45)');
        grad.addColorStop(0.5, 'rgba(127, 0, 255, 0.45)');
        grad.addColorStop(1, 'rgba(225, 0, 255, 0.45)');

        ctx.strokeStyle = grad;
        ctx.lineWidth = 2.5;
        ctx.beginPath();

        const sliceWidth = canvas.width / 100;
        let x = 0;

        for (let i = 0; i <= 100; i++) {
          const sin1 = Math.sin(phase + i * 0.12);
          const sin2 = Math.cos(phase * 1.5 + i * 0.08);
          const amplitude = (sin1 * 0.6 + sin2 * 0.4) * (canvas.height * 0.35) * volMultiplier;
          const y = (canvas.height / 2) + amplitude;

          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);

          x += sliceWidth;
        }
        ctx.stroke();
      } else {
        const barWidth = canvas.width / barCount;
        for (let i = 0; i < barCount; i++) {
          const targetHeight = Math.abs(Math.sin(phase + i * 0.3) * Math.cos(phase * 0.5 + i * 0.2)) * (canvas.height * 0.8) * volMultiplier;
          barHeights[i] += (targetHeight - barHeights[i]) * 0.15;

          const h = Math.max(2, barHeights[i]);
          const x = i * barWidth;
          const y = canvas.height - h;

          const barGrad = ctx.createLinearGradient(0, canvas.height, 0, y);
          barGrad.addColorStop(0, 'rgba(0, 242, 254, 0.2)');
          barGrad.addColorStop(1, 'rgba(127, 0, 255, 0.6)');

          ctx.fillStyle = barGrad;
          ctx.fillRect(x + 2, y, barWidth - 4, h);
        }
      }
    }

    draw();
  }

  // ==========================================================================
  // 3D Interactive World Globe Engine (Three.js)
  // Calibrated for smooth, weighted navigation & thousands of crisp pinpoint dots
  // ==========================================================================
  const GlobeRadar = {
    isInitialized: false,
    scene: null,
    camera: null,
    renderer: null,
    globeMesh: null,
    pointsMesh: null,
    beaconMesh: null,
    hoverMarkerMesh: null,
    raycaster: null,
    mouse: null,
    mountEl: null,
    animFrameId: null,

    // Rotation & Orbit physics (Calibrated for gentle, precise control)
    isDragging: false,
    prevMousePos: { x: 0, y: 0 },
    currentRot: { x: 0.25, y: 0 },
    targetRot: { x: 0.25, y: 0 },
    cameraDistance: 245,
    targetDistance: 245,
    autoRotate: false, // Explicitly false: No automatic movement
    hoveredStation: null,
    activeStationData: null,
    renderedStations: [],

    init: function () {
      if (this.isInitialized) return;
      this.mountEl = document.getElementById('globe-canvas-mount');
      if (!this.mountEl || typeof THREE === 'undefined') {
        console.warn('Three.js or mount element missing');
        return;
      }

      const width = this.mountEl.clientWidth || 800;
      const height = this.mountEl.clientHeight || 700;

      // 1. Scene, Camera, Renderer
      this.scene = new THREE.Scene();
      this.camera = new THREE.PerspectiveCamera(45, width / height, 0.2, 2000);
      this.camera.position.z = this.cameraDistance;

      this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      this.renderer.setSize(width, height);
      this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      this.mountEl.appendChild(this.renderer.domElement);

      this.raycaster = new THREE.Raycaster();
      this.raycaster.params.Points = { threshold: 1.8 }; // Generous comfortable hover picking
      this.mouse = new THREE.Vector2();

      // 2. Lighting
      const ambientLight = new THREE.AmbientLight(0xffffff, 0.85);
      this.scene.add(ambientLight);

      const dirLight = new THREE.DirectionalLight(0x00f2fe, 0.45);
      dirLight.position.set(200, 150, 200);
      this.scene.add(dirLight);

      // 3. Earth Globe Sphere
      const earthRadius = 100;
      const earthGeo = new THREE.SphereGeometry(earthRadius, 64, 64);

      const textureLoader = new THREE.TextureLoader();
      const earthTexture = textureLoader.load('earth.jpg');

      const earthMat = new THREE.MeshPhongMaterial({
        map: earthTexture,
        color: 0x88aacc,
        specular: 0x112233,
        shininess: 12
      });

      this.globeMesh = new THREE.Mesh(earthGeo, earthMat);
      this.scene.add(this.globeMesh);

      // 4. Subtle Atmosphere Outer Glow Shell
      const atmosGeo = new THREE.SphereGeometry(earthRadius * 1.025, 48, 48);
      const atmosMat = new THREE.MeshBasicMaterial({
        color: 0x00f2fe,
        transparent: true,
        opacity: 0.12,
        side: THREE.BackSide
      });
      const atmosMesh = new THREE.Mesh(atmosGeo, atmosMat);
      this.scene.add(atmosMesh);

      // 5. Grid Graticule Lines
      const gridGeo = new THREE.SphereGeometry(earthRadius * 1.006, 24, 12);
      const gridMat = new THREE.MeshBasicMaterial({
        color: 0x00f2fe,
        wireframe: true,
        transparent: true,
        opacity: 0.08
      });
      const gridMesh = new THREE.Mesh(gridGeo, gridMat);
      this.scene.add(gridMesh);

      // 6. Background Starfield
      this.createStarfield();

      // 7. Active Beacon Ring Mesh
      this.createBeaconRing(earthRadius);

      // 8. Hover Highlight Ring Mesh
      this.createHoverMarker();

      // 9. Input and Interaction listeners
      this.setupGlobeEvents();

      this.isInitialized = true;
      this.startAnimation();

      // Render initial markers
      if (geoStationsList.length > 0) {
        this.updateMarkers(geoStationsList);
      }
    },

    createStarfield: function () {
      const starGeo = new THREE.BufferGeometry();
      const starCount = 600;
      const starPos = new Float32Array(starCount * 3);

      for (let i = 0; i < starCount * 3; i += 3) {
        starPos[i] = (Math.random() - 0.5) * 1400;
        starPos[i + 1] = (Math.random() - 0.5) * 1400;
        starPos[i + 2] = (Math.random() - 0.5) * 1400;
      }

      starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
      const starMat = new THREE.PointsMaterial({
        color: 0xffffff,
        size: 1.5,
        transparent: true,
        opacity: 0.45
      });
      const stars = new THREE.Points(starGeo, starMat);
      this.scene.add(stars);
    },

    createDotTexture: function () {
      const canvas = document.createElement('canvas');
      canvas.width = 32;
      canvas.height = 32;
      const c = canvas.getContext('2d');

      const grad = c.createRadialGradient(16, 16, 0, 16, 16, 16);
      grad.addColorStop(0, 'rgba(255, 255, 255, 1.0)');
      grad.addColorStop(0.3, 'rgba(0, 242, 254, 0.9)');
      grad.addColorStop(0.7, 'rgba(0, 242, 254, 0.3)');
      grad.addColorStop(1, 'rgba(0, 242, 254, 0)');

      c.fillStyle = grad;
      c.beginPath();
      c.arc(16, 16, 15, 0, Math.PI * 2);
      c.fill();

      return new THREE.CanvasTexture(canvas);
    },

    createBeaconRing: function (radius) {
      const ringGeo = new THREE.RingGeometry(0.35, 1.2, 24);
      const ringMat = new THREE.MeshBasicMaterial({
        color: 0x00f2fe,
        side: THREE.DoubleSide,
        transparent: true,
        opacity: 0.85
      });
      this.beaconMesh = new THREE.Mesh(ringGeo, ringMat);
      this.beaconMesh.visible = false;
      this.scene.add(this.beaconMesh);
    },

    createHoverMarker: function () {
      const ringGeo = new THREE.RingGeometry(0.5, 0.85, 16);
      const ringMat = new THREE.MeshBasicMaterial({
        color: 0xffffff,
        side: THREE.DoubleSide,
        transparent: true,
        opacity: 0.9
      });
      this.hoverMarkerMesh = new THREE.Mesh(ringGeo, ringMat);
      this.hoverMarkerMesh.visible = false;
      this.scene.add(this.hoverMarkerMesh);
    },

    latLonToVec3: function (lat, lon, radius = 100.45) {
      const phi = (90 - lat) * (Math.PI / 180);
      const theta = (lon + 180) * (Math.PI / 180);
      const x = -(radius * Math.sin(phi) * Math.cos(theta));
      const z = (radius * Math.sin(phi) * Math.sin(theta));
      const y = (radius * Math.cos(phi));
      return new THREE.Vector3(x, y, z);
    },

    updateMarkers: function (stations) {
      if (!this.isInitialized) return;

      if (this.pointsMesh) {
        this.scene.remove(this.pointsMesh);
        if (this.pointsMesh.geometry) this.pointsMesh.geometry.dispose();
        if (this.pointsMesh.material) this.pointsMesh.material.dispose();
        this.pointsMesh = null;
      }

      this.renderedStations = [];
      const positions = [];
      const colors = [];

      const colorCyan = new THREE.Color(0x00f2fe);
      const colorFav = new THREE.Color(0x10b981);

      stations.forEach((station) => {
        const lat = parseFloat(station.geo_lat);
        const lon = parseFloat(station.geo_long);

        if (isNaN(lat) || isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
          return;
        }

        const pos = this.latLonToVec3(lat, lon, 100.45);
        positions.push(pos.x, pos.y, pos.z);

        const col = isFavorite(station) ? colorFav : colorCyan;
        colors.push(col.r, col.g, col.b);

        this.renderedStations.push(station);
      });

      const bufferGeo = new THREE.BufferGeometry();
      bufferGeo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
      bufferGeo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));

      // Ultra-small, crisp pinpoint dots
      const dotTexture = this.createDotTexture();
      const pointsMat = new THREE.PointsMaterial({
        size: 1.6, // Refined tiny pinpoint size
        map: dotTexture,
        transparent: true,
        opacity: 0.95,
        vertexColors: true,
        sizeAttenuation: true
      });

      this.pointsMesh = new THREE.Points(bufferGeo, pointsMat);
      this.scene.add(this.pointsMesh);

      const statsBadge = document.getElementById('globe-stats-badge');
      if (statsBadge) {
        statsBadge.textContent = `${this.renderedStations.length} Live Stations on Radar`;
      }
    },

    setActiveStation: function (station) {
      this.activeStationData = station;
      if (!station || station.geo_lat == null || station.geo_long == null) {
        if (this.beaconMesh) this.beaconMesh.visible = false;
        return;
      }

      const lat = parseFloat(station.geo_lat);
      const lon = parseFloat(station.geo_long);
      if (isNaN(lat) || isNaN(lon)) return;

      const pos = this.latLonToVec3(lat, lon, 100.7);
      if (this.beaconMesh) {
        this.beaconMesh.position.copy(pos);
        this.beaconMesh.lookAt(new THREE.Vector3(0, 0, 0));
        this.beaconMesh.visible = true;
      }

      this.flyToLocation(lat, lon);
      this.showStationCard(station);
    },

    flyToLocation: function (lat, lon) {
      const targetPhi = (lat * Math.PI) / 180;
      const targetTheta = -(lon * Math.PI) / 180 - Math.PI / 2;

      this.targetRot.x = Math.max(-1.25, Math.min(1.25, targetPhi));
      this.targetRot.y = targetTheta;
    },

    showStationCard: function (station) {
      const card = document.getElementById('globe-station-card');
      if (!card) return;

      const flag = getCountryFlag(station.countrycode);
      const flagEl = document.getElementById('gcard-flag');
      if (flagEl) flagEl.textContent = flag;

      const metaEl = document.getElementById('gcard-meta');
      if (metaEl) {
        metaEl.textContent = `${station.country || 'Global'}${station.state ? ' • ' + station.state : ''}`;
      }

      const nameEl = document.getElementById('gcard-name');
      if (nameEl) nameEl.textContent = station.name;

      const art = document.getElementById('gcard-art');
      if (station.favicon) {
        art.innerHTML = `<img src="${escapeHtml(station.favicon)}" alt="" onerror="this.parentElement.textContent='📻'">`;
      } else {
        art.textContent = '📻';
      }

      const tagsEl = document.getElementById('gcard-tags');
      const tags = station.tags ? station.tags.split(',').slice(0, 2).map(t => `<span class="station-tag">#${escapeHtml(t.trim())}</span>`).join('') : '';
      tagsEl.innerHTML = tags;

      const playBtn = document.getElementById('gcard-play-btn');
      playBtn.onclick = () => {
        playStation(station);
      };

      const favBtn = document.getElementById('gcard-fav-btn');
      favBtn.className = `btn btn-secondary btn-sm ${isFavorite(station) ? 'is-fav' : ''}`;
      favBtn.onclick = () => {
        toggleFavorite(station);
        favBtn.className = `btn btn-secondary btn-sm ${isFavorite(station) ? 'is-fav' : ''}`;
      };

      // Radio Garden Multi-Station City Explorer (Group stations in this city/region)
      const nearbySection = document.getElementById('gcard-nearby-section');
      const nearbyList = document.getElementById('gcard-nearby-list');
      const nearbyCount = document.getElementById('nearby-count');

      if (nearbySection && nearbyList && station.geo_lat != null) {
        const lat = parseFloat(station.geo_lat);
        const lon = parseFloat(station.geo_long);

        const nearby = this.renderedStations.filter(s => {
          if (s.stationuuid === station.stationuuid) return false;
          const sLat = parseFloat(s.geo_lat);
          const sLon = parseFloat(s.geo_long);
          if (isNaN(sLat) || isNaN(sLon)) return false;
          const dist = Math.hypot(lat - sLat, lon - sLon);
          return dist < 3.2; // Local city/regional cluster
        }).slice(0, 12);

        if (nearby.length > 0) {
          nearbyCount.textContent = nearby.length;
          nearbyList.innerHTML = nearby.map(s => `
            <button class="nearby-station-pill ${currentStation && currentStation.stationuuid === s.stationuuid ? 'is-active' : ''}" data-uuid="${s.stationuuid}" title="${escapeHtml(s.name)}">
              ${escapeHtml(s.name)}
            </button>
          `).join('');

          nearbyList.querySelectorAll('.nearby-station-pill').forEach(pill => {
            pill.onclick = () => {
              const uuid = pill.getAttribute('data-uuid');
              const target = nearby.find(s => s.stationuuid === uuid);
              if (target) {
                playStation(target);
                this.showStationCard(target);
              }
            };
          });

          nearbySection.classList.remove('hidden');
        } else {
          nearbySection.classList.add('hidden');
        }
      }

      card.classList.remove('hidden');
    },

    updateZoomBadge: function () {
      const badge = document.getElementById('zoom-level-text');
      if (!badge) return;
      const dist = this.targetDistance;
      if (dist <= 112) {
        badge.textContent = 'City 📍';
      } else if (dist <= 135) {
        badge.textContent = 'Regional';
      } else if (dist <= 175) {
        badge.textContent = 'Country';
      } else {
        badge.textContent = 'Orbit';
      }
    },

    setupGlobeEvents: function () {
      const dom = this.renderer.domElement;
      const tooltip = document.getElementById('globe-hover-tooltip');

      // Calibrated mouse drag rotation (Significantly reduced sensitivity)
      dom.addEventListener('mousedown', (e) => {
        this.isDragging = true;
        this.prevMousePos = { x: e.clientX, y: e.clientY };
      });

      window.addEventListener('mouseup', () => {
        this.isDragging = false;
      });

      dom.addEventListener('mousemove', (e) => {
        const rect = dom.getBoundingClientRect();
        this.mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
        this.mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;

        if (this.isDragging) {
          const deltaX = e.clientX - this.prevMousePos.x;
          const deltaY = e.clientY - this.prevMousePos.y;

          // Smooth, non-jerky sensitivity factor
          this.targetRot.y += deltaX * 0.0016;
          this.targetRot.x += deltaY * 0.0016;
          this.targetRot.x = Math.max(-1.3, Math.min(1.3, this.targetRot.x));

          this.prevMousePos = { x: e.clientX, y: e.clientY };
        } else {
          this.checkHover(e, rect, tooltip);
        }
      });

      // Touch interactions
      dom.addEventListener('touchstart', (e) => {
        if (e.touches.length === 1) {
          this.isDragging = true;
          this.prevMousePos = { x: e.touches[0].clientX, y: e.touches[0].clientY };
        }
      });

      dom.addEventListener('touchmove', (e) => {
        if (this.isDragging && e.touches.length === 1) {
          const deltaX = e.touches[0].clientX - this.prevMousePos.x;
          const deltaY = e.touches[0].clientY - this.prevMousePos.y;

          this.targetRot.y += deltaX * 0.002;
          this.targetRot.x += deltaY * 0.002;
          this.targetRot.x = Math.max(-1.3, Math.min(1.3, this.targetRot.x));

          this.prevMousePos = { x: e.touches[0].clientX, y: e.touches[0].clientY };
        }
      });

      dom.addEventListener('touchend', () => {
        this.isDragging = false;
      });

      // Deeper Logarithmic Zoom via Mouse Wheel (Down to 103.8 city level)
      dom.addEventListener('wheel', (e) => {
        e.preventDefault();
        const altitude = this.cameraDistance - 100;
        const zoomFactor = Math.max(0.012, (altitude / 100) * 0.06);
        this.targetDistance += e.deltaY * zoomFactor;
        this.targetDistance = Math.max(103.8, Math.min(285, this.targetDistance));
        this.updateZoomBadge();
      }, { passive: false });

      // Double-click to fly deep into city/location
      dom.addEventListener('dblclick', (e) => {
        const rect = dom.getBoundingClientRect();
        this.mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
        this.mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
        this.raycaster.setFromCamera(this.mouse, this.camera);
        const intersects = this.raycaster.intersectObject(this.globeMesh);
        if (intersects.length > 0) {
          const pt = intersects[0].point;
          const lat = 90 - (Math.acos(pt.y / 100) * 180 / Math.PI);
          const lon = ((Math.atan2(pt.z, -pt.x) * 180 / Math.PI) - 180);
          this.flyToLocation(lat, lon);
          this.targetDistance = 110; // Deep city dive
          this.updateZoomBadge();
          showToast('Diving in close to city view 📍');
        }
      });

      // Click to select station
      dom.addEventListener('click', () => {
        if (this.hoveredStation) {
          playStation(this.hoveredStation);
          this.showStationCard(this.hoveredStation);
        }
      });

      // Resize
      window.addEventListener('resize', () => this.onResize());

      // Region fly-to buttons
      const regionBtns = document.querySelectorAll('.globe-region-btn');
      regionBtns.forEach(btn => {
        btn.addEventListener('click', () => {
          regionBtns.forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          const region = btn.getAttribute('data-region');
          this.flyToRegion(region);
        });
      });

      // HUD Navigation Tools
      document.getElementById('globe-rotate-btn').addEventListener('click', () => {
        this.autoRotate = !this.autoRotate;
        showToast(this.autoRotate ? 'Auto-spin enabled 🌐' : 'Auto-spin paused (Stationary)');
      });

      document.getElementById('globe-zoom-in-btn').addEventListener('click', () => {
        const step = Math.max(4, (this.targetDistance - 100) * 0.35);
        this.targetDistance = Math.max(103.8, this.targetDistance - step);
        this.updateZoomBadge();
      });

      document.getElementById('globe-zoom-out-btn').addEventListener('click', () => {
        const step = Math.max(8, (this.targetDistance - 100) * 0.45);
        this.targetDistance = Math.min(285, this.targetDistance + step);
        this.updateZoomBadge();
      });

      document.getElementById('globe-reset-btn').addEventListener('click', () => {
        this.targetRot = { x: 0.25, y: 0 };
        this.targetDistance = 245;
        this.updateZoomBadge();
        showToast('Camera reset to default');
      });

      const closeBtn = document.getElementById('gcard-close-btn');
      if (closeBtn) {
        closeBtn.addEventListener('click', () => {
          document.getElementById('globe-station-card').classList.add('hidden');
        });
      }
    },

    checkHover: function (e, rect, tooltip) {
      if (!this.pointsMesh || this.renderedStations.length === 0) return;

      this.raycaster.setFromCamera(this.mouse, this.camera);
      const intersects = this.raycaster.intersectObject(this.pointsMesh);

      if (intersects.length > 0) {
        const hitIdx = intersects[0].index;
        const station = this.renderedStations[hitIdx];
        if (station) {
          this.hoveredStation = station;
          this.mountEl.style.cursor = 'pointer';

          // Show subtle hover indicator ring at station location
          const pos = intersects[0].point;
          if (this.hoverMarkerMesh) {
            this.hoverMarkerMesh.position.copy(pos).multiplyScalar(1.002);
            this.hoverMarkerMesh.lookAt(new THREE.Vector3(0, 0, 0));
            this.hoverMarkerMesh.visible = true;
          }

          const flag = getCountryFlag(station.countrycode);
          document.getElementById('tooltip-flag').textContent = flag;
          document.getElementById('tooltip-title').textContent = station.name;
          document.getElementById('tooltip-sub').textContent = `${station.country || 'Global'} • ${station.bitrate ? station.bitrate + ' kbps' : (station.codec || 'Live')}`;

          tooltip.style.left = `${e.clientX - rect.left}px`;
          tooltip.style.top = `${e.clientY - rect.top}px`;
          tooltip.classList.remove('hidden');
          return;
        }
      }

      if (this.hoverMarkerMesh) this.hoverMarkerMesh.visible = false;
      this.hoveredStation = null;
      this.mountEl.style.cursor = 'grab';
      tooltip.classList.add('hidden');
    },

    // Radio Garden Center Reticle Targeting System
    checkCenterReticle: function () {
      if (!this.pointsMesh || this.renderedStations.length === 0) return;
      const reticleEl = document.getElementById('garden-reticle');

      this.raycaster.setFromCamera(new THREE.Vector2(0, 0), this.camera);
      // Dynamic threshold: tighter when zoomed deep, wider when far
      const altitude = this.cameraDistance - 100;
      this.raycaster.params.Points.threshold = Math.max(1.2, altitude * 0.035);

      const intersects = this.raycaster.intersectObject(this.pointsMesh);

      if (intersects.length > 0) {
        const hitIdx = intersects[0].index;
        const station = this.renderedStations[hitIdx];
        if (station) {
          if (reticleEl) reticleEl.classList.add('is-locked');
          if (this.hoverMarkerMesh) {
            const pos = intersects[0].point;
            this.hoverMarkerMesh.position.copy(pos).multiplyScalar(1.002);
            this.hoverMarkerMesh.lookAt(new THREE.Vector3(0, 0, 0));
            this.hoverMarkerMesh.visible = true;
          }
          this.hoveredStation = station;
          this.showStationCard(station);
          return;
        }
      }

      if (reticleEl) reticleEl.classList.remove('is-locked');
    },

    flyToRegion: function (region) {
      switch (region) {
        case 'eu': // Europe
          this.flyToLocation(48.8, 10.5);
          this.targetDistance = 145; // Closer view
          break;
        case 'na': // North America
          this.flyToLocation(39.8, -98.5);
          this.targetDistance = 155;
          break;
        case 'sa': // South America
          this.flyToLocation(-15.6, -56.0);
          this.targetDistance = 155;
          break;
        case 'as': // Asia
          this.flyToLocation(34.0, 105.0);
          this.targetDistance = 160;
          break;
        case 'af': // Africa
          this.flyToLocation(2.0, 21.0);
          this.targetDistance = 160;
          break;
        case 'oc': // Oceania
          this.flyToLocation(-25.2, 134.0);
          this.targetDistance = 160;
          break;
        default: // Global
          this.targetRot = { x: 0.25, y: 0 };
          this.targetDistance = 245;
          break;
      }
      this.updateZoomBadge();
    },

    onResize: function () {
      if (!this.mountEl || !this.renderer || !this.camera) return;
      const width = this.mountEl.clientWidth;
      const height = this.mountEl.clientHeight;
      if (width === 0 || height === 0) return;

      this.camera.aspect = width / height;
      this.camera.updateProjectionMatrix();
      this.renderer.setSize(width, height);
    },

    startAnimation: function () {
      let beaconScale = 1;
      let beaconOpacity = 0.85;
      let frameCount = 0;

      const render = () => {
        this.animFrameId = requestAnimationFrame(render);
        frameCount++;

        // Only rotate if user explicitly enabled it
        if (!this.isDragging && this.autoRotate) {
          this.targetRot.y += 0.0015;
        }

        // Calibrated smooth damping (0.06)
        this.currentRot.x += (this.targetRot.x - this.currentRot.x) * 0.06;
        this.currentRot.y += (this.targetRot.y - this.currentRot.y) * 0.06;
        this.cameraDistance += (this.targetDistance - this.cameraDistance) * 0.06;

        // Apply rotation to globe & points
        this.globeMesh.rotation.x = this.currentRot.x;
        this.globeMesh.rotation.y = this.currentRot.y;

        if (this.pointsMesh) {
          this.pointsMesh.rotation.x = this.currentRot.x;
          this.pointsMesh.rotation.y = this.currentRot.y;
        }

        // Radio Garden reticle lock check (throttled)
        if (frameCount % 4 === 0) {
          this.checkCenterReticle();
        }

        // Animate beacon pulse for active station
        if (this.beaconMesh && this.beaconMesh.visible) {
          beaconScale += 0.035;
          beaconOpacity -= 0.02;
          if (beaconScale > 2.2) {
            beaconScale = 1;
            beaconOpacity = 0.85;
          }
          this.beaconMesh.scale.set(beaconScale, beaconScale, 1);
          this.beaconMesh.material.opacity = Math.max(0, beaconOpacity);
        }

        this.camera.position.z = this.cameraDistance;
        this.renderer.render(this.scene, this.camera);
      };

      render();
    }
  };

  // --- View Switcher Logic ---
  function switchView(viewName) {
    currentView = viewName;
    if (viewName === 'grid') {
      viewGridBtn.classList.add('active');
      viewGlobeBtn.classList.remove('active');
      viewGridBtn.setAttribute('aria-selected', 'true');
      viewGlobeBtn.setAttribute('aria-selected', 'false');

      gridViewContainer.classList.remove('hidden');
      globeViewContainer.classList.add('hidden');
    } else {
      viewGlobeBtn.classList.add('active');
      viewGridBtn.classList.remove('active');
      viewGlobeBtn.setAttribute('aria-selected', 'true');
      viewGridBtn.setAttribute('aria-selected', 'false');

      gridViewContainer.classList.add('hidden');
      globeViewContainer.classList.remove('hidden');

      if (!GlobeRadar.isInitialized) {
        GlobeRadar.init();
      } else {
        setTimeout(() => GlobeRadar.onResize(), 50);
      }

      if (currentStation) {
        GlobeRadar.setActiveStation(currentStation);
      }
    }
  }

  // --- Event Handlers & Listeners ---
  function setupEventListeners() {
    viewGridBtn.addEventListener('click', () => switchView('grid'));
    viewGlobeBtn.addEventListener('click', () => switchView('globe'));

    playPauseBtn.addEventListener('click', togglePlayPause);
    prevStationBtn.addEventListener('click', playPrevStation);
    nextStationBtn.addEventListener('click', playNextStation);

    currentFavBtn.addEventListener('click', () => {
      if (currentStation) toggleFavorite(currentStation);
    });

    volumeSlider.addEventListener('input', (e) => {
      const vol = parseFloat(e.target.value);
      audio.volume = vol;
      volumePct.textContent = `${Math.round(vol * 100)}%`;
      localStorage.setItem('pulse_volume', vol.toString());

      if (vol === 0) {
        volumeIconHigh.classList.add('hidden');
        volumeIconMuted.classList.remove('hidden');
      } else {
        volumeIconHigh.classList.remove('hidden');
        volumeIconMuted.classList.add('hidden');
        previousVolume = vol;
      }
    });

    muteBtn.addEventListener('click', () => {
      if (audio.volume > 0) {
        previousVolume = audio.volume;
        audio.volume = 0;
        volumeSlider.value = 0;
        volumePct.textContent = '0%';
        volumeIconHigh.classList.add('hidden');
        volumeIconMuted.classList.remove('hidden');
      } else {
        const restoreVol = previousVolume > 0 ? previousVolume : 0.8;
        audio.volume = restoreVol;
        volumeSlider.value = restoreVol;
        volumePct.textContent = `${Math.round(restoreVol * 100)}%`;
        volumeIconHigh.classList.remove('hidden');
        volumeIconMuted.classList.add('hidden');
      }
    });

    searchInput.addEventListener('input', (e) => {
      const val = e.target.value;
      if (val.trim()) {
        clearSearchBtn.classList.remove('hidden');
      } else {
        clearSearchBtn.classList.add('hidden');
      }

      clearTimeout(searchDebounceTimer);
      searchDebounceTimer = setTimeout(() => {
        performSearch(val);
      }, 350);
    });

    clearSearchBtn.addEventListener('click', () => {
      searchInput.value = '';
      clearSearchBtn.classList.add('hidden');
      performSearch('');
    });

    genrePills.addEventListener('click', (e) => {
      const pill = e.target.closest('.pill');
      if (!pill) return;

      document.querySelectorAll('.genre-pills-container .pill').forEach(p => p.classList.remove('active'));
      pill.classList.add('active');

      const genre = pill.getAttribute('data-genre');
      activeGenre = genre;
      searchInput.value = '';
      clearSearchBtn.classList.add('hidden');

      if (genre === 'featured') {
        loadCuratedStations();
      } else if (genre === 'topclick') {
        loadTopStations();
      } else if (genre === 'favorites') {
        renderFavorites();
      } else {
        loadGenreStations(genre);
      }
    });

    countrySelect.addEventListener('change', () => {
      if (searchInput.value.trim()) {
        performSearch(searchInput.value.trim());
      } else if (activeGenre !== 'featured' && activeGenre !== 'favorites') {
        if (activeGenre === 'topclick') loadTopStations();
        else loadGenreStations(activeGenre);
      }
    });

    sortSelect.addEventListener('change', () => {
      if (searchInput.value.trim()) {
        performSearch(searchInput.value.trim());
      } else if (activeGenre !== 'featured' && activeGenre !== 'favorites') {
        if (activeGenre === 'topclick') loadTopStations();
        else loadGenreStations(activeGenre);
      }
    });

    resetFilterBtn.addEventListener('click', () => {
      countrySelect.value = '';
      sortSelect.value = 'clickcount';
      searchInput.value = '';
      clearSearchBtn.classList.add('hidden');
      document.querySelector('.genre-pills-container .pill[data-genre="featured"]').click();
    });

    sleepTimerBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      sleepTimerMenu.classList.toggle('hidden');
    });

    document.addEventListener('click', () => {
      sleepTimerMenu.classList.add('hidden');
    });

    sleepTimerMenu.addEventListener('click', (e) => {
      const item = e.target.closest('.menu-item');
      if (!item) return;
      const minutes = parseInt(item.getAttribute('data-time'), 10);
      startSleepTimer(minutes);
      sleepTimerMenu.classList.add('hidden');
    });

    openCustomModalBtn.addEventListener('click', () => {
      customModal.classList.remove('hidden');
    });

    closeCustomModalBtn.addEventListener('click', () => {
      customModal.classList.add('hidden');
    });

    cancelCustomBtn.addEventListener('click', () => {
      customModal.classList.add('hidden');
    });

    customModal.addEventListener('click', (e) => {
      if (e.target === customModal) customModal.classList.add('hidden');
    });

    customForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const name = document.getElementById('custom-name-input').value.trim();
      const url = document.getElementById('custom-url-input').value.trim();
      const genre = document.getElementById('custom-genre-input').value.trim();

      if (!url) return;

      const customStation = {
        stationuuid: 'custom-' + Date.now(),
        name: name || 'Custom Radio Stream',
        url_resolved: url,
        url: url,
        country: 'Custom',
        countrycode: '',
        tags: genre || 'custom, stream',
        codec: 'LIVE',
        bitrate: 0,
        favicon: '',
        votes: 1
      };

      favorites.unshift(customStation);
      saveFavorites();

      customModal.classList.add('hidden');
      customForm.reset();

      showToast(`Added and playing custom stream "${customStation.name}"`);
      playStation(customStation);
    });

    document.getElementById('toggle-visualizer-btn').addEventListener('click', () => {
      visualizerMode = (visualizerMode + 1) % 2;
      showToast(visualizerMode === 0 ? 'Visualizer: Neon Wave mode' : 'Visualizer: Frequency Bars mode');
    });

    window.addEventListener('keydown', (e) => {
      if (['INPUT', 'SELECT', 'TEXTAREA'].includes(document.activeElement.tagName)) {
        return;
      }

      if (e.code === 'Space') {
        e.preventDefault();
        togglePlayPause();
      } else if (e.altKey && e.code === 'ArrowRight') {
        e.preventDefault();
        playNextStation();
      } else if (e.altKey && e.code === 'ArrowLeft') {
        e.preventDefault();
        playPrevStation();
      } else if (e.key === 'm' || e.key === 'M') {
        e.preventDefault();
        muteBtn.click();
      }
    });
  }

  // --- Run on Load ---
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
