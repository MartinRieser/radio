package com.pulsecast.radio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pulsecast.radio.ui.theme.AccentPrimary
import com.pulsecast.radio.ui.theme.BgMain
import com.pulsecast.radio.ui.theme.BgSurfaceCard
import com.pulsecast.radio.ui.theme.BorderSubtle
import com.pulsecast.radio.ui.theme.TextMuted
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary

@Composable
fun SmartSwitchDialog(
    isEnabled: Boolean,
    toleranceSeconds: Int,
    speechSensitivity: Float,
    speechResetHoldSeconds: Int,
    smartSwitchStrategy: String,
    miningMode: String,
    syncState: com.pulsecast.radio.data.IntelligenceSyncState = com.pulsecast.radio.data.IntelligenceSyncState.Idle,
    trainedStationCount: Int = 0,
    activeGeneratedAt: String? = null,
    onSyncIntelligenceClick: () -> Unit = {},
    onEnabledChanged: (Boolean) -> Unit,
    onToleranceChanged: (Int) -> Unit,
    onSensitivityChanged: (Float) -> Unit,
    onResetHoldChanged: (Int) -> Unit,
    onStrategyChanged: (String) -> Unit,
    onMiningModeChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = BgSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(20.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentPrimary
                        )
                        Text(
                            text = "Smart Station Switcher",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Master Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1523))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatisches Ausweichen",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Wechselt zu Musik bei Werbung, News & Stau",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onEnabledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgMain,
                            checkedTrackColor = AccentPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // OTA Intelligence Training Sync Card (Top Priority)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1523))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📡 KI-Trainingsdaten (OTA)",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val dateStr = activeGeneratedAt?.take(16)?.replace("T", " ") ?: "Lokal"
                                Text(
                                    text = "$trainedStationCount Sender • Stand: $dateStr",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            val isChecking = syncState is com.pulsecast.radio.data.IntelligenceSyncState.Checking
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChecking) TextMuted.copy(alpha = 0.2f) else AccentPrimary.copy(alpha = 0.2f))
                                    .clickable(enabled = !isChecking, onClick = onSyncIntelligenceClick)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isChecking) "Prüfe…" else "🔄 Sync",
                                    color = if (isChecking) TextMuted else AccentPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (syncState is com.pulsecast.radio.data.IntelligenceSyncState.Success) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "✅ ${syncState.message} (${syncState.stationCount} Sender)",
                                color = Color(0xFF4ADE80),
                                fontSize = 11.sp
                            )
                        } else if (syncState is com.pulsecast.radio.data.IntelligenceSyncState.Error) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val is404 = syncState.error.contains("404")
                            Text(
                                text = if (is404) "⚠️ HTTP 404: Git-Repo muss auf GitHub auf 'Public' stehen" else "⚠️ ${syncState.error}",
                                color = Color(0xFFFF6B6B),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tolerance Slider
                Text(
                    text = "Sprach-Toleranz: $toleranceSeconds Sekunden",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Verhindert Senderwechsel bei kurzen DJ-Anmoderationen.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Slider(
                    value = toleranceSeconds.toFloat(),
                    onValueChange = { onToleranceChanged(it.toInt()) },
                    valueRange = 5f..30f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPrimary,
                        activeTrackColor = AccentPrimary,
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15, 20).forEach { s ->
                        val isSelected = toleranceSeconds == s
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentPrimary else Color(0xFF0F1523))
                                .clickable { onToleranceChanged(s) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (s == 10) "${s}s (Std)" else "${s}s",
                                color = if (isSelected) BgMain else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI Model Sensitivity
                Text(
                    text = "Erkennungs-Empfindlichkeit (KI)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Stellt ein, wie leicht die KI Sprache über Musik erkennt.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0.65f to "Schonend",
                        0.50f to "Standard",
                        0.35f to "Empfindlich"
                    ).forEach { (sens, label) ->
                        val isSelected = (speechSensitivity - sens).let { Math.abs(it) < 0.08f }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentPrimary else Color(0xFF0F1523))
                                .clickable { onSensitivityChanged(sens) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) BgMain else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speech Pause Hold Buffer
                Text(
                    text = "Sprechpausen-Puffer: $speechResetHoldSeconds Sekunden",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Verhindert Zurücksetzen des Timers bei kurzen Redepausen.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 3, 5).forEach { h ->
                        val isSelected = speechResetHoldSeconds == h
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF10B981) else Color(0xFF0F1523))
                                .clickable { onResetHoldChanged(h) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (h == 3) "${h}s (Std)" else "${h}s",
                                color = if (isSelected) BgMain else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fallback Strategy Option
                Text(
                    text = "Ausweich-Ziel (Welcher Sender wird gewählt?)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Bestimmt, wohin bei Sprache/Werbung gewechselt wird.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "favorites_first" to "Favoriten bevorzugen, sonst Musiksender",
                        "favorites_only" to "Nur eigene Favoriten-Liste",
                        "same_genre" to "Anderer Sender im selben Genre"
                    ).forEach { (stratKey, label) ->
                        val isSelected = smartSwitchStrategy == stratKey
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Color(0xFF0F1523))
                                .border(1.dp, if (isSelected) AccentPrimary else BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { onStrategyChanged(stratKey) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mining Window Option
                Text(
                    text = "Signal-Mining & Jingle-Training Modus",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Bestimmt, wie oft die KI Jingle-Fingerabdrücke im Hintergrund lernt.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "continuous" to "Durchgehend 100%",
                        "window_broad" to "Breit (:55 - :05)",
                        "window_std" to "Standard (:57 - :03)"
                    ).forEach { (modeKey, label) ->
                        val isSelected = miningMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF0F1523))
                                .clickable { onMiningModeChanged(modeKey) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Learning & Fallback Explanatory Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF080D1A))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "💡 So funktioniert die intelligente Steuerung:",
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 1. Priorität: Favoriten mit '100% Musik' & ohne News.\n" +
                                   "• 2. Priorität: Elektronische Musiksender (SomaFM, Lofi Cafe, Ibiza).\n" +
                                   "• Stumme Vorab-Prüfung: Wartet Pre-Roll Werbung stumm ab, bevor Ton wechselt.\n" +
                                   "• Manuelle Rückkehr: Mit dem Zurück-Knopf kehrst du sofort zum Hauptsender zurück und ignorierst dessen Sprache.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
