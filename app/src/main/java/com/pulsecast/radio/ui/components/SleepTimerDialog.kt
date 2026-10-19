package com.pulsecast.radio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsecast.radio.ui.theme.AccentPrimary
import com.pulsecast.radio.ui.theme.BgSurfaceCard
import com.pulsecast.radio.ui.theme.TextPrimary
import com.pulsecast.radio.ui.theme.TextSecondary

@Composable
fun SleepTimerDialog(
    currentMinutesRemaining: Int,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    val options = listOf(
        0 to "Aus (Kein Timer)",
        15 to "15 Minuten",
        30 to "30 Minuten",
        45 to "45 Minuten",
        60 to "60 Minuten",
        90 to "90 Minuten"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = AccentPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Sleep Timer", color = TextPrimary, fontSize = 18.sp)
            }
        },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    val isSelected = (minutes == 0 && currentMinutesRemaining == 0) ||
                            (minutes > 0 && currentMinutesRemaining in (minutes - 1)..minutes)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectMinutes(minutes)
                                onDismiss()
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectMinutes(minutes)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = if (isSelected) AccentPrimary else TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Schließen", color = AccentPrimary)
            }
        }
    )
}
