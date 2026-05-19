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
import androidx.compose.material3.HorizontalDivider
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
import com.bios.app.alerts.DailyDigestWorker
import com.bios.app.push.PushRegistrationManager

/**
 * Settings → Notifications card. Three toggles: daily digest, disconnect
 * alerts, and UnifiedPush registration. Extracted from [SettingsScreen]
 * so the host file stays under the 500-line cap as more data-source rows
 * (WHOOP, future Garmin) land in the data-sources block above.
 */
@Composable
internal fun SettingsNotificationsCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            var digestEnabled by remember { mutableStateOf(DailyDigestWorker.isEnabled(context)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily Digest", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Morning summary of your vitals at 8 AM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = digestEnabled,
                    onCheckedChange = {
                        digestEnabled = it
                        DailyDigestWorker.setEnabled(context, it)
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DisconnectAlertToggle()
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            var pushEnabled by remember { mutableStateOf(PushRegistrationManager.isEnabled(context)) }
            var pushDistributor by remember { mutableStateOf(PushRegistrationManager.getDistributorName(context)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Push Notifications", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (pushEnabled && pushDistributor != null)
                            "Via $pushDistributor — no Google required"
                        else
                            "Receive population health signals without polling. Requires a UnifiedPush distributor (e.g. ntfy).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = pushEnabled,
                    onCheckedChange = { enabled ->
                        pushEnabled = enabled
                        if (enabled) {
                            PushRegistrationManager.setEnabled(context, true)
                            PushRegistrationManager.register(context)
                        } else {
                            PushRegistrationManager.unregister(context)
                        }
                        pushDistributor = PushRegistrationManager.getDistributorName(context)
                    }
                )
            }
        }
    }
}
