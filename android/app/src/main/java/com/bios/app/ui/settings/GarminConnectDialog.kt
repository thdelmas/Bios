package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Garmin connection dialog — paste an OAuth-issued access token for the
 * Garmin Wellness API. Same paste-the-token UX as Oura / Withings / WHOOP.
 *
 * Caveat: Garmin's Wellness API officially uses OAuth 1.0a signed
 * requests, not OAuth 2.0 bearer tokens. The current
 * [com.bios.app.ingest.GarminApiAdapter] sends `Authorization: Bearer <token>`
 * which works when the upstream accepts a session token but won't pass
 * Garmin's signed-request enforcement out of the box — a registered Bios
 * Garmin developer app and per-request HMAC-SHA1 signing is a follow-up.
 * Until that lands, this surface is useful for owners proxying through a
 * pre-signed gateway, and for getting the wiring tests green.
 */
@Composable
fun GarminConnectDialog(
    onConnect: (token: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Garmin") },
        text = {
            Column {
                Text(
                    "Paste a Garmin Wellness API access token (or a pre-signed " +
                        "session token from a Garmin proxy). Bios does not perform " +
                        "the OAuth 1.0a dance itself yet — see the connection notes " +
                        "for what works today.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Access Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = tokenInput.trim()
                    if (trimmed.isNotBlank()) onConnect(trimmed)
                }
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
