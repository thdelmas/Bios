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
import com.bios.app.model.SourceType
import com.bios.app.ingest.OwnerAction
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch

/**
 * COROS row: unlike the paste-a-token providers, COROS has no developer
 * portal for a personal token, so the row signs in with the account
 * credentials and keeps only the session token the vendor returns.
 * The password is held in composable state for the duration of the dialog
 * and written to the encrypted store only when the owner ticks "keep my
 * password" (docs/specs/source-liveness.md, limit 2). "Connected" means
 * the last use succeeded: a recorded refusal shows as "Needs attention"
 * with the vendor's message, and the same dialog re-signs in.
 */
@Composable
fun CorosSourceRow(viewModel: AppViewModel) {
    val store = viewModel.apiTokenStore
    val health = viewModel.sourceHealthStore
    var connected by remember { mutableStateOf(store.hasToken(CorosApiAdapter.PROVIDER_KEY)) }
    var attention by remember {
        mutableStateOf(
            health.get(SourceType.COROS_API).let { h ->
                if (h.hasOpenError && h.ownerAction != OwnerAction.NONE) h.lastError else null
            }
        )
    }
    var showDialog by remember { mutableStateOf(false) }
    ConnectableSourceRow(
        name = "COROS",
        isConnected = connected,
        attention = attention,
        onConnect = { showDialog = true },
        onDisconnect = {
            store.clearToken(CorosApiAdapter.PROVIDER_KEY)
            store.clearToken(CorosApiAdapter.STATUS_KEY)
            store.clearToken(CorosApiAdapter.EMAIL_KEY)
            store.clearToken(CorosApiAdapter.PASSWORD_KEY)
            health.clear(SourceType.COROS_API)
            connected = false
            attention = null
        },
    )
    if (showDialog) {
        CorosLoginDialog(
            adapter = viewModel.corosAdapter,
            lastStatus = store.getToken(CorosApiAdapter.STATUS_KEY),
            keepPasswordInitially = store.hasToken(CorosApiAdapter.PASSWORD_KEY),
            onConnected = { login ->
                store.saveToken(CorosApiAdapter.REGION_KEY, login.region)
                store.saveToken(CorosApiAdapter.PROVIDER_KEY, login.token)
                if (login.keepPassword) {
                    store.saveToken(CorosApiAdapter.EMAIL_KEY, login.email)
                    store.saveToken(CorosApiAdapter.PASSWORD_KEY, login.password)
                } else {
                    store.clearToken(CorosApiAdapter.EMAIL_KEY)
                    store.clearToken(CorosApiAdapter.PASSWORD_KEY)
                }
                health.recordOk(SourceType.COROS_API)
                connected = true
                attention = null
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** What a successful sign-in hands back to the row. */
data class CorosLogin(
    val token: String,
    val region: String,
    val email: String,
    val password: String,
    val keepPassword: Boolean,
)

@Composable
fun CorosLoginDialog(
    adapter: CorosApiAdapter,
    lastStatus: String?,
    keepPasswordInitially: Boolean = false,
    onConnected: (CorosLogin) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keepPassword by remember { mutableStateOf(keepPasswordInitially) }
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
                CorosDialogFooter(
                    keepPassword = keepPassword,
                    onKeepPasswordChange = { keepPassword = it },
                    enabled = !busy,
                    lastStatus = lastStatus,
                    error = error,
                )
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
                            .onSuccess { token ->
                                onConnected(CorosLogin(token, region, email.trim(), password, keepPassword))
                            }
                            .onFailure { error = it.message ?: "Sign-in failed" }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Signing in…" else "Sign in") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** Keep-password choice, last sync status and sign-in error, below the region chips. */
@Composable
private fun CorosDialogFooter(
    keepPassword: Boolean,
    onKeepPasswordChange: (Boolean) -> Unit,
    enabled: Boolean,
    lastStatus: String?,
    error: String?,
) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = keepPassword, onCheckedChange = onKeepPasswordChange, enabled = enabled)
        Text(
            "Keep my password so Bios can sign in again by itself when the session expires",
            style = MaterialTheme.typography.bodySmall,
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
