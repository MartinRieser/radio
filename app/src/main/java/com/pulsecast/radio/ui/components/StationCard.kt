package com.pulsecast.radio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.pulsecast.radio.ui.theme.BgSurface
import com.pulsecast.radio.ui.theme.BgSurfaceCard
import com.pulsecast.radio.ui.theme.BorderSubtle
import com.pulsecast.radio.ui.theme.TextMuted
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary

@Composable
fun StationCard(
    station: Station,
    isActive: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    favoriteType: Int = 0,
    isFavorite: Boolean = favoriteType > 0,
    onCardClick: () -> Unit,
    onSetFavoriteType: (Int) -> Unit = {},
    onFavoriteClick: () -> Unit = {}
) {
    var showFavMenu by remember { mutableStateOf(false) }

    val border = if (isActive) {
        BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(AccentPrimary, Color(0xFF7000FF))))
    } else {
        BorderStroke(1.dp, BorderSubtle)
    }

    val cardBg = if (isActive) {
        Color(0xFF1A2438)
    } else {
        BgSurfaceCard
    }

    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = border,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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

            // Station Icon / Favicon with Play/Pause/Equalizer status
            Box(
                modifier = Modifier
                    .size(52.dp)
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
                        modifier = Modifier.size(52.dp),
                        error = fallbackPainter,
                        fallback = fallbackPainter
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = AccentPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                if (isActive && isPlaying) {
                    EqualizerIndicator()
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Station Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = station.name,
                    color = if (isActive) AccentPrimary else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${station.getCountryFlag()} ${station.country ?: "Global"}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )

                    val tags = station.tags?.split(",")?.take(2)?.joinToString(", ") { it.trim() }
                    if (!tags.isNullOrBlank()) {
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = tags,
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (station.bitrate in 1..320) {
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "${station.bitrate} kbps",
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Play / Stop Button: If stream is active show Stop button, otherwise show Play button
            val isStreamActive = isActive && (isPlaying || isBuffering)
            IconButton(
                onClick = onCardClick,
                modifier = Modifier.size(40.dp)
            ) {
                if (isActive && isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AccentPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isStreamActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isStreamActive) "Stoppen" else "Abspielen",
                        tint = if (isStreamActive || isActive) AccentPrimary else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            val effectiveFavType = if (favoriteType in 1..3) favoriteType else if (isFavorite) 1 else 0
            val (favIcon, favColor, favDesc) = when (effectiveFavType) {
                1 -> Triple(Icons.Default.Favorite, AccentFav, "Favorit Liste 1 (Herz)")
                2 -> Triple(Icons.Default.Diamond, Color(0xFF06B6D4), "Favorit Liste 2 (Diamant)")
                3 -> Triple(Icons.Default.Star, Color(0xFFFBBF24), "Favorit Liste 3 (Stern)")
                else -> Triple(Icons.Default.FavoriteBorder, TextMuted, "Zu Favoriten hinzufügen")
            }

            // Favorite Button with direct Favorite List Picker Popup
            Box {
                IconButton(
                    onClick = { showFavMenu = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = favIcon,
                        contentDescription = favDesc,
                        tint = favColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                FavoriteListPickerMenu(
                    expanded = showFavMenu,
                    currentFavType = effectiveFavType,
                    onDismiss = { showFavMenu = false },
                    onSelectType = { newType ->
                        showFavMenu = false
                        onSetFavoriteType(newType)
                    }
                )
            }
        }
    }
}

@Composable
fun FavoriteListPickerMenu(
    expanded: Boolean,
    currentFavType: Int,
    onDismiss: () -> Unit,
    onSelectType: (Int) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(BgSurface)
    ) {
        val options = listOf(
            Triple(1, "❤️ Favoriten 1 (Herz)", AccentFav),
            Triple(2, "💎 Favoriten 2 (Diamant)", Color(0xFF06B6D4)),
            Triple(3, "⭐ Favoriten 3 (Stern)", Color(0xFFFBBF24))
        )

        options.forEach { (type, label, color) ->
            val isSelected = currentFavType == type
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) color else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                },
                trailingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Aktiv",
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                onClick = {
                    // Tapping the already active list removes it; tapping another list assigns it
                    onSelectType(if (isSelected) 0 else type)
                }
            )
        }

        if (currentFavType > 0) {
            HorizontalDivider(color = BorderSubtle)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Aus Favoriten entfernen",
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = { onSelectType(0) }
            )
        }
    }
}

@Composable
private fun EqualizerIndicator() {
    val transition = rememberInfiniteTransition(label = "eq")
    val h1 by transition.animateFloat(
        initialValue = 4f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 14f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 6f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(20.dp)
        ) {
            Box(Modifier.width(3.dp).height(h1.dp).clip(CircleShape).background(AccentPrimary))
            Box(Modifier.width(3.dp).height(h2.dp).clip(CircleShape).background(AccentPrimary))
            Box(Modifier.width(3.dp).height(h3.dp).clip(CircleShape).background(AccentPrimary))
        }
    }
}
