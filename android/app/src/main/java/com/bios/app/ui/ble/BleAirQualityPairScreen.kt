package com.bios.app.ui.ble

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Screen for pairing a BLE air-quality peripheral (#43 phase 2). The owner
 * lands here from Settings → Pair air-quality sensor or from the Data
 * Coverage CTA. Lists ESS-advertising devices in range; tap to pair.
 *
 * The screen owns the runtime-permission launcher because permissions are
 * a UX concern: the OS prompt must follow an owner tap (the "Allow
 * scanning" button) so the request lands in context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleAirQualityPairScreen(
    viewModel: BleAirQualityPairViewModel,
    onBack: () -> Unit
) {
    val discovered by viewModel.discovered.collectAsState()
    val paired by viewModel.paired.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val bleEnabled by viewModel.bleEnabled.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissionState()
        if (viewModel.hasPermissions.value) {
            viewModel.startScan()
        }
    }

    // Auto-stop the scan when the screen is left. ViewModel.onCleared also
    // handles cancellation; this is a defensive double-stop.
    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair air-quality sensor") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopScan()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderCard()

            if (paired != null) {
                PairedCard(
                    deviceName = paired!!.name,
                    address = paired!!.address,
                    isConnected = isConnected,
                    onUnpair = { viewModel.unpair() }
                )
            }

            when {
                !bleEnabled -> StatusCard(
                    title = "Bluetooth is off",
                    detail = "Turn Bluetooth on in system settings, then return here.",
                )
                !hasPermissions -> Button(
                    onClick = {
                        permissionLauncher.launch(viewModel.requiredPermissions())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Allow scanning") }
                else -> ScanControls(
                    isScanning = isScanning,
                    onStart = { viewModel.startScan() },
                    onStop = { viewModel.stopScan() }
                )
            }

            if (hasPermissions && bleEnabled) {
                Text(
                    if (discovered.isEmpty()) "No devices yet — make sure the sensor is on and advertising."
                    else "Tap a device to pair.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(discovered, key = { it.address }) { d ->
                        DeviceRow(
                            name = d.name,
                            address = d.address,
                            rssi = d.rssi,
                            isCurrentlyPaired = paired?.address == d.address,
                            onClick = { viewModel.pair(d.address, d.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Environmental Sensing Service (ESS)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Bios looks for peripherals exposing the Bluetooth-SIG Environmental Sensing Service (0x181A). " +
                    "Air-quality readings (PM2.5, CO2, VOC) feed the same patterns your wearables do — " +
                    "they're a confounder for sleep, HRV, and respiratory anomalies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PairedCard(
    deviceName: String,
    address: String,
    isConnected: Boolean,
    onUnpair: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Paired",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(deviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(address, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isConnected) "Connected — readings streaming." else "Waiting for connection…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
                Text("Unpair")
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScanControls(
    isScanning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStart,
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Text(if (isScanning) "Scanning…" else "Start scan")
        }
        OutlinedButton(
            onClick = onStop,
            enabled = isScanning,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    address: String,
    rssi: Int,
    isCurrentlyPaired: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("RSSI: $rssi dBm", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onClick, enabled = !isCurrentlyPaired) {
                Text(if (isCurrentlyPaired) "Paired" else "Pair")
            }
        }
    }
}
