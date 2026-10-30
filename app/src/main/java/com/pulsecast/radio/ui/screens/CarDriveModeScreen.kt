package com.pulsecast.radio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.ui.theme.AccentPrimary
import com.pulsecast.radio.ui.theme.BgMain
import com.pulsecast.radio.ui.theme.BgSurface
import com.pulsecast.radio.ui.theme.BorderAccent
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary

@Composable
fun CarDriveModeScreen(
    currentStation: Station?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    trackTitle: String,
    activeCategory: String,
    favorites1: List<Station>,
    favorites2: List<Station>,
    favorites3: List<Station>,
    activeStations: List<Station> = emptyList(),
    speedKmh: Float,
    reason: String,
    onExitClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectStation: (Station) -> Unit
) {
    val listLabel = when (activeCategory) {
        "favorites", "favorites_1" -> "❤️ Favoriten 1 (Herz)"
        "favorites_2" -> "💎 Favoriten 2 (Diamant)"
        "favorites_3" -> "⭐ Favoriten 3 (Stern)"
        "featured" -> "📻 Entdecken"
        else -> "📻 Aktive Liste ($activeCategory)"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Header: Mode & App-Ansicht Return Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Automodus",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (speedKmh > 0) "${speedKmh.toInt()} km/h • $reason" else reason,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // App-Ansicht Return Button
                Surface(
                    onClick = onExitClick,
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF222B3E),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zur App-Ansicht",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "App-Ansicht",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Center Station Card & Live Radio Text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Station Artwork
                Surface(
                    modifier = Modifier.size(110.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = BgSurface,
                    border = BorderStroke(2.dp, BorderAccent),
                    shadowElevation = 16.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val fallbackPainter = rememberVectorPainter(Icons.Default.Radio)
                        val iconUrl = remember(currentStation?.favicon, currentStation?.getStreamUrl()) {
                            val fav = currentStation?.favicon?.trim() ?: ""
                            if (fav.isNotBlank() && !fav.contains("favicon.ico")) {
                                if (fav.startsWith("http://")) fav.replace("http://", "https://") else fav
                            } else {
                                val streamUrl = currentStation?.getStreamUrl() ?: ""
                                if (streamUrl.contains("//")) {
                                    val host = streamUrl.split("//")[1].split("/")[0]
                                    val parts = host.split(".")
                                    if (parts.size >= 2) {
                                        val domain = parts.takeLast(2).joinToString(".")
                                        "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                                    } else ""
                                } else ""
                            }
                        }

                        if (iconUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(iconUrl)
                                    .setHeader("User-Agent", "Mozilla/5.0")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = currentStation?.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = fallbackPainter,
                                fallback = fallbackPainter
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Station Name
                Text(
                    text = currentStation?.name ?: "Kein Sender gewählt",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Live Radio Text
                Text(
                    text = if (trackTitle.isNotBlank()) "🎵 $trackTitle" else "📻 ${currentStation?.country ?: "Live Radio"}",
                    color = AccentPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Live Playback Status Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPlaying) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF1E293B),
                    border = BorderStroke(1.dp, if (isPlaying) Color(0xFF34D399) else Color(0xFF334155))
                ) {
                    val statusLabel = when {
                        isBuffering -> "⏳ Verbinde…"
                        isPlaying -> "🟢 Live Wiedergabe"
                        else -> "⚪ Gestoppt"
                    }
                    Text(
                        text = statusLabel,
                        color = if (isPlaying) Color(0xFF6EE7B7) else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // 3. Giant Controls (Prev, Giant Play/Pause, Next)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Giant Prev Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .clickable(onClick = onPrevClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Vorheriger Sender",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Giant Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = BgMain,
                            strokeWidth = 4.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stoppen" else "Abspielen",
                            tint = BgMain,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                // Giant Next Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .clickable(onClick = onNextClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Weiter",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // 4. Synchronized List Selector & Quick Carousel
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("featured", "📻 Alle", true),
                        Triple("favorites_1", "❤️ (${favorites1.size})", favorites1.isNotEmpty()),
                        Triple("favorites_2", "💎 (${favorites2.size})", favorites2.isNotEmpty()),
                        Triple("favorites_3", "⭐ (${favorites3.size})", favorites3.isNotEmpty())
                    ).forEach { (catId, label, hasItems) ->
                        val isSelected = activeCategory == catId || (catId == "favorites_1" && activeCategory == "favorites")
                        Surface(
                            onClick = {
                                val targetCategory = if (isSelected && catId != "featured") "featured" else catId
                                onSelectCategory(targetCategory)
                                val targetList = when (targetCategory) {
                                    "favorites_1" -> favorites1
                                    "favorites_2" -> favorites2
                                    "favorites_3" -> favorites3
                                    else -> emptyList()
                                }
                                if (targetList.isNotEmpty() && targetList.none { it.isSameStation(currentStation) }) {
                                    onSelectStation(targetList.first())
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) AccentPrimary else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AccentPrimary else Color(0xFF334155)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                color = when {
                                    isSelected -> BgMain
                                    hasItems -> TextPrimary
                                    else -> TextSecondary.copy(alpha = 0.5f)
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Carousel of active station list
                if (activeStations.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "$listLabel (${activeStations.size})",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = activeStations,
                                key = { it.stationuuid?.takeIf { u -> u.isNotBlank() } ?: it.getStreamUrl() }
                            ) { station ->
                                val isSelected = station.isSameStation(currentStation)
                                Surface(
                                    onClick = { onSelectStation(station) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) AccentPrimary.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) AccentPrimary else Color(0xFF334155)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = station.name,
                                            color = if (isSelected) AccentPrimary else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Diese Favoriten-Liste ist noch leer.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
