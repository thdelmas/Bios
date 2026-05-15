package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One row in Settings → Data Sources for a third-party adapter that needs
 * an owner-managed credential (Oura, Withings, future WHOOP / Garmin /
 * Dexcom). Renders the name, the connection state, and — when connected —
 * a disconnect button below. Keeps SettingsScreen.kt clear of the
 * adapter-specific boilerplate that grows linearly with adapter count.
 */
@Composable
fun ConnectableSourceRow(
    name: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name)
        if (isConnected) {
            Text("Connected", color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onConnect) { Text("Connect") }
        }
    }
    if (isConnected) {
        TextButton(onClick = onDisconnect) {
            Text("Disconnect $name", color = MaterialTheme.colorScheme.error)
        }
    }
}
