package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bios.app.ingest.CorosApiAdapter
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch

/**
 * COROS row: unlike the paste-a-token providers, COROS has no developer
 * portal for a personal token, so the row signs in with the account
 * credentials and keeps only the session token the vendor returns.
 * The password is held in composable state for the duration of the dialog
 * and never written anywhere. The last sync status (written by the adapter)
 * is shown in the dialog so a dead session is visible, not silent.
 */
@Composable
fun CorosSourceRow(viewModel: AppViewModel) {
    val store = viewModel.apiTokenStore
    var connected by remember { mutableStateOf(store.hasToken(CorosApiAdapter.PROVIDER_KEY)) }
    var showDialog by remember { mutableStateOf(false) }
    ConnectableSourceRow(
        name = "COROS",
        isConnected = connected,
        onConnect = { showDialog = true },
        onDisconnect = {
            store.clearToken(CorosApiAdapter.PROVIDER_KEY)
            store.clearToken(CorosApiAdapter.STATUS_KEY)
            connected = false
        },
    )
    if (showDialog) {
        CorosLoginDialog(
            adapter = viewModel.corosAdapter,
            lastStatus = store.getToken(CorosApiAdapter.STATUS_KEY),
            onConnected = { token, region ->
                store.saveToken(CorosApiAdapter.REGION_KEY, region)
                store.saveToken(CorosApiAdapter.PROVIDER_KEY, token)
                connected = true
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
fun CorosLoginDialog(
    adapter: CorosApiAdapter,
    lastStatus: String?,
    onConnected: (token: String, region: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(CorosApiAdapter.DEFAULT_REGION) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Connect COROS") },
        text = {
            Column {
                Text(SettingsHelperText.COROS, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("E-mail") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), enabled = !busy,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), enabled = !busy,
                )
                Spacer(Modifier.height(8.dp))
                Text("Account region", style = MaterialTheme.typography.labelMedium)
                CorosApiAdapter.REGION_LABELS.forEach { (key, label) ->
                    FilterChip(
                        selected = region == key,
                        onClick = { region = key },
                        label = { Text(label) },
                        enabled = !busy,
                    )
                }
                if (!lastStatus.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Last sync: $lastStatus", style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        adapter.login(email, password, region)
                            .onSuccess { token -> onConnected(token, region) }
                            .onFailure { error = it.message ?: "Sign-in failed" }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Signing in…" else "Sign in") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
