package com.pulsecast.radio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.ui.theme.AccentFav
import com.pulsecast.radio.ui.theme.AccentPrimary
import com.pulsecast.radio.ui.theme.BgMain
import com.pulsecast.radio.ui.theme.BgSurface
import com.pulsecast.radio.ui.theme.BorderAccent
import com.pulsecast.radio.ui.theme.TextMuted
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary

@Composable
fun PlayerBar(
    station: Station?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    liveSeconds: Int,
    trackTitle: String,
    favoriteType: Int = 0,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSetFavoriteType: (Int) -> Unit = {}
) {
    var showFavMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = station != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        if (station == null) return@AnimatedVisibility

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = BgSurface,
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, BorderAccent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconUrl = remember(station.favicon, station.getStreamUrl()) {
                        val fav = station.favicon?.trim() ?: ""
                        if (fav.isNotBlank() && !fav.contains("favicon.ico")) {
                            if (fav.startsWith("http://")) fav.replace("http://", "https://") else fav
                        } else {
                            val streamUrl = station.getStreamUrl()
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

                    // Station Icon
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F1523)),
                        contentAlignment = Alignment.Center
                    ) {
                        val fallbackPainter = rememberVectorPainter(Icons.Default.Radio)
                        if (iconUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(iconUrl)
                                    .setHeader("User-Agent", "Mozilla/5.0")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = station.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(46.dp),
                                error = fallbackPainter,
                                fallback = fallbackPainter
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Station Info & Track Title / Playback Status
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = station.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        if (isPlaying && trackTitle.isNotBlank()) {
                            Text(
                                text = "🎵 $trackTitle",
                                color = AccentPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val dotColor = when {
                                    isBuffering -> Color(0xFFF59E0B)
                                    isPlaying -> Color(0xFF10B981)
                                    else -> TextMuted
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                val formattedTime = String.format(
                                    "%02d:%02d",
                                    (liveSeconds % 3600) / 60,
                                    liveSeconds % 60
                                )
                                val statusText = when {
                                    isBuffering -> "Verbinde…"
                                    isPlaying -> "LIVE • $formattedTime"
                                    else -> "Gestoppt"
                                }
                                Text(
                                    text = statusText,
                                    color = if (isBuffering) Color(0xFFF59E0B) else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Standard Player Controls: Favorite, Prev, Play/Pause, Next
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val (favIcon, favColor, favDesc) = when (favoriteType) {
                            1 -> Triple(Icons.Default.Favorite, AccentFav, "Favorit Liste 1 (Herz)")
                            2 -> Triple(Icons.Default.Diamond, Color(0xFF06B6D4), "Favorit Liste 2 (Diamant)")
                            3 -> Triple(Icons.Default.Star, Color(0xFFFBBF24), "Favorit Liste 3 (Stern)")
                            else -> Triple(Icons.Default.FavoriteBorder, TextSecondary, "Zu Favoriten hinzufügen")
                        }

                        Box {
                            IconButton(
                                onClick = { showFavMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = favIcon,
                                    contentDescription = favDesc,
                                    tint = favColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            FavoriteListPickerMenu(
                                expanded = showFavMenu,
                                currentFavType = favoriteType,
                                onDismiss = { showFavMenu = false },
                                onSelectType = { newType ->
                                    showFavMenu = false
                                    onSetFavoriteType(newType)
                                }
                            )
                        }

                        // Previous Station
                        IconButton(onClick = onPrevClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Vorheriger Sender",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play / Pause Button
                        Surface(
                            onClick = onPlayPauseClick,
                            shape = CircleShape,
                            color = AccentPrimary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = BgMain,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Stoppen" else "Abspielen",
                                        tint = BgMain,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        // Next Station
                        IconButton(onClick = onNextClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Nächster Sender",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
