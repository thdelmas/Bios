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
 * Withings connection dialog — paste an OAuth-issued access token.
 *
 * Withings's API requires OAuth 2.0; this dialog deliberately doesn't
 * implement the OAuth dance (would require a registered Withings developer
 * app with client_id / client_secret bound at build time). Instead it
 * accepts a token the owner has obtained out-of-band — same shape as the
 * Oura personal-access-token flow, and works with any token issued via a
 * Withings developer account's OAuth exchange.
 *
 * Refresh-token handling is also out of scope: the adapter treats whatever
 * string is stored as a bearer token. Owners replace the value when it
 * expires. A full OAuth-with-refresh integration is a future PR once a
 * Withings developer app is registered for Bios.
 */
@Composable
fun WithingsConnectDialog(
    onConnect: (token: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Withings") },
        text = {
            Column {
                Text(
                    "Paste a Withings API access token. Obtain one through a Withings " +
                        "developer-account OAuth exchange against developer.withings.com — " +
                        "Bios does not perform the OAuth dance itself yet.",
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
