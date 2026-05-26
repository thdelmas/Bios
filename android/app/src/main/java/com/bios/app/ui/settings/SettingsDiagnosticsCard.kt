package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bios.app.engine.PpgCalibrationLogger

/**
 * Settings → Diagnostics card. One toggle today: PPG calibration logger
 * (#266 Cut 1). Surfaced as its own card so the user sees this is *opt-in*
 * data collection scoped to their own device, distinct from the Community
 * privacy tier (which is about leaving the device at all).
 *
 * Lives in a separate file because [SettingsScreen] is at the 500-line
 * limit; new toggles land here rather than growing the host file.
 */
@Composable
internal fun SettingsDiagnosticsCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            var ppgEnabled by remember {
                mutableStateOf(PpgCalibrationLogger.isEnabled(context))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PPG calibration log", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Append one row per camera-PPG capture with signal-quality numbers — " +
                            "lets us calibrate motion thresholds for your device. " +
                            "Off by default. Stays on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ppgEnabled,
                    onCheckedChange = {
                        ppgEnabled = it
                        PpgCalibrationLogger.setEnabled(context, it)
                    },
                )
            }
        }
    }
}
