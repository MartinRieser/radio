package com.pulsecast.radio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.pulsecast.radio.data.HistoryAndFavoritesRepository
import com.pulsecast.radio.model.Station
import com.pulsecast.radio.ui.theme.AccentFav
import com.pulsecast.radio.ui.theme.AccentHistory
import com.pulsecast.radio.ui.theme.AccentPrimary
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
    favoriteType: Int = 0,
    isFavorite: Boolean = favoriteType > 0,
    isHistoryView: Boolean = false,
    onCardClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val border = if (isActive) {
        BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(AccentPrimary, Color(0xFF7000FF))))
    } else {
        BorderStroke(1.dp, BorderSubtle)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(containerColor = BgSurfaceCard),
        border = border,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconUrl = androidx.compose.runtime.remember(station.favicon, station.getStreamUrl()) {
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

            // Station Icon / Favicon
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
                    // Mini Equalizer Indicator
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
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Country & Flag
                    Text(
                        text = "${station.getCountryFlag()} ${station.country ?: "Global"}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )

                    // Genre Tags
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

                    // Bitrate (if > 0 and <= 320)
                    if (station.bitrate in 1..320) {
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "${station.bitrate} kbps",
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    // History Timestamp (only in History View)
                    if (isHistoryView && station.lastPlayed != null) {
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = HistoryAndFavoritesRepository.formatRelativeTime(station.lastPlayed),
                            color = AccentHistory,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val effectiveFavType = if (favoriteType in 1..3) favoriteType else if (isFavorite) 1 else 0
            val (favIcon, favColor, favDesc) = when (effectiveFavType) {
                1 -> Triple(Icons.Default.Favorite, AccentFav, "Favorit Liste 1 (Herz) - Tippen für Liste 2")
                2 -> Triple(Icons.Default.Diamond, Color(0xFF06B6D4), "Favorit Liste 2 (Diamant) - Tippen für Liste 3")
                3 -> Triple(Icons.Default.Star, Color(0xFFFBBF24), "Favorit Liste 3 (Stern) - Tippen zum Entfernen")
                else -> Triple(Icons.Default.FavoriteBorder, TextMuted, "Zu Favoriten hinzufügen")
            }

            // Favorite Button (1x Herz -> 2x Diamant -> 3x Stern -> Entfernen)
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = favIcon,
                    contentDescription = favDesc,
                    tint = favColor,
                    modifier = Modifier.size(20.dp)
                )
            }
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
