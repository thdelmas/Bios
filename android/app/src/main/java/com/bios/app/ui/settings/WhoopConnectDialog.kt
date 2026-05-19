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
 * WHOOP connection dialog — paste an OAuth-issued access token.
 *
 * WHOOP's developer API requires OAuth 2.0; this dialog accepts a bearer
 * token the owner has obtained out-of-band against developer.whoop.com,
 * mirroring the Oura / Withings paste-the-token pattern. Refresh handling
 * is out of scope for the first cut — owners replace the value when it
 * expires; a registered Bios WHOOP app with refresh-aware token storage
 * is a future PR.
 */
@Composable
fun WhoopConnectDialog(
    onConnect: (token: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect WHOOP") },
        text = {
            Column {
                Text(
                    "Paste a WHOOP API access token. Obtain one through a WHOOP " +
                        "developer-account OAuth exchange against developer.whoop.com — " +
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
