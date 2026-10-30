package com.pulsecast.radio.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.ui.components.AppUpdateDialog
import com.pulsecast.radio.ui.components.PlayerBar
import com.pulsecast.radio.ui.components.StationCard
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

    val favorites1 by viewModel.favorites1.collectAsState()
    val favorites2 by viewModel.favorites2.collectAsState()
    val favorites3 by viewModel.favorites3.collectAsState()

    val currentStation by viewModel.playbackController.currentStation.collectAsState()
    val isPlaying by viewModel.playbackController.isPlaying.collectAsState()
    val isBuffering by viewModel.playbackController.isBuffering.collectAsState()
    val liveSeconds by viewModel.playbackController.liveSeconds.collectAsState()
    val trackTitle by viewModel.playbackController.trackTitle.collectAsState()

    val isCarModeActive by viewModel.isCarModeActive.collectAsState()
    val currentSpeedKmh by viewModel.currentSpeedKmh.collectAsState()
    val carConnectionReason by viewModel.carConnectionReason.collectAsState()

    val context = LocalContext.current
    val appUpdateState by viewModel.appUpdateState.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentStationFavType = remember(currentStation, favorites1, favorites2, favorites3) {
        val st = currentStation ?: return@remember 0
        when {
            favorites1.any { it.isSameStation(st) } -> 1
            favorites2.any { it.isSameStation(st) } -> 2
            favorites3.any { it.isSameStation(st) } -> 3
            else -> 0
        }
    }

    if (isCarModeActive) {
        CarDriveModeScreen(
            currentStation = currentStation,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            trackTitle = trackTitle,
            activeCategory = activeCategory,
            favorites1 = favorites1,
            favorites2 = favorites2,
            favorites3 = favorites3,
            activeStations = stations,
            speedKmh = currentSpeedKmh,
            reason = carConnectionReason,
            onExitClick = { viewModel.exitCarMode() },
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onNextClick = { viewModel.playNext() },
            onPrevClick = { viewModel.playPrevious() },
            onSelectCategory = { viewModel.selectCategory(it) },
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
                    favoriteType = currentStationFavType,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.playNext() },
                    onPrevClick = { viewModel.playPrevious() },
                    onSetFavoriteType = { newType ->
                        currentStation?.let { viewModel.setFavoriteType(it, newType) }
                    }
                )
            }
        ) { paddingValues ->
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Column(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp)
                    ) {
                        HeaderView(
                            isCarModeActive = isCarModeActive,
                            onCarModeClick = { viewModel.toggleCarMode() }
                        )

                        SearchBarView(
                            query = searchQuery,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onClear = { viewModel.clearSearch() }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        CategoryListLandscape(
                            activeCategory = activeCategory,
                            fav1Count = favorites1.size,
                            fav2Count = favorites2.size,
                            fav3Count = favorites3.size,
                            onSelectCategory = { viewModel.selectCategory(it) }
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = BorderSubtle,
                        thickness = 1.dp
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        SectionHeader(
                            activeCategory = activeCategory,
                            count = stations.size
                        )

                        StationListContent(
                            isLoading = isLoading,
                            stations = stations,
                            favorites1 = favorites1,
                            favorites2 = favorites2,
                            favorites3 = favorites3,
                            activeCategory = activeCategory,
                            searchQuery = searchQuery,
                            currentStation = currentStation,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onStationClick = { viewModel.onStationCardClick(it) },
                            onSetFavoriteType = { station, type -> viewModel.setFavoriteType(station, type) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 1. App Header
                    HeaderView(
                        isCarModeActive = isCarModeActive,
                        onCarModeClick = { viewModel.toggleCarMode() }
                    )

                    // 2. Search Bar
                    SearchBarView(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onClear = { viewModel.clearSearch() }
                    )

                    // 3. Scrollable Category & Genre Chips
                    CategoryTabs(
                        activeCategory = activeCategory,
                        fav1Count = favorites1.size,
                        fav2Count = favorites2.size,
                        fav3Count = favorites3.size,
                        onSelectCategory = { viewModel.selectCategory(it) }
                    )

                    // 4. Section Header
                    SectionHeader(
                        activeCategory = activeCategory,
                        count = stations.size
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
                            favorites1 = favorites1,
                            favorites2 = favorites2,
                            favorites3 = favorites3,
                            activeCategory = activeCategory,
                            searchQuery = searchQuery,
                            currentStation = currentStation,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onStationClick = { viewModel.onStationCardClick(it) },
                            onSetFavoriteType = { station, type -> viewModel.setFavoriteType(station, type) }
                        )
                    }
                }
            }
        }
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
    isCarModeActive: Boolean,
    onCarModeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
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
                    text = "Live Radio",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Surface(
            onClick = onCarModeClick,
            shape = RoundedCornerShape(20.dp),
            color = if (isCarModeActive) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.18f),
            border = BorderStroke(1.dp, if (isCarModeActive) Color(0xFF34D399) else Color(0xFF059669))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "Automodus",
                    tint = if (isCarModeActive) BgMain else Color(0xFF34D399),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Automodus",
                    color = if (isCarModeActive) BgMain else Color(0xFF34D399),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SearchBarView(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Sender, Land oder Genre suchen…",
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
                        contentDescription = "Suche löschen",
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

private fun buildCategoryList(fav1Count: Int, fav2Count: Int, fav3Count: Int): List<Pair<String, String>> {
    return listOf(
        "featured" to "📻 Entdecken",
        "favorites_1" to "❤️ Herz ($fav1Count)",
        "favorites_2" to "💎 Diamant ($fav2Count)",
        "favorites_3" to "⭐ Stern ($fav3Count)",
        "pop" to "🎵 Pop & Hits",
        "rock" to "🎸 Rock & Metal",
        "electronic" to "⚡ Dance & Electronic",
        "chillout" to "🌙 Chillout",
        "jazz" to "🎷 Jazz & Blues",
        "classical" to "🎻 Klassik",
        "hiphop" to "🎤 Hip-Hop",
        "alternative" to "🌿 Indie",
        "retro" to "🕰️ 80s & 90s"
    )
}

@Composable
private fun CategoryTabs(
    activeCategory: String,
    fav1Count: Int,
    fav2Count: Int,
    fav3Count: Int,
    onSelectCategory: (String) -> Unit
) {
    val tabs = remember(fav1Count, fav2Count, fav3Count) {
        buildCategoryList(fav1Count, fav2Count, fav3Count)
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(tabs, key = { it.first }) { (id, label) ->
            val isSelected = activeCategory == id || (id == "favorites_1" && activeCategory == "favorites")
            Surface(
                onClick = { onSelectCategory(id) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) AccentPrimary else BgSurfaceCard,
                border = BorderStroke(
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
    onSelectCategory: (String) -> Unit
) {
    val categories = remember(fav1Count, fav2Count, fav3Count) {
        buildCategoryList(fav1Count, fav2Count, fav3Count)
    }

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
                border = BorderStroke(
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
    count: Int
) {
    val title = when (activeCategory) {
        "featured" -> "📻 Entdecken"
        "favorites", "favorites_1" -> "❤️ Favoriten 1 (Herz)"
        "favorites_2" -> "💎 Favoriten 2 (Diamant)"
        "favorites_3" -> "⭐ Favoriten 3 (Stern)"
        "search" -> "🔍 Suchergebnisse"
        "pop" -> "🎵 Pop & Hits"
        "rock" -> "🎸 Rock & Metal"
        "electronic" -> "⚡ Dance & Electronic"
        "chillout" -> "🌙 Chillout & Ambient"
        "jazz" -> "🎷 Jazz & Blues"
        "classical" -> "🎻 Klassik"
        "hiphop" -> "🎤 Hip-Hop & Rap"
        "alternative" -> "🌿 Alternative & Indie"
        "retro" -> "🕰️ 80s & 90s Oldies"
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
    }
}

@Composable
private fun StationListContent(
    isLoading: Boolean,
    stations: List<Station>,
    favorites1: List<Station>,
    favorites2: List<Station>,
    favorites3: List<Station>,
    activeCategory: String,
    searchQuery: String,
    currentStation: Station?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onStationClick: (Station) -> Unit,
    onSetFavoriteType: (Station, Int) -> Unit
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
                activeCategory in listOf("favorites", "favorites_1") ->
                    "Noch keine Sender in „❤️ Herz“ gespeichert.\nTippe bei einem Sender rechts auf das Herz-Symbol und wähle „❤️ Favoriten 1“."
                activeCategory == "favorites_2" ->
                    "Noch keine Sender in „💎 Diamant“ gespeichert.\nTippe bei einem Sender rechts auf das Herz-Symbol und wähle „💎 Favoriten 2“."
                activeCategory == "favorites_3" ->
                    "Noch keine Sender in „⭐ Stern“ gespeichert.\nTippe bei einem Sender rechts auf das Herz-Symbol und wähle „⭐ Favoriten 3“."
                else -> "Keine Sender in dieser Kategorie gefunden."
            }
            Text(
                text = emptyMessage,
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(
                items = stations,
                key = { it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl() }
            ) { station ->
                val favType = remember(station, favorites1, favorites2, favorites3) {
                    when {
                        favorites1.any { it.isSameStation(station) } -> 1
                        favorites2.any { it.isSameStation(station) } -> 2
                        favorites3.any { it.isSameStation(station) } -> 3
                        else -> 0
                    }
                }
                val isCurrent = station.isSameStation(currentStation)

                StationCard(
                    station = station,
                    isActive = isCurrent,
                    isPlaying = isPlaying,
                    isBuffering = isCurrent && isBuffering,
                    favoriteType = favType,
                    onCardClick = { onStationClick(station) },
                    onSetFavoriteType = { newType -> onSetFavoriteType(station, newType) }
                )
            }
        }
    }
}
