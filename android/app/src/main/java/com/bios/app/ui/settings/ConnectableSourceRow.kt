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
 * a disconnect button below. "Connected" means the last use succeeded;
 * a row whose adapter recorded a refusal reads "Needs attention" with the
 * message, and the button opens the same connect path without clearing
 * anything (docs/specs/source-liveness.md). Keeps SettingsScreen.kt clear of the
 * adapter-specific boilerplate that grows linearly with adapter count.
 */
@Composable
fun ConnectableSourceRow(
    name: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    attention: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name)
        when {
            !isConnected -> TextButton(onClick = onConnect) { Text("Connect") }
            attention != null -> TextButton(onClick = onConnect) {
                Text("Needs attention", color = MaterialTheme.colorScheme.error)
            }
            else -> Text("Connected", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (isConnected && attention != null) {
        Text(attention, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (isConnected) {
        TextButton(onClick = onDisconnect) {
            Text("Disconnect $name", color = MaterialTheme.colorScheme.error)
        }
    }
}
