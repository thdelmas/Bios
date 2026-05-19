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
 * Generic paste-an-OAuth-token dialog used by every API adapter that hasn't
 * yet wired its full OAuth dance (Oura, Withings, WHOOP, Garmin, Polar at
 * the time of writing). Each adapter was previously a 60-line file that
 * differed only in `providerName` and the helper-text body string — those
 * four near-identical files collapse into this one composable.
 *
 * Why this pattern. Bios doesn't ship registered OAuth client_id/secret
 * pairs for these providers, so the owner must obtain a token out-of-band
 * (a personal-access-token issued via the provider's developer portal, or
 * a refresh-exchanged bearer from a CLI helper). When/if Bios registers a
 * provider app and runs the full OAuth dance in-app, this dialog stays —
 * the new flow drops the bearer into the same EncryptedSharedPreferences
 * slot and the paste UI becomes the "advanced" override.
 *
 * The dialog never logs the entered token (`singleLine = true` + no
 * downstream emission outside [onConnect]).
 */
@Composable
fun PasteTokenDialog(
    providerName: String,
    helperText: String,
    onConnect: (token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tokenInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect $providerName") },
        text = {
            Column {
                Text(helperText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Access Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
