package com.pulsecast.radio.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pulsecast.radio.ui.theme.BgSurfaceCard
import com.pulsecast.radio.ui.theme.BorderSubtle
import com.pulsecast.radio.ui.theme.TextMuted
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary
import com.pulsecast.radio.updater.AppUpdateState

@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    onUpdateClick: (downloadUrl: String) -> Unit,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Neues Update verfügbar",
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

                when (state) {
                    is AppUpdateState.Available -> {
                        Text(
                            text = "Version: ${state.versionName}",
                            color = AccentPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F1523))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Änderungen:",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.changelog,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Später", color = TextMuted)
                            }
                            Button(
                                onClick = { onUpdateClick(state.downloadUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Jetzt installieren", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is AppUpdateState.Downloading -> {
                        Text(
                            text = "Lade Update herunter (${state.progressPercent}%)…",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentPrimary,
                            trackColor = Color(0xFF0F1523)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Der Android-Installer öffnet sich automatisch nach dem Download.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    is AppUpdateState.ReadyToInstall -> {
                        Text(
                            text = "Download abgeschlossen! Bitte bestätige die Installation.",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}
