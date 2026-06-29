package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bios.app.ingest.GarminApiAdapter
import com.bios.app.ingest.PolarApiAdapter
import com.bios.app.ingest.WhoopApiAdapter
import com.bios.app.ingest.WithingsApiAdapter
import com.bios.app.ui.AppViewModel

/**
 * Data & sources — where the owner's data comes from: wearable/API
 * connections and Health Connect. Export/import lives in its own
 * [DataExportScreen] (route "your_data") so owners can find it one tap off
 * the Self tab instead of buried at the bottom of this connection screen.
 *
 * [onNavigate] forwards the few sub-routes this screen links to
 * (data_coverage, metric_sources, ble_pair).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit = {},
    onNavigate: (route: String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DataSourcesCard(viewModel = viewModel, onNavigate = onNavigate)
        }
    }
}

/**
 * A token-backed wearable/API connection: the connect/disconnect row plus
 * its paste-token dialog. Storage differs per provider (Oura has its own
 * store; the rest share apiTokenStore keyed by PROVIDER_KEY), so the
 * save/clear/initial-state are passed in as plain lambdas.
 */
private class TokenSource(
    val rowName: String,
    val dialogName: String,
    val helperText: String,
    val initialConnected: Boolean,
    val onSave: (String) -> Unit,
    val onClear: () -> Unit,
)

@Composable
private fun DataSourcesCard(
    viewModel: AppViewModel,
    onNavigate: (route: String) -> Unit,
) {
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val tokenSources = listOf(
        TokenSource(
            "Oura Ring", "Oura", SettingsHelperText.OURA,
            viewModel.ouraTokenStore.hasToken(),
            { viewModel.ouraTokenStore.saveToken(it) },
            { viewModel.ouraTokenStore.clearToken() },
        ),
        apiTokenSource(viewModel, "Withings", SettingsHelperText.WITHINGS, WithingsApiAdapter.PROVIDER_KEY),
        apiTokenSource(viewModel, "WHOOP", SettingsHelperText.WHOOP, WhoopApiAdapter.PROVIDER_KEY),
        apiTokenSource(viewModel, "Garmin", SettingsHelperText.GARMIN, GarminApiAdapter.PROVIDER_KEY),
        apiTokenSource(viewModel, "Polar", SettingsHelperText.POLAR, PolarApiAdapter.PROVIDER_KEY),
    )

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Data Sources", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Health Connect")
                Text(
                    if (hasPermissions) "Connected" else "Not Connected",
                    color = if (hasPermissions) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            tokenSources.forEach { source ->
                Spacer(Modifier.height(4.dp))
                ApiTokenSourceRow(source)
            }

            Spacer(Modifier.height(4.dp))
            ConnectableSourceRow(
                name = "Air-quality sensor (BLE)",
                isConnected = viewModel.bleAirQualityAdapter.isPaired,
                // Both routes go to the pair screen — unpair lives there.
                onConnect = { onNavigate("ble_pair") },
                onDisconnect = { onNavigate("ble_pair") },
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onNavigate("data_coverage") }, modifier = Modifier.fillMaxWidth()) {
                Text("Data coverage")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { onNavigate("metric_sources") }, modifier = Modifier.fillMaxWidth()) {
                Text("Per-metric sources")
            }
        }
    }
}

private fun apiTokenSource(
    viewModel: AppViewModel,
    name: String,
    helperText: String,
    providerKey: String,
) = TokenSource(
    rowName = name,
    dialogName = name,
    helperText = helperText,
    initialConnected = viewModel.apiTokenStore.hasToken(providerKey),
    onSave = { viewModel.apiTokenStore.saveToken(providerKey, it) },
    onClear = { viewModel.apiTokenStore.clearToken(providerKey) },
)

@Composable
private fun ApiTokenSourceRow(source: TokenSource) {
    var connected by remember { mutableStateOf(source.initialConnected) }
    var showDialog by remember { mutableStateOf(false) }
    ConnectableSourceRow(
        name = source.rowName,
        isConnected = connected,
        onConnect = { showDialog = true },
        onDisconnect = { source.onClear(); connected = false },
    )
    if (showDialog) {
        PasteTokenDialog(
            providerName = source.dialogName,
            helperText = source.helperText,
            onConnect = { token -> source.onSave(token); connected = true; showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}
