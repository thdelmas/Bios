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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bios.app.engine.SeizureDetectionPrefs
import com.bios.app.ingest.SeizureDetectionService

/**
 * Settings → Seizure detection card. Single toggle that gates the
 * continuous accelerometer foreground service feeding
 * [com.bios.app.engine.SeizureDetector] (#269 Cut 3a). Lives in its
 * own card (separate from `Diagnostics`) because this is **not**
 * device-keyed observability — it's an experimental medical detector
 * the owner opts into, and the copy needs space to be honest about
 * what enabling it does.
 *
 * Off by default. Lives in a separate file because [SettingsScreen]
 * is at the 500-line limit; new cards land here rather than growing
 * the host file.
 */
@Composable
internal fun SettingsSeizureDetectionCard(onViewDetections: () -> Unit = {}) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Seizure detection", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            var enabled by remember {
                mutableStateOf(SeizureDetectionPrefs.isEnabled(context))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Wearable convulsive-seizure detector",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Runs a continuous accelerometer + heart-rate watch in the background to " +
                            "flag candidate convulsive episodes. Detections land in your timeline " +
                            "for review — they do not page emergency services on their own. " +
                            "Off by default. Holds a wake lock; expect a measurable battery cost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        SeizureDetectionPrefs.setEnabled(context, it)
                        // Flip the foreground service in real time so the
                        // owner doesn't need to restart the app for the
                        // toggle to take effect.
                        if (it) {
                            SeizureDetectionService.startIfOptedIn(context)
                        } else {
                            SeizureDetectionService.stop(context)
                        }
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onViewDetections) {
                Text("View detections")
            }
        }
    }
}
