package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.bios.app.alerts.DisconnectNotifier

/**
 * Owner-disable toggle for the category-3 "Bios-state push" surface —
 * notifications about previously-active ingest adapters that have gone
 * silent past their staleness window.
 *
 * Lives in its own sibling file so [SettingsScreen] stays under the
 * 500-line cap. Mirrors the `PdfSummaryButton` extraction pattern.
 */
@Composable
fun DisconnectAlertToggle() {
    val context = LocalContext.current
    val notifier = remember { DisconnectNotifier(context) }
    var enabled by remember { mutableStateOf(notifier.isEnabled()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Disconnect alerts", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Notify when a data source stops syncing (Oura token expiry, " +
                    "Gadgetbridge stalled, etc.). Bios reporting on Bios.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                notifier.setEnabled(it)
            },
        )
    }
}
