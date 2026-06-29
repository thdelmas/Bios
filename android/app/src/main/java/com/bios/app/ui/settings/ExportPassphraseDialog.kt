package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Minimum passphrase length for an encrypted export (defensive, not a policy). */
private const val MIN_PASSPHRASE_LENGTH = 8

/**
 * Collects a passphrase (entered twice) for a password-protected export. The
 * passphrase never leaves this dialog except via [onConfirm] — the caller holds
 * it only in transient screen state for the encryption call.
 *
 * There is no recovery: a forgotten passphrase means the encrypted file cannot
 * be opened, by Bios or anyone else. The confirm field guards against a typo
 * silently producing an unrecoverable file.
 */
@Composable
fun ExportPassphraseDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val tooShort = passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != passphrase
    val valid = passphrase.length >= MIN_PASSPHRASE_LENGTH && confirm == passphrase

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password-protect this export") },
        text = {
            Column {
                Text(
                    "The export becomes an AES-256 encrypted zip. Anyone you share " +
                        "it with — a doctor, another app — can open it with this " +
                        "password using standard tools. Keep it safe: a lost password " +
                        "cannot be recovered and the file cannot be opened without it.",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text("At least $MIN_PASSPHRASE_LENGTH characters") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text("Passwords don't match") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(passphrase) }) {
                Text("Set password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
