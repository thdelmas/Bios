package com.bios.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.CompanionGrant
import com.bios.app.model.PrivacyTier
import com.bios.app.privacy.ContributionWorker
import com.bios.app.ui.AppViewModel

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigate: (route: String) -> Unit = {},
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val companionGrants by viewModel.db.companionGrantDao()
        .observeAll()
        .collectAsState(initial = emptyList())
    val pendingCompanionCount = companionGrants.count { it.state == CompanionGrant.STATE_PENDING }
    val approvedCompanionCount = companionGrants.count { it.state == CompanionGrant.STATE_GRANTED }
    var privacyTier by remember {
        val prefs = context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
        val tier = prefs.getString("privacy_tier", PrivacyTier.PRIVATE.name)
        mutableStateOf(PrivacyTier.valueOf(tier ?: PrivacyTier.PRIVATE.name))
    }
    // Passive, owner-pulled preventive-care due count for the Identity card
    // badge (#401). Computed only because the owner opened this screen — no
    // notification, no background work. Recomputed each time the screen is
    // entered, so recording a checkup elsewhere clears the badge on return.
    var dueByRoute by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val due = com.bios.app.ui.screening.PreventiveCareData.dueCount(context)
        dueByRoute = if (due > 0) mapOf("preventive_care" to due) else emptyMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Self", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        IdentityCard(onNavigate = onNavigate, dueByRoute = dueByRoute)

        // Privacy — what can leave, and who can access. Highest-stakes surface.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                CompanionAppsRow(
                    pendingCount = pendingCompanionCount,
                    approvedCount = approvedCompanionCount,
                    onClick = { onNavigate("companions") }
                )

                Spacer(Modifier.height(12.dp))
                Text("Tier", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = privacyTier == PrivacyTier.PRIVATE,
                        onClick = {
                            privacyTier = PrivacyTier.PRIVATE
                            saveTier(context, PrivacyTier.PRIVATE)
                            ContributionWorker.cancel(context)
                        },
                        label = { Text("Private") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = privacyTier == PrivacyTier.COMMUNITY,
                        onClick = {
                            privacyTier = PrivacyTier.COMMUNITY
                            saveTier(context, PrivacyTier.COMMUNITY)
                            ContributionWorker.enqueueNextContribution(context)
                        },
                        label = { Text("Community") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (privacyTier) {
                        PrivacyTier.PRIVATE -> "Your data never leaves this device. Zero-knowledge architecture."
                        PrivacyTier.COMMUNITY -> "Send anonymous aggregates. Off by default. Aggregation happens on-device before transmission — raw data never leaves."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))
                SettingsRow("Data Location", "On-device only")
                SettingsRow("Encryption", "AES-256 (SQLCipher)")

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onNavigate("privacy") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy Dashboard")
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete All Data")
                }
            }
        }

        // Data — owner's own data first (export/import is a one-tap autonomy
        // feature, kept prominent so owners can find it), then the wearable/API
        // connections that feed it. Both live behind their own screens so the
        // Self tab stays a short, scannable hub.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Data", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SettingsActionButton("Your data & export") { onNavigate("your_data") }
                Spacer(Modifier.height(4.dp))
                SettingsActionButton("Data & sources") { onNavigate("data_sources") }
            }
        }

        // App configuration (notifications, language, feedback, on-device
        // toggles, diagnostics, about) lives behind its own screen so the
        // Self tab stays focused on owner identity, privacy, and data.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SettingsActionButton("App settings") { onNavigate("app_settings") }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Data?") },
            text = { Text("This will permanently delete all your health data, baselines, and alerts from Bios. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.bios.app.platform.DataDestroyer.destroyAll(context)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

private fun saveTier(context: Context, tier: PrivacyTier) {
    context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE).edit().putString("privacy_tier", tier.name).apply()
}
