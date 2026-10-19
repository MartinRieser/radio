/**
 * PulseCast Web Radio
 * Modern Web Radio player powered by the free Radio Browser API
 * Featuring Grid View with Search, Genre Filters, and Ad Policy Detection
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

  // --- Handpicked Featured High-Uptime Stations ---
  const CURATED_STATIONS = [
    {
      stationuuid: 'bcb91573-5557-456d-afb2-ee1462efbd25',
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
      stationuuid: '206f93c5-5f3c-4ba1-82c2-19a42582fcc2',
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
      stationuuid: '067388ea-bff2-41b5-869a-766b2f7b4a47',
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
      stationuuid: '0de82079-04e7-407b-b616-e0726eba5244',
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
      stationuuid: '240d28b9-7858-48d2-a816-9cf8e1875fe8',
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
      stationuuid: 'b2cbd1fd-275d-432a-8b20-37dcb3572315',
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
      stationuuid: '7b6aca3d-dfa3-47dd-9f57-820b04c3f339',
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
      stationuuid: '0596bdc5-77fc-48ba-8653-e5e34bb86568',
      name: 'SomaFM Groove Salad',
      url_resolved: 'https://ice1.somafm.com/groovesalad-256-mp3',
      country: 'United States',
      countrycode: 'US',
      tags: 'ambient, chillout, downtempo, electronic',
      codec: 'MP3',
      bitrate: 256,
      favicon: 'https://somafm.com/img3/groovesalad120.png',
      votes: 2900,
      geo_lat: 37.7749,
      geo_long: -122.4194
    },
    {
      stationuuid: 'e9fddd49-3ee2-4597-8574-1b7dbd00aac0',
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
      stationuuid: '4260f1f5-d5b3-44d5-9666-355da4da0b21',
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
      stationuuid: '34edbcea-ce81-4e39-81ab-71e74745d544',
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
      stationuuid: '960eb2e9-0601-11e8-ae97-52543be04c81',
      name: 'SomaFM Drone Zone',
      url_resolved: 'https://ice5.somafm.com/dronezone-128-mp3',
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
      stationuuid: '9a368941-71a3-4bda-9186-6604d9030514',
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
      stationuuid: '960e4940-0601-11e8-ae97-52543be04c81',
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
      stationuuid: '78d27eb0-ad49-4188-be72-38f7137e4164',
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
      stationuuid: '0ec95505-0ff5-4b11-af12-80d77d591367',
      name: 'SomaFM Secret Agent',
      url_resolved: 'https://ice6.somafm.com/secretagent-128-aac',
      country: 'United States',
      countrycode: 'US',
      tags: 'spy, surf, lounge, vintage',
      codec: 'AAC',
      bitrate: 128,
      favicon: 'https://somafm.com/img3/secretagent120.png',
      votes: 1150,
      geo_lat: 37.7649,
      geo_long: -122.4294
    },
    {
      stationuuid: '4b473070-a3c2-41bd-8d98-c2db5f5954bc',
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
      stationuuid: 'f7fd408e-02a5-49a1-8c03-078727e97e8f',
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
      stationuuid: '14b6c684-bb7c-4926-b2a3-fd5a02bf7867',
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
  let stationsList = [];
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
  const gridViewContainer = document.getElementById('grid-view-container');

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
  const playerAdBadge = document.getElementById('player-ad-badge');
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

  // More Options Dropdown
  const moreMenuBtn = document.getElementById('more-menu-btn');
  const moreDropdownMenu = document.getElementById('more-dropdown-menu');

  // Custom Stream Modal
  const openCustomModalBtn = document.getElementById('open-custom-modal-btn');
  const closeCustomModalBtn = document.getElementById('close-custom-modal-btn');
  const cancelCustomBtn = document.getElementById('cancel-custom-btn');
  const customModal = document.getElementById('custom-stream-modal');
  const customForm = document.getElementById('custom-stream-form');

  // On-Demand Stream Links Manager DOM
  const updateLinksBtn = document.getElementById('update-links-btn');
  const updateLinksBtnText = document.getElementById('update-links-btn-text');
  const linksStatusBadge = document.getElementById('links-status-badge');
  const linksStatusText = document.getElementById('links-status-text');
  const updateLinksModal = document.getElementById('update-links-modal');
  const closeLinksModalBtn = document.getElementById('close-links-modal-btn');
  const closeLinksModalBottomBtn = document.getElementById('close-links-modal-bottom-btn');
  const triggerUpdateLinksBtn = document.getElementById('trigger-update-links-btn');
  const syncFavsCount = document.getElementById('sync-favs-count');
  const syncFeaturedCount = document.getElementById('sync-featured-count');
  const syncDirectoryCount = document.getElementById('sync-directory-count');
  const syncLastTime = document.getElementById('sync-last-time');
  const syncApiMirror = document.getElementById('sync-api-mirror');
  let isUpdatingLinks = false;

  // Header Favorites Button
  const showFavoritesBtn = document.getElementById('show-favorites-btn');
  const headerFavCount = document.getElementById('header-fav-count');

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
    loadCachedCuratedStations();
    loadFavorites();
    initAudioEvents();
    initVisualizer();
    setupEventListeners();
    updateLinksStatusDisplay();

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

    // Automatically check and refresh radio station stream links on every app launch
    setTimeout(() => {
      updateStationLinks({ silent: true, isAuto: true });
    }, 1800);
  }

  // --- Cached Curated Stations ---
  function loadCachedCuratedStations() {
    try {
      const cached = localStorage.getItem('pulse_curated_cache');
      if (cached) {
        const parsed = JSON.parse(cached);
        if (Array.isArray(parsed) && parsed.length > 0) {
          CURATED_STATIONS.forEach(c => {
            const match = parsed.find(p => p.stationuuid === c.stationuuid);
            if (match && match.url_resolved) {
              c.url_resolved = match.url_resolved;
              if (match.bitrate) c.bitrate = match.bitrate;
              if (match.codec) c.codec = match.codec;
              if (match.favicon) c.favicon = match.favicon;
            }
          });
        }
      }
    } catch (e) {
      console.warn('Failed loading cached curated stations:', e);
    }
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
    if (headerFavCount) headerFavCount.textContent = favorites.length;
    if (syncFavsCount) syncFavsCount.textContent = favorites.length;
  }

  function showFavorites() {
    if (searchInput) {
      searchInput.value = '';
      if (clearSearchBtn) clearSearchBtn.classList.add('hidden');
    }

    activeGenre = 'favorites';
    document.querySelectorAll('.genre-pills-container .pill').forEach(p => {
      if (p.getAttribute('data-genre') === 'favorites') {
        p.classList.add('active');
        p.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
      } else {
        p.classList.remove('active');
      }
    });

    if (showFavoritesBtn) {
      showFavoritesBtn.classList.add('is-active');
    }

    renderFavorites();
  }

  function toggleFavoritesView() {
    if (activeGenre === 'favorites') {
      const featuredPill = document.querySelector('.genre-pills-container .pill[data-genre="featured"]');
      if (featuredPill) {
        featuredPill.click();
      } else {
        activeGenre = 'featured';
        if (showFavoritesBtn) showFavoritesBtn.classList.remove('is-active');
        loadCuratedStations();
      }
    } else {
      showFavorites();
    }
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

  // --- Pre-Roll & Audio Ad Policy Detection ---
  function detectAdPolicy(station) {
    if (!station) {
      return { type: 'unknown', label: 'Direktstream', isAdFree: false, isPrerollLikely: false, tooltip: 'Standard-Stream' };
    }

    const url = (station.url_resolved || station.url || '').toLowerCase();
    const name = (station.name || '').toLowerCase();
    const tags = (station.tags || '').toLowerCase();

    // 1. Guaranteed 100% Ad-Free (Public Broadcasters & Independent Listener-Supported)
    const isAdFree =
      // Public Broadcasters: Swiss SRG SSR / SRF
      url.includes('srg-ssr.ch') || url.includes('srf.ch') || name.includes('swiss pop') || name.includes('swiss jazz') || name.includes('swiss classic') ||
      // BBC
      url.includes('bbc.co.uk') || url.includes('bbcfmt.net') || name.includes('bbc ') || name.startsWith('bbc') ||
      // German ARD / Deutschlandradio
      url.includes('wdr.de') || url.includes('ndr.de') || url.includes('swr.de') || url.includes('br.de') || url.includes('hr.de') || url.includes('rbb-online.de') || url.includes('deutschlandfunk') || url.includes('dradio.de') ||
      // Austrian ORF
      url.includes('orf.at') || name.includes('fm4') || name.includes('ö1') || name.includes('oe1') ||
      // French Radio France (FIP, France Musique, etc.)
      url.includes('radiofrance.fr') || url.includes('fip.') || name.includes('fip') || name.includes('france musique') ||
      // Independent non-profit / listener supported
      url.includes('somafm.com') || name.includes('somafm') ||
      url.includes('kexp.org') || name.includes('kexp') ||
      url.includes('byte.fm') || url.includes('radioparadise.com') ||
      // Explicit tags
      tags.includes('ad-free') || tags.includes('commercial-free') || tags.includes('werbefrei') || tags.includes('no ads');

    if (isAdFree) {
      return {
        type: 'ad-free',
        label: 'Werbefrei (0% Pre-Roll)',
        shortLabel: 'Werbefrei',
        isAdFree: true,
        isPrerollLikely: false,
        tooltip: 'Öffentlich-rechtlich oder spendenfinanziert: 100% werbefreier Audiostream ohne Pre-Roll Unterbrechung'
      };
    }

    // 2. High probability of pre-roll ad insertion (Commercial SSAI ad network CDNs)
    const isPrerollLikely =
      url.includes('streamtheworld.com') ||
      url.includes('tritondigital.com') ||
      url.includes('adswizz.com') ||
      url.includes('ais-stream') ||
      url.includes('targetspot.com') ||
      url.includes('bauerweb.co.uk') ||
      url.includes('planetradio.co.uk') ||
      url.includes('live365.com') ||
      url.includes('?awparams=') ||
      url.includes('&awparams=') ||
      url.includes('?preroll=') ||
      url.includes('&preroll=');

    if (isPrerollLikely) {
      return {
        type: 'preroll-likely',
        label: 'Pre-Roll Werbung möglich',
        shortLabel: 'Pre-Roll möglich',
        isAdFree: false,
        isPrerollLikely: true,
        tooltip: 'Kommerzielles Ad-Netzwerk (Triton / AdsWizz): Vor Streamstart kann ein Werbespot geschaltet werden'
      };
    }

    // 3. Direct Icecast / Shoutcast Stream without known commercial ad wrappers
    return {
      type: 'direct',
      label: 'Direktstream (Standard)',
      shortLabel: 'Direktstream',
      isAdFree: false,
      isPrerollLikely: false,
      tooltip: 'Direkter Radiostream ohne bekannte Pre-Roll Werbe-Engine'
    };
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

  // ==========================================================================
  // Station Links Manager & On-Demand Synchronization
  // ==========================================================================

  function formatLastUpdatedTime(timestamp) {
    if (!timestamp) return 'Never';
    const diffMs = Date.now() - timestamp;
    const diffSec = Math.floor(diffMs / 1000);
    const diffMin = Math.floor(diffSec / 60);
    const diffHours = Math.floor(diffMin / 60);

    if (diffSec < 60) return 'Just now';
    if (diffMin < 60) return `${diffMin}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    return new Date(timestamp).toLocaleDateString();
  }

  function updateLinksStatusDisplay(timestamp) {
    const ts = timestamp || parseInt(localStorage.getItem('pulse_links_last_updated') || '0', 10);
    const text = ts ? `Links: ${formatLastUpdatedTime(ts)}` : 'Links: Up to date';
    if (linksStatusText) {
      linksStatusText.textContent = text;
    }
    if (syncLastTime) {
      syncLastTime.textContent = ts ? formatLastUpdatedTime(ts) : 'On startup';
    }
    if (syncFavsCount) {
      syncFavsCount.textContent = favorites.length;
    }
    if (syncFeaturedCount) {
      syncFeaturedCount.textContent = CURATED_STATIONS.length;
    }
    if (syncDirectoryCount) {
      syncDirectoryCount.textContent = '40,000+';
    }
    if (syncApiMirror) {
      syncApiMirror.textContent = getApiBase();
    }
  }

  function setUpdateLinksUiState(updating) {
    if (updateLinksBtn) {
      if (updating) {
        updateLinksBtn.classList.add('is-updating');
        if (updateLinksBtnText) updateLinksBtnText.textContent = 'Updating...';
      } else {
        updateLinksBtn.classList.remove('is-updating');
        if (updateLinksBtnText) updateLinksBtnText.textContent = 'Update Links';
      }
    }
    if (linksStatusBadge) {
      if (updating) {
        linksStatusBadge.classList.add('is-syncing');
        if (linksStatusText) linksStatusText.textContent = 'Updating links...';
      } else {
        linksStatusBadge.classList.remove('is-syncing');
        updateLinksStatusDisplay();
      }
    }
    if (triggerUpdateLinksBtn) {
      triggerUpdateLinksBtn.disabled = updating;
      const span = triggerUpdateLinksBtn.querySelector('span');
      if (span) span.textContent = updating ? 'Synchronizing with API...' : 'Update All Links Now';
    }
  }

  function openLinksManagerModal() {
    updateLinksStatusDisplay();
    if (updateLinksModal) updateLinksModal.classList.remove('hidden');
  }

  function closeLinksManagerModal() {
    if (updateLinksModal) updateLinksModal.classList.add('hidden');
  }

  async function updateStationLinks(options = {}) {
    const silent = !!options.silent;
    const isAuto = !!options.isAuto;

    if (isUpdatingLinks) {
      if (!silent) showToast('Station links update is already running...');
      return { inProgress: true };
    }

    isUpdatingLinks = true;
    setUpdateLinksUiState(true);

    let updatedFavorites = 0;
    let verifiedFavorites = 0;
    let updatedCurated = 0;
    let verifiedCurated = 0;

    try {
      // 1. Synchronize Curated Stations Links with API
      const curatedUuids = CURATED_STATIONS
        .map(s => s.stationuuid)
        .filter(uuid => uuid && uuid.length >= 30);

      if (curatedUuids.length > 0) {
        try {
          const res = await fetch(`${getApiBase()}/json/stations/byuuid?uuids=${curatedUuids.join(',')}`, {
            headers: { 'Accept': 'application/json' }
          });
          if (res.ok) {
            const apiStations = await res.json();
            if (Array.isArray(apiStations)) {
              apiStations.forEach(apiStation => {
                const local = CURATED_STATIONS.find(c => c.stationuuid === apiStation.stationuuid);
                if (local && (apiStation.url_resolved || apiStation.url)) {
                  const resolvedUrl = apiStation.url_resolved || apiStation.url;
                  if (local.url_resolved !== resolvedUrl) {
                    local.url_resolved = resolvedUrl;
                    updatedCurated++;
                  }
                  if (apiStation.bitrate) local.bitrate = apiStation.bitrate;
                  if (apiStation.codec) local.codec = apiStation.codec;
                  if (apiStation.favicon && !local.favicon) local.favicon = apiStation.favicon;
                  verifiedCurated++;
                }
              });

              try {
                localStorage.setItem('pulse_curated_cache', JSON.stringify(CURATED_STATIONS));
              } catch (e) {}
            }
          }
        } catch (curatedErr) {
          console.warn('Failed to update curated stations from API mirror:', curatedErr);
        }
      }

      // 2. Synchronize Saved Favorites Links with API
      if (favorites.length > 0) {
        const favUuids = favorites
          .map(f => f.stationuuid)
          .filter(uuid => uuid && uuid.length >= 30 && !uuid.startsWith('custom-'));

        let freshFavMap = new Map();

        if (favUuids.length > 0) {
          try {
            const res = await fetch(`${getApiBase()}/json/stations/byuuid?uuids=${favUuids.join(',')}`, {
              headers: { 'Accept': 'application/json' }
            });
            if (res.ok) {
              const data = await res.json();
              if (Array.isArray(data)) {
                data.forEach(s => {
                  if (s && s.stationuuid) freshFavMap.set(s.stationuuid, s);
                });
              }
            }
          } catch (favErr) {
            console.warn('Failed batch fetching favorites by UUID:', favErr);
          }
        }

        let favsChanged = false;
        for (const fav of favorites) {
          let fresh = freshFavMap.get(fav.stationuuid);

          // If not in batch response, and not a custom user URL, fallback to name search
          if (!fresh && fav.name && (!fav.stationuuid || !fav.stationuuid.startsWith('custom-'))) {
            try {
              const cleanName = fav.name.split('-')[0].split('(')[0].trim();
              const searchRes = await fetch(`${getApiBase()}/json/stations/search?name=${encodeURIComponent(cleanName)}&limit=1`);
              if (searchRes.ok) {
                const results = await searchRes.json();
                if (Array.isArray(results) && results.length > 0) {
                  fresh = results[0];
                }
              }
            } catch (e) {}
          }

          if (fresh && (fresh.url_resolved || fresh.url)) {
            const resolvedUrl = fresh.url_resolved || fresh.url;
            if (fav.url_resolved !== resolvedUrl) {
              fav.url_resolved = resolvedUrl;
              fav.url = fresh.url || resolvedUrl;
              updatedFavorites++;
              favsChanged = true;
            }
            if (fresh.bitrate) fav.bitrate = fresh.bitrate;
            if (fresh.codec) fav.codec = fresh.codec;
            if (fresh.favicon && !fav.favicon) fav.favicon = fresh.favicon;
            verifiedFavorites++;
          }
        }

        if (favsChanged) {
          saveFavorites();
        }
      }

      // 3. Update Currently Playing Station if its URL was refreshed
      if (currentStation) {
        const matchCurated = CURATED_STATIONS.find(c => c.stationuuid === currentStation.stationuuid);
        if (matchCurated && matchCurated.url_resolved !== currentStation.url_resolved) {
          currentStation.url_resolved = matchCurated.url_resolved;
          currentStation.url = matchCurated.url || matchCurated.url_resolved;
        }
        const matchFav = favorites.find(f => f.stationuuid === currentStation.stationuuid);
        if (matchFav && matchFav.url_resolved !== currentStation.url_resolved) {
          currentStation.url_resolved = matchFav.url_resolved;
          currentStation.url = matchFav.url || matchFav.url_resolved;
        }
      }

      // 4. Refresh Active UI Grid View
      if (activeGenre === 'featured') {
        stationsList = CURATED_STATIONS;
        renderCards(stationsList);
      } else if (activeGenre === 'favorites') {
        renderFavorites();
      }

      // 5. Record Timestamp
      const now = Date.now();
      localStorage.setItem('pulse_links_last_updated', now.toString());
      updateLinksStatusDisplay(now);

      const totalUpdated = updatedFavorites + updatedCurated;
      const totalVerified = verifiedFavorites + verifiedCurated;

      // 7. User Notification Feedback
      if (!silent) {
        if (totalUpdated > 0) {
          showToast(`✨ Updated ${totalUpdated} radio stream link(s)! All streams active.`);
        } else {
          showToast(`✓ All station stream links verified up to date (${totalVerified} checked)`);
        }
      } else if (isAuto && totalUpdated > 0) {
        showToast(`🔄 Auto-refreshed ${totalUpdated} stream links to latest live servers.`);
      }

      return {
        success: true,
        updatedFavorites,
        verifiedFavorites,
        updatedCurated,
        verifiedCurated,
        totalUpdated,
        totalVerified,
        timestamp: now
      };
    } catch (err) {
      console.error('Error synchronizing station links:', err);
      if (!silent) {
        showToast('Encountered an issue updating links. Rotated to backup API mirror.');
      }
      rotateServer();
      return { success: false, error: err };
    } finally {
      isUpdatingLinks = false;
      setUpdateLinksUiState(false);
    }
  }

  // --- Self-Healing Stream Recovery on Playback Interruption ---
  async function handlePlaybackError(station, error) {
    if (!station) return;

    // Guard against repeated retry loops
    if (station._healAttempted && Date.now() - station._healAttempted < 30000) {
      isPlaying = false;
      bufferingSpinner.classList.add('hidden');
      playIcon.classList.remove('hidden');
      pauseIcon.classList.add('hidden');
      liveDot.classList.remove('active');
      setPlayerStatus('error', 'OFFLINE');
      playbackTimer.textContent = 'OFFLINE';
      stopLiveTimer();
      showToast(`Unable to play "${station.name}". Stream might be offline or blocked.`);
      return;
    }

    station._healAttempted = Date.now();
    setPlayerStatus('loading', 'HEALING LINK');
    showToast(`Stream issue detected. Checking live API for updated URL for "${station.name}"...`);

    let freshStation = null;
    if (station.stationuuid && station.stationuuid.length >= 30 && !station.stationuuid.startsWith('custom-')) {
      try {
        const res = await fetch(`${getApiBase()}/json/stations/byuuid/${station.stationuuid}`);
        if (res.ok) {
          const list = await res.json();
          if (Array.isArray(list) && list.length > 0) freshStation = list[0];
        }
      } catch (e) {}
    }

    if (!freshStation && station.name) {
      try {
        const cleanName = station.name.split('-')[0].split('(')[0].trim();
        const res = await fetch(`${getApiBase()}/json/stations/search?name=${encodeURIComponent(cleanName)}&limit=1`);
        if (res.ok) {
          const list = await res.json();
          if (Array.isArray(list) && list.length > 0) freshStation = list[0];
        }
      } catch (e) {}
    }

    if (freshStation && (freshStation.url_resolved || freshStation.url)) {
      const newUrl = freshStation.url_resolved || freshStation.url;
      if (newUrl !== station.url_resolved) {
        station.url_resolved = newUrl;
        station.url = freshStation.url || newUrl;
        if (freshStation.bitrate) station.bitrate = freshStation.bitrate;
        if (freshStation.codec) station.codec = freshStation.codec;

        // Update in favorites if saved
        const favIdx = favorites.findIndex(f => f.stationuuid === station.stationuuid || f.name === station.name);
        if (favIdx >= 0) {
          favorites[favIdx].url_resolved = newUrl;
          saveFavorites();
        }

        showToast(`Found updated live stream link for "${station.name}"! Reconnecting...`);
        audio.src = newUrl;
        audio.load();
        const retryPromise = audio.play();
        if (retryPromise !== undefined) {
          retryPromise.then(() => {
            isPlaying = true;
            updatePlaybackUI();
            startLiveTimer();
          }).catch(retryErr => {
            console.error('Playback error on retried link:', retryErr);
            setPlayerStatus('error', 'OFFLINE');
            showToast(`Stream for "${station.name}" is currently offline on broadcaster server.`);
          });
        }
        return;
      }
    }

    // Default error state if no new link was found
    isPlaying = false;
    bufferingSpinner.classList.add('hidden');
    playIcon.classList.remove('hidden');
    pauseIcon.classList.add('hidden');
    liveDot.classList.remove('active');
    setPlayerStatus('error', 'STREAM ERROR');
    playbackTimer.textContent = 'OFFLINE';
    stopLiveTimer();
    showToast(`Unable to play "${station.name}". Stream might be offline or blocked.`);
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

  async function loadAdFreeStations() {
    sectionTitle.textContent = 'Werbefreie Radiostreams (0% Pre-Roll)';
    resultsCount.textContent = 'Lade werbefreie Sender...';

    // 1. Curated verified ad-free streams
    const adFreeCurated = CURATED_STATIONS.filter(s => detectAdPolicy(s).isAdFree);

    // 2. Fetch popular stations & search public radio tags
    try {
      const sort = sortSelect.value || 'clickcount';
      const country = countrySelect.value || '';
      const params = {
        limit: 80,
        order: sort,
        reverse: 'true',
        hidebroken: 'true'
      };
      if (country) params.countrycode = country;

      const [topStations, publicStations] = await Promise.all([
        fetchRadioStations('/json/stations/topclick/80', country ? { countrycode: country } : {}),
        fetchRadioStations('/json/stations/search', { ...params, tag: 'public radio' })
      ]);

      const map = new Map();
      adFreeCurated.forEach(s => {
        if (!country || s.countrycode === country) map.set(s.stationuuid, s);
      });

      [...(publicStations || []), ...(topStations || [])].forEach(s => {
        if (s && s.stationuuid && !map.has(s.stationuuid)) {
          if (detectAdPolicy(s).isAdFree) {
            map.set(s.stationuuid, s);
          }
        }
      });

      stationsList = Array.from(map.values());
      resultsCount.textContent = `${stationsList.length} werbefreie Sender gefunden`;
      renderCards(stationsList);
    } catch (err) {
      stationsList = adFreeCurated;
      resultsCount.textContent = `${stationsList.length} werbefreie Sender gefunden`;
      renderCards(stationsList);
    }
  }

  async function performSearch(query) {
    if (!query || query.trim() === '') {
      if (activeGenre === 'featured') {
        loadCuratedStations();
      } else if (activeGenre === 'topclick') {
        loadTopStations();
      } else if (activeGenre === 'favorites') {
        renderFavorites();
      } else if (activeGenre === 'adfree') {
        loadAdFreeStations();
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

      // Clean tags into sleek human-readable genres (no '#' clutter)
      const tagsArray = station.tags ? station.tags.split(',').map(t => t.trim()).filter(Boolean).slice(0, 2) : [];
      const cleanGenres = tagsArray.map(t => t.charAt(0).toUpperCase() + t.slice(1)).join(' • ');

      // Ad policy check (Werbefrei vs Pre-Roll)
      const adPolicy = detectAdPolicy(station);
      let adBadgeHtml = '';
      if (adPolicy.isAdFree) {
        adBadgeHtml = `<span class="station-ad-badge badge-adfree" title="${escapeHtml(adPolicy.tooltip)}"><span class="ad-dot dot-adfree"></span>Werbefrei</span>`;
      } else if (adPolicy.isPrerollLikely) {
        adBadgeHtml = `<span class="station-ad-badge badge-preroll" title="${escapeHtml(adPolicy.tooltip)}"><span class="ad-dot dot-preroll"></span>Pre-Roll</span>`;
      }

      const isCurrentPlaying = card.classList.contains('is-active') && isPlaying;

      card.innerHTML = `
        <div class="station-art-box">
          ${station.favicon ? `<img src="${escapeHtml(station.favicon)}" alt="" loading="lazy" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">` : ''}
          <div class="station-art-placeholder" style="${station.favicon ? 'display:none;' : ''}">📻</div>
          <div class="card-play-hover-btn" aria-label="Play">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <polygon points="7 4 19 12 7 20 7 4"></polygon>
            </svg>
          </div>
          ${isCurrentPlaying ? `
            <div class="card-equalizer">
              <span></span><span></span><span></span><span></span>
            </div>
          ` : ''}
        </div>

        <div class="station-info">
          <div class="station-title-row">
            <h3 class="station-name" title="${escapeHtml(station.name)}">${escapeHtml(station.name)}</h3>
            <button class="card-fav-btn ${isFav ? 'is-fav' : ''}" title="Favorite" aria-label="Favorite">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
              </svg>
            </button>
          </div>
          <div class="station-sub">
            <span class="station-country">${flag} ${escapeHtml(station.country || 'Global')}</span>
            ${cleanGenres ? `<span class="station-sub-dot">•</span><span class="station-genre-text">${escapeHtml(cleanGenres)}</span>` : ''}
            ${adBadgeHtml ? `<span class="station-sub-dot">•</span>${adBadgeHtml}` : ''}
          </div>
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

    // Update Pre-roll ad policy badge in player bar
    const adPolicy = detectAdPolicy(station);
    if (playerAdBadge) {
      if (adPolicy.isAdFree) {
        playerAdBadge.className = 'player-ad-pill adfree';
        playerAdBadge.innerHTML = '<span class="ad-dot dot-adfree"></span> Werbefrei (0% Pre-Roll)';
        playerAdBadge.title = adPolicy.tooltip;
        playerAdBadge.classList.remove('hidden');
      } else if (adPolicy.isPrerollLikely) {
        playerAdBadge.className = 'player-ad-pill preroll';
        playerAdBadge.innerHTML = '<span class="ad-dot dot-preroll"></span> Pre-Roll Spot möglich';
        playerAdBadge.title = adPolicy.tooltip;
        playerAdBadge.classList.remove('hidden');
      } else {
        playerAdBadge.className = 'player-ad-pill hidden';
      }
    }

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
          handlePlaybackError(station, error);
        });
    }

    renderCards(stationsList);
  }

  function togglePlayPause() {
    if (!currentStation) {
      if (stationsList.length > 0) {
        playStation(stationsList[0], 0);
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
    const list = stationsList;
    if (list.length === 0) return;
    let nextIdx = (currentStationIndex + 1) % list.length;
    playStation(list[nextIdx], nextIdx);
  }

  function playPrevStation() {
    const list = stationsList;
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
      handlePlaybackError(currentStation, e);
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

  // --- Event Handlers & Listeners ---
  function setupEventListeners() {
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
      } else if (genre === 'adfree') {
        loadAdFreeStations();
      } else {
        loadGenreStations(genre);
      }

      if (showFavoritesBtn) {
        if (genre === 'favorites') showFavoritesBtn.classList.add('is-active');
        else showFavoritesBtn.classList.remove('is-active');
      }
    });

    countrySelect.addEventListener('change', () => {
      if (searchInput.value.trim()) {
        performSearch(searchInput.value.trim());
      } else if (activeGenre !== 'featured' && activeGenre !== 'favorites') {
        if (activeGenre === 'topclick') loadTopStations();
        else if (activeGenre === 'adfree') loadAdFreeStations();
        else loadGenreStations(activeGenre);
      }
    });

    sortSelect.addEventListener('change', () => {
      if (searchInput.value.trim()) {
        performSearch(searchInput.value.trim());
      } else if (activeGenre !== 'featured' && activeGenre !== 'favorites') {
        if (activeGenre === 'topclick') loadTopStations();
        else if (activeGenre === 'adfree') loadAdFreeStations();
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
      if (moreDropdownMenu) moreDropdownMenu.classList.add('hidden');
    });

    if (moreMenuBtn && moreDropdownMenu) {
      moreMenuBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        moreDropdownMenu.classList.toggle('hidden');
        if (sleepTimerMenu) sleepTimerMenu.classList.add('hidden');
      });

      moreDropdownMenu.addEventListener('click', (e) => {
        e.stopPropagation();
        if (e.target.closest('.menu-item')) {
          moreDropdownMenu.classList.add('hidden');
        }
      });
    }

    document.addEventListener('click', () => {
      sleepTimerMenu.classList.add('hidden');
      if (moreDropdownMenu) moreDropdownMenu.classList.add('hidden');
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

    // Show Favorites Button Listener
    if (showFavoritesBtn) {
      showFavoritesBtn.addEventListener('click', toggleFavoritesView);
    }

    // Stream Links Manager Listeners
    if (updateLinksBtn) {
      updateLinksBtn.addEventListener('click', () => {
        updateStationLinks({ silent: false });
      });
    }

    if (linksStatusBadge) {
      linksStatusBadge.addEventListener('click', openLinksManagerModal);
    }

    if (triggerUpdateLinksBtn) {
      triggerUpdateLinksBtn.addEventListener('click', () => {
        updateStationLinks({ silent: false });
      });
    }

    if (closeLinksModalBtn) {
      closeLinksModalBtn.addEventListener('click', closeLinksManagerModal);
    }

    if (closeLinksModalBottomBtn) {
      closeLinksModalBottomBtn.addEventListener('click', closeLinksManagerModal);
    }

    if (updateLinksModal) {
      updateLinksModal.addEventListener('click', (e) => {
        if (e.target === updateLinksModal) closeLinksManagerModal();
      });
    }

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
      } else if (e.key === 'f' || e.key === 'F') {
        e.preventDefault();
        toggleFavoritesView();
      } else if (e.key === 'u' || e.key === 'U') {
        e.preventDefault();
        updateStationLinks({ silent: false });
      } else if (e.key === 'Escape') {
        if (customModal) customModal.classList.add('hidden');
        closeLinksManagerModal();
      }
    });
  }

  // --- Public API ---
  window.PulseCast = {
    showFavorites,
    toggleFavoritesView,
    updateStationLinks,
    openLinksManagerModal,
    closeLinksManagerModal,
    getCuratedStations: () => CURATED_STATIONS,
    getFavorites: () => favorites,
    getApiBase,
    rotateServer
  };

  // --- Run on Load ---
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
