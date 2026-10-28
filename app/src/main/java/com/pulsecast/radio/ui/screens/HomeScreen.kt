package com.pulsecast.radio.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.ui.components.AppUpdateDialog
import com.pulsecast.radio.ui.components.PlayerBar
import com.pulsecast.radio.ui.components.StationCard
import com.pulsecast.radio.ui.components.SleepTimerDialog
import com.pulsecast.radio.ui.theme.AccentPrimary
import com.pulsecast.radio.ui.theme.AccentSecondary
import com.pulsecast.radio.ui.theme.BgMain
import com.pulsecast.radio.ui.theme.BgSurfaceCard
import com.pulsecast.radio.ui.theme.BorderSubtle
import com.pulsecast.radio.ui.theme.TextMuted
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary
import com.pulsecast.radio.ui.viewmodel.MainViewModel
import com.pulsecast.radio.updater.AppUpdateState

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val activeCategory by viewModel.activeCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val favorites1 by viewModel.favorites1.collectAsState()
    val favorites2 by viewModel.favorites2.collectAsState()
    val favorites3 by viewModel.favorites3.collectAsState()
    val allFavorites by viewModel.allFavorites.collectAsState()
    val history by viewModel.history.collectAsState()

    val currentStation by viewModel.playbackController.currentStation.collectAsState()
    val isPlaying by viewModel.playbackController.isPlaying.collectAsState()
    val isBuffering by viewModel.playbackController.isBuffering.collectAsState()
    val liveSeconds by viewModel.playbackController.liveSeconds.collectAsState()
    val trackTitle by viewModel.playbackController.trackTitle.collectAsState()

    val sleepMinutes by viewModel.sleepMinutesRemaining.collectAsState()
    var showSleepDialog by remember { mutableStateOf(false) }

    val isCarModeActive by viewModel.isCarModeActive.collectAsState()
    val currentSpeedKmh by viewModel.currentSpeedKmh.collectAsState()
    val carConnectionReason by viewModel.carConnectionReason.collectAsState()

    val context = LocalContext.current
    val appUpdateState by viewModel.appUpdateState.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isCarModeActive) {
        CarDriveModeScreen(
            currentStation = currentStation,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            trackTitle = trackTitle,
            favorites = allFavorites,
            speedKmh = currentSpeedKmh,
            reason = carConnectionReason,
            onExitClick = { viewModel.exitCarMode() },
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onNextClick = { viewModel.playNext() },
            onPrevClick = { viewModel.playPrevious() },
            onSelectStation = { viewModel.playStation(it) }
        )
    } else {
        Scaffold(
            containerColor = BgMain,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                PlayerBar(
                    station = currentStation,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    liveSeconds = liveSeconds,
                    trackTitle = trackTitle,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.playNext() },
                    onPrevClick = { viewModel.playPrevious() }
                )
            }
        ) { paddingValues ->
            if (isLandscape) {
                // Landscape Layout: 2-Column Split View
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Left Column: Controls & Category Navigation
                    Column(
                        modifier = Modifier
                            .width(310.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp)
                    ) {
                        HeaderView(
                            sleepMinutes = sleepMinutes,
                            isCarModeActive = isCarModeActive,
                            onSleepClick = { showSleepDialog = true },
                            onCarModeClick = { viewModel.toggleCarMode() }
                        )

                        SearchAndFilterBar(
                            query = searchQuery,
                            activeCategory = activeCategory,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onClear = { viewModel.clearSearch() },
                            onSelectCategory = { viewModel.selectCategory(it) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        CategoryListLandscape(
                            activeCategory = activeCategory,
                            fav1Count = favorites1.size,
                            fav2Count = favorites2.size,
                            fav3Count = favorites3.size,
                            historyCount = history.size,
                            onSelectCategory = { viewModel.selectCategory(it) }
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = BorderSubtle,
                        thickness = 1.dp
                    )

                    // Right Column: Station List
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        SectionHeader(
                            activeCategory = activeCategory,
                            count = stations.size,
                            onClearHistory = { viewModel.clearHistory() }
                        )

                        StationListContent(
                            isLoading = isLoading,
                            stations = stations,
                            activeCategory = activeCategory,
                            searchQuery = searchQuery,
                            favorites1 = favorites1,
                            favorites2 = favorites2,
                            favorites3 = favorites3,
                            currentStation = currentStation,
                            isPlaying = isPlaying,
                            onPlayStation = { viewModel.playStation(it) },
                            onToggleFavorite = { viewModel.cycleFavorite(it) }
                        )
                    }
                }
            } else {
                // Portrait Layout: Single Column View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 1. App Header
                    HeaderView(
                        sleepMinutes = sleepMinutes,
                        isCarModeActive = isCarModeActive,
                        onSleepClick = { showSleepDialog = true },
                        onCarModeClick = { viewModel.toggleCarMode() }
                    )

                    // 2. Search & Genre Filter Bar
                    SearchAndFilterBar(
                        query = searchQuery,
                        activeCategory = activeCategory,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onClear = { viewModel.clearSearch() },
                        onSelectCategory = { viewModel.selectCategory(it) }
                    )

                    // 3. Category Tabs
                    CategoryTabs(
                        activeCategory = activeCategory,
                        fav1Count = favorites1.size,
                        fav2Count = favorites2.size,
                        fav3Count = favorites3.size,
                        historyCount = history.size,
                        onSelectCategory = { viewModel.selectCategory(it) }
                    )

                    // 4. Section Header
                    SectionHeader(
                        activeCategory = activeCategory,
                        count = stations.size,
                        onClearHistory = { viewModel.clearHistory() }
                    )

                    // 5. Stations List
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        StationListContent(
                            isLoading = isLoading,
                            stations = stations,
                            activeCategory = activeCategory,
                            searchQuery = searchQuery,
                            favorites1 = favorites1,
                            favorites2 = favorites2,
                            favorites3 = favorites3,
                            currentStation = currentStation,
                            isPlaying = isPlaying,
                            onPlayStation = { viewModel.playStation(it) },
                            onToggleFavorite = { viewModel.cycleFavorite(it) }
                        )
                    }
                }
            }
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            currentMinutesRemaining = sleepMinutes,
            onDismiss = { showSleepDialog = false },
            onSelectMinutes = { viewModel.setSleepTimer(it) }
        )
    }

    if (appUpdateState is AppUpdateState.Available || appUpdateState is AppUpdateState.Downloading) {
        AppUpdateDialog(
            state = appUpdateState,
            onUpdateClick = { url ->
                val activity = context as? Activity
                if (activity != null) {
                    viewModel.startAppUpdate(activity, url)
                }
            },
            onDismiss = { viewModel.dismissAppUpdate() }
        )
    }
}

@Composable
private fun HeaderView(
    sleepMinutes: Int,
    isCarModeActive: Boolean,
    onSleepClick: () -> Unit,
    onCarModeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Branding
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AccentPrimary, AccentSecondary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = BgMain,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "PulseCast",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Live Radio & Streams",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Action Buttons: Car Mode & Sleep Timer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Car Drive Mode Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isCarModeActive) Color(0xFF10B981).copy(alpha = 0.25f) else BgSurfaceCard)
                    .clickable(onClick = onCarModeClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Automodus",
                        tint = if (isCarModeActive) Color(0xFF10B981) else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Auto",
                        color = if (isCarModeActive) Color(0xFF10B981) else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sleep Timer Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sleepMinutes > 0) AccentPrimary.copy(alpha = 0.2f) else BgSurfaceCard)
                    .clickable(onClick = onSleepClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepMinutes > 0) AccentPrimary else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    if (sleepMinutes > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${sleepMinutes}m",
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterBar(
    query: String,
    activeCategory: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val genres = listOf(
        "pop" to "Pop & Hits",
        "rock" to "Rock & Metal",
        "electronic" to "Electronic & Dance",
        "chillout" to "Chillout & Ambient",
        "jazz" to "Jazz & Blues",
        "classical" to "Klassik & Mosaik",
        "hiphop" to "Hip-Hop & Rap",
        "alternative" to "Alternative & Indie",
        "retro" to "80s & 90s Oldies"
    )

    var genreMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search Box
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Sender oder Genre suchen…",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Löschen",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BgSurfaceCard,
                unfocusedContainerColor = BgSurfaceCard,
                disabledContainerColor = BgSurfaceCard,
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.weight(1f)
        )

        // Genre Filter Dropdown Button
        Box {
            Surface(
                onClick = { genreMenuExpanded = true },
                shape = RoundedCornerShape(14.dp),
                color = BgSurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Genre Filter",
                        tint = AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Genre",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            DropdownMenu(
                expanded = genreMenuExpanded,
                onDismissRequest = { genreMenuExpanded = false },
                modifier = Modifier.background(BgSurfaceCard)
            ) {
                genres.forEach { (tag, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = if (activeCategory == tag) AccentPrimary else TextPrimary,
                                fontWeight = if (activeCategory == tag) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            genreMenuExpanded = false
                            onSelectCategory(tag)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    activeCategory: String,
    fav1Count: Int,
    fav2Count: Int,
    fav3Count: Int,
    historyCount: Int,
    onSelectCategory: (String) -> Unit
) {
    val tabs = listOf(
        "featured" to "📻 Entdecken",
        "favorites_1" to "❤️ Favoriten ($fav1Count)",
        "favorites_2" to "💎 Diamant ($fav2Count)",
        "favorites_3" to "⭐ Stern ($fav3Count)",
        "adfree" to "🛡️ Werbefrei",
        "history" to "🕒 Verlauf ($historyCount)"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    ) {
        items(tabs) { (id, label) ->
            val isSelected = activeCategory == id || (id == "favorites_1" && activeCategory == "favorites")
            Surface(
                onClick = { onSelectCategory(id) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) AccentPrimary else BgSurfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) AccentPrimary else BorderSubtle
                )
            ) {
                Text(
                    text = label,
                    color = if (isSelected) BgMain else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryListLandscape(
    activeCategory: String,
    fav1Count: Int,
    fav2Count: Int,
    fav3Count: Int,
    historyCount: Int,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf(
        "featured" to "📻 Entdecken",
        "favorites_1" to "❤️ Favoriten ($fav1Count)",
        "favorites_2" to "💎 Diamant ($fav2Count)",
        "favorites_3" to "⭐ Stern ($fav3Count)",
        "adfree" to "🛡️ Werbefrei",
        "history" to "🕒 Verlauf ($historyCount)",
        "pop" to "🎵 Pop & Hits",
        "rock" to "🎸 Rock & Metal",
        "electronic" to "⚡ Electronic & Dance",
        "chillout" to "🌙 Chillout & Ambient",
        "alternative" to "🎷 Alternative & Indie"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { (id, label) ->
            val isSelected = activeCategory == id || (id == "favorites_1" && activeCategory == "favorites")
            Surface(
                onClick = { onSelectCategory(id) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) AccentPrimary.copy(alpha = 0.2f) else BgSurfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) AccentPrimary else BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    color = if (isSelected) AccentPrimary else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    activeCategory: String,
    count: Int,
    onClearHistory: () -> Unit
) {
    val title = when (activeCategory) {
        "featured" -> "📻 Entdecken"
        "favorites", "favorites_1" -> "❤️ Favoriten (Liste 1)"
        "favorites_2" -> "💎 Diamant-Favoriten (Liste 2)"
        "favorites_3" -> "⭐ Stern-Favoriten (Liste 3)"
        "adfree" -> "🛡️ Werbefreie Sender"
        "history" -> "🕒 Hörverlauf"
        "search" -> "🔍 Suchergebnisse"
        else -> "🏷️ $activeCategory"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceCard
            ) {
                Text(
                    text = "$count",
                    color = AccentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (activeCategory == "history" && count > 0) {
            IconButton(onClick = onClearHistory, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Verlauf leeren",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StationListContent(
    isLoading: Boolean,
    stations: List<Station>,
    activeCategory: String,
    searchQuery: String,
    favorites1: List<Station>,
    favorites2: List<Station>,
    favorites3: List<Station>,
    currentStation: Station?,
    isPlaying: Boolean,
    onPlayStation: (Station) -> Unit,
    onToggleFavorite: (Station) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AccentPrimary)
        }
    } else if (stations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val emptyMessage = when {
                searchQuery.isNotBlank() -> "Keine Sender für „$searchQuery“ gefunden."
                activeCategory in listOf("favorites", "favorites_1") -> "Noch keine Favoriten in der ❤️ Herz-Liste gespeichert.\nTippe 1x auf das Herz bei einem Sender!"
                activeCategory == "favorites_2" -> "Noch keine Favoriten in der 💎 Diamant-Liste gespeichert.\nTippe 2x auf das Symbol bei einem Sender!"
                activeCategory == "favorites_3" -> "Noch keine Favoriten in der ⭐ Stern-Liste gespeichert.\nTippe 3x auf das Symbol bei einem Sender!"
                activeCategory == "history" -> "Dein Verlauf ist leer.\nHöre mindestens 1 Minute einen Sender!"
                else -> "Keine Sender in dieser Kategorie gefunden."
            }
            Text(
                text = emptyMessage,
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(24.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(
                items = stations,
                key = { it.stationuuid ?: it.getStreamUrl() }
            ) { station ->
                val favType = remember(station, favorites1, favorites2, favorites3) {
                    val key = station.stationuuid?.takeIf { it.isNotBlank() } ?: station.getStreamUrl()
                    when {
                        favorites1.any { (it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl()) == key } -> 1
                        favorites2.any { (it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl()) == key } -> 2
                        favorites3.any { (it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl()) == key } -> 3
                        else -> 0
                    }
                }
                val isCurrent = currentStation?.stationuuid == station.stationuuid || (currentStation != null && currentStation.getStreamUrl() == station.getStreamUrl())

                StationCard(
                    station = station,
                    isActive = isCurrent,
                    isPlaying = isPlaying,
                    favoriteType = favType,
                    isHistoryView = activeCategory == "history",
                    onCardClick = { onPlayStation(station) },
                    onFavoriteClick = { onToggleFavorite(station) }
                )
            }
        }
    }
}
