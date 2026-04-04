package com.bios.app.ui.privacy

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.ReproductiveDatabase
import com.bios.app.model.PrivacyTier
import com.bios.app.platform.DataDestroyer
import com.bios.app.platform.DataFootprint
import com.bios.app.platform.ForensicRiskMonitor
import com.bios.app.platform.PlatformDetector
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch

/**
 * Privacy dashboard — the owner audits their own exposure from a single screen.
 *
 * Shows:
 * - Data footprint (total readings, DB size, date range, retention)
 * - Connected sources and their last sync
 * - Privacy tier and what it means
 * - LETHE status (burner mode, dead man's switch) if embedded
 * - Forensic risk indicators
 * - Reproductive data status (separate store)
 * - Quick actions: wipe recent data, destroy reproductive data, destroy all
 *
 * Aligned with LETHE's principle: "every action visible and explainable."
 */
@Composable
fun PrivacyDashboardScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var footprint by remember { mutableStateOf<DataFootprint?>(null) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showReproWipeDialog by remember { mutableStateOf(false) }
    var showDestroyAllDialog by remember { mutableStateOf(false) }

    val privacyTier = remember {
        val prefs = context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
        val tier = prefs.getString("privacy_tier", PrivacyTier.PRIVATE.name)
        PrivacyTier.valueOf(tier ?: PrivacyTier.PRIVATE.name)
    }

    LaunchedEffect(Unit) {
        val monitor = ForensicRiskMonitor(context, viewModel.db)
        footprint = monitor.getDataFootprint()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Privacy",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        footprint?.let { fp ->
            // Forensic risk warning
            if (fp.shouldWarnBurnerModeOff) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Burner mode is off. Health data accumulates between boots. " +
                                "You have ${fp.dataAgeDays} days of data on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Data footprint
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data Footprint", style = MaterialTheme.typography.titleSmall)
                    FootprintRow("Readings stored", "${fp.totalReadings}")
                    FootprintRow("Data age", "${fp.dataAgeDays} days")
                    FootprintRow("Database size", "%.1f MB".format(fp.databaseSizeMb))
                    FootprintRow("Connected sources", "${fp.connectedSourceCount}")
                    FootprintRow("Retention policy", "${fp.retentionDays} days")
                    if (fp.hasReproductiveData) {
                        FootprintRow("Reproductive data", "Separate encrypted store")
                    }
                }
            }

            // Privacy tier
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy Tier", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (privacyTier) {
                            PrivacyTier.PRIVATE -> "Private — all data stays on this device. Nothing is transmitted."
                            PrivacyTier.COMMUNITY -> "Community — anonymized statistical patterns are contributed. " +
                                "No raw readings, timestamps, or identifiers leave this device."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Platform status
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Platform", style = MaterialTheme.typography.titleSmall)
                    FootprintRow(
                        "Platform",
                        if (fp.isLethe) "LETHE (privacy-hardened)" else "Stock Android"
                    )
                    if (fp.isLethe) {
                        FootprintRow(
                            "Burner mode",
                            if (fp.burnerModeActive) "On (data wiped each boot)" else "Off"
                        )
                        FootprintRow(
                            "Dead man's switch",
                            if (fp.deadManSwitchArmed) "Armed" else "Disarmed"
                        )
                    }
                    FootprintRow(
                        "Google Play Services",
                        if (PlatformDetector.capabilities(context).hasPlayServices) "Present" else "Absent"
                    )
                }
            }

            // Quick actions
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data Controls", style = MaterialTheme.typography.titleSmall)

                    if (fp.hasReproductiveData) {
                        OutlinedButton(
                            onClick = { showReproWipeDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Destroy reproductive data")
                        }
                    }

                    OutlinedButton(
                        onClick = { showWipeDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wipe recent data")
                    }

                    Button(
                        onClick = { showDestroyAllDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Destroy all data")
                    }
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    // Dialogs
    if (showReproWipeDialog) {
        AlertDialog(
            onDismissRequest = { showReproWipeDialog = false },
            title = { Text("Destroy reproductive data?") },
            text = { Text("This will permanently destroy all cycle tracking data. The encryption key will be destroyed — data is irrecoverable.") },
            confirmButton = {
                TextButton(onClick = {
                    DataDestroyer.destroyReproductiveData(context)
                    showReproWipeDialog = false
                    scope.launch {
                        val monitor = ForensicRiskMonitor(context, viewModel.db)
                        footprint = monitor.getDataFootprint()
                    }
                }) { Text("Destroy", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showReproWipeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDestroyAllDialog) {
        AlertDialog(
            onDismissRequest = { showDestroyAllDialog = false },
            title = { Text("Destroy all Bios data?") },
            text = { Text("This will permanently destroy all health data, baselines, alerts, journal entries, and settings. Encryption keys will be destroyed — nothing is recoverable.") },
            confirmButton = {
                TextButton(onClick = {
                    DataDestroyer.destroyAll(context)
                    showDestroyAllDialog = false
                }) { Text("Destroy everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDestroyAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showWipeDialog) {
        var selectedDays by remember { mutableIntStateOf(7) }
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe recent data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete the most recent readings. Baselines are preserved.")
                    listOf(1, 7, 30).forEach { days ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedDays == days,
                                onClick = { selectedDays = days }
                            )
                            Text("Last $days day${if (days > 1) "s" else ""}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val monitor = ForensicRiskMonitor(context, viewModel.db)
                        monitor.quickWipe(selectedDays)
                        footprint = monitor.getDataFootprint()
                    }
                    showWipeDialog = false
                }) { Text("Wipe") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FootprintRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
