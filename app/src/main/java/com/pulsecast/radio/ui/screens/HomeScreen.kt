package com.pulsecast.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsecast.radio.ui.components.PlayerBar
import com.pulsecast.radio.ui.components.SleepTimerDialog
import com.pulsecast.radio.ui.components.StationCard
import com.pulsecast.radio.ui.theme.AccentFav
import com.pulsecast.radio.ui.theme.AccentHistory
import com.pulsecast.radio.ui.theme.AccentPrimary
import com.pulsecast.radio.ui.theme.AccentSecondary
import com.pulsecast.radio.ui.theme.BgMain
import com.pulsecast.radio.ui.theme.BgSurface
import com.pulsecast.radio.ui.theme.BgSurfaceCard
import com.pulsecast.radio.ui.theme.BorderSubtle
import com.pulsecast.radio.ui.theme.TextMuted
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary
import com.pulsecast.radio.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val activeCategory by viewModel.activeCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()

    val currentStation by viewModel.playbackController.currentStation.collectAsState()
    val isPlaying by viewModel.playbackController.isPlaying.collectAsState()
    val isBuffering by viewModel.playbackController.isBuffering.collectAsState()
    val liveSeconds by viewModel.playbackController.liveSeconds.collectAsState()
    val trackTitle by viewModel.playbackController.trackTitle.collectAsState()

    val sleepMinutes by viewModel.sleepMinutesRemaining.collectAsState()
    var showSleepDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgMain,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. App Header
            HeaderView(
                sleepMinutes = sleepMinutes,
                onSleepClick = { showSleepDialog = true }
            )

            // 2. Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClear = { viewModel.clearSearch() }
            )

            // 3. Category Pills (Horizontal Scroll)
            CategoryPills(
                activeCategory = activeCategory,
                favCount = favorites.size,
                historyCount = history.size,
                onSelectCategory = { viewModel.selectCategory(it) }
            )

            // 4. Section Header
            SectionHeader(
                activeCategory = activeCategory,
                count = stations.size,
                onClearHistory = { viewModel.clearHistory() }
            )

            // 5. Stations List / Empty State / Loading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentPrimary
                    )
                } else if (stations.isEmpty()) {
                    EmptyStateView(
                        category = activeCategory,
                        query = searchQuery
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(stations, key = { it.stationuuid ?: it.getStreamUrl() }) { station ->
                            val isActive = currentStation?.stationuuid == station.stationuuid ||
                                    currentStation?.getStreamUrl() == station.getStreamUrl()
                            val isFav = favorites.any { it.stationuuid == station.stationuuid || it.getStreamUrl() == station.getStreamUrl() }

                            StationCard(
                                station = station,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                isFavorite = isFav,
                                isHistoryView = (activeCategory == "history"),
                                onCardClick = { viewModel.playStation(station) },
                                onFavoriteClick = { viewModel.toggleFavorite(station) }
                            )
                        }
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
}

@Composable
private fun HeaderView(
    sleepMinutes: Int,
    onSleepClick: () -> Unit
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

        // Sleep Timer Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (sleepMinutes > 0) AccentPrimary.copy(alpha = 0.2f) else BgSurfaceCard)
                .clickable(onClick = onSleepClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepMinutes > 0) AccentPrimary else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                if (sleepMinutes > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${sleepMinutes}m",
                        color = AccentPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("Sender, Genre oder Land suchen…", color = TextMuted, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Löschen", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BgSurfaceCard,
                unfocusedContainerColor = BgSurfaceCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun CategoryPills(
    activeCategory: String,
    favCount: Int,
    historyCount: Int,
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
        "news" to "Nachrichten & Talk",
        "country" to "Country & Folk",
        "reggae" to "Reggae & Dub",
        "80s" to "80er & Oldies",
        "latin" to "Latin & Salsa",
        "lounge" to "Lounge & Easy Listening"
    )

    val fixedCategories = listOf(
        "featured" to "Featured",
        "adfree" to "🛡️ Werbefrei",
        "favorites" to "❤️ Favoriten ($favCount)",
        "history" to "🕒 Verlauf ($historyCount)",
        "topclick" to "Trending"
    )

    var genreMenuExpanded by remember { mutableStateOf(false) }

    val selectedGenrePair = genres.find { it.first == activeCategory }
    val isGenreActive = (selectedGenrePair != null)
    val genreButtonText = if (selectedGenrePair != null) "🎵 ${selectedGenrePair.second}" else "🎵 Genre wählen"

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(fixedCategories) { (id, label) ->
            val isSelected = (activeCategory == id)
            val pillColor = when {
                isSelected -> AccentPrimary
                id == "favorites" -> AccentFav
                id == "history" -> AccentHistory
                else -> TextSecondary
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) AccentPrimary.copy(alpha = 0.15f)
                        else BgSurfaceCard
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) AccentPrimary else BorderSubtle,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelectCategory(id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) TextPrimary else pillColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // Genre Dropdown Selector Button
        item {
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isGenreActive) AccentPrimary.copy(alpha = 0.2f)
                            else BgSurfaceCard
                        )
                        .border(
                            width = 1.dp,
                            color = if (isGenreActive) AccentPrimary else BorderSubtle,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { genreMenuExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = genreButtonText,
                            color = if (isGenreActive) TextPrimary else AccentPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isGenreActive) FontWeight.Bold else FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Genre Dropdown",
                            tint = if (isGenreActive) AccentPrimary else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = genreMenuExpanded,
                    onDismissRequest = { genreMenuExpanded = false },
                    modifier = Modifier
                        .background(BgSurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    genres.forEach { (genreId, genreName) ->
                        val isCurrentGenre = (activeCategory == genreId)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = genreName,
                                    color = if (isCurrentGenre) AccentPrimary else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrentGenre) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                genreMenuExpanded = false
                                onSelectCategory(genreId)
                            }
                        )
                    }
                }
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
    val genresMap = mapOf(
        "pop" to "Pop & Hits",
        "rock" to "Rock & Metal",
        "electronic" to "Electronic & Dance",
        "chillout" to "Chillout & Ambient",
        "jazz" to "Jazz & Blues",
        "classical" to "Klassik & Mosaik",
        "hiphop" to "Hip-Hop & Rap",
        "alternative" to "Alternative & Indie",
        "news" to "Nachrichten & Talk",
        "country" to "Country & Folk",
        "reggae" to "Reggae & Dub",
        "80s" to "80er & Oldies",
        "latin" to "Latin & Salsa",
        "lounge" to "Lounge & Easy Listening"
    )

    val title = when (activeCategory) {
        "featured" -> "Featured Sender"
        "adfree" -> "Werbefreie Sender"
        "favorites" -> "Gespeicherte Favoriten"
        "history" -> "Zuletzt gespielt (Verlauf)"
        "topclick" -> "Trending Streams"
        else -> genresMap[activeCategory]?.let { "Genre: $it" }
            ?: "${activeCategory.replaceFirstChar { it.uppercase() }} Sender"
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
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgSurfaceCard)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Clear history button
        if (activeCategory == "history" && count > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClearHistory)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Verlauf leeren",
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Verlauf leeren",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    category: String,
    query: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val icon = when (category) {
            "favorites" -> Icons.Default.Favorite
            "history" -> Icons.Default.History
            else -> Icons.Default.Radio
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(54.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        val title = when {
            query.isNotBlank() -> "Keine Sender gefunden"
            category == "favorites" -> "Noch keine Favoriten"
            category == "history" -> "Kein Verlauf vorhanden"
            else -> "Keine Sender verfügbar"
        }

        val desc = when {
            query.isNotBlank() -> "Versuche einen anderen Suchbegriff oder ein anderes Genre."
            category == "favorites" -> "Tippe auf das Herz-Symbol bei einem Sender, um ihn hier zu speichern."
            category == "history" -> "Radiosender werden hier automatisch gespeichert, sobald du sie mindestens 1 Minute lang hörst."
            else -> "Bitte prüfe deine Internetverbindung."
        }

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = desc,
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
