package com.bios.app.ui.companions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.CompanionGrant
import com.bios.app.provider.CompanionGate
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Companion Apps — the owner reviews, approves, and revokes other apps' access
 * to Bios health data. Pending rows appear here automatically when a not-yet-
 * approved app calls the BiosHealthProvider.
 *
 * Manifesto: "the owner is final" — no companion reads or writes without an
 * explicit approval here. Revoke is one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val grants by viewModel.db.companionGrantDao()
        .observeAll()
        .collectAsState(initial = emptyList())
    val gate = remember { CompanionGate(viewModel.db.companionGrantDao()) }

    val pending = grants.filter { it.state == CompanionGrant.STATE_PENDING }
    val granted = grants.filter { it.state == CompanionGrant.STATE_GRANTED }
    val revoked = grants.filter { it.state == CompanionGrant.STATE_REVOKED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Companion Apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Apps that ask to read or write Bios health data appear here. Nothing is " +
                        "shared until you approve it. Revoke any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (grants.isEmpty()) {
                EmptyState()
            }

            if (pending.isNotEmpty()) {
                SectionCard(
                    title = "Pending — awaiting your decision",
                    emphasis = true
                ) {
                    pending.forEach { grant ->
                        GrantRow(
                            grant = grant,
                            primaryLabel = "Approve",
                            primaryAction = { scope.launch { gate.approve(grant.packageName) } },
                            secondaryLabel = "Block",
                            secondaryAction = { scope.launch { gate.revoke(grant.packageName) } }
                        )
                    }
                }
            }

            if (granted.isNotEmpty()) {
                SectionCard(title = "Approved") {
                    granted.forEach { grant ->
                        GrantRow(
                            grant = grant,
                            primaryLabel = "Revoke",
                            primaryAction = { scope.launch { gate.revoke(grant.packageName) } }
                        )
                    }
                }
            }

            if (revoked.isNotEmpty()) {
                SectionCard(title = "Blocked") {
                    revoked.forEach { grant ->
                        GrantRow(
                            grant = grant,
                            primaryLabel = "Re-approve",
                            primaryAction = { scope.launch { gate.approve(grant.packageName) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("No companion apps yet.", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "When an app first tries to read or write Bios data, it will show up here " +
                        "for your review.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    emphasis: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val container = if (emphasis) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun GrantRow(
    grant: CompanionGrant,
    primaryLabel: String,
    primaryAction: () -> Unit,
    secondaryLabel: String? = null,
    secondaryAction: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(grant.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            buildMetaLine(grant),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = primaryAction) { Text(primaryLabel) }
            if (secondaryLabel != null && secondaryAction != null) {
                OutlinedButton(onClick = secondaryAction) { Text(secondaryLabel) }
            }
        }
    }
}

private fun buildMetaLine(grant: CompanionGrant): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val firstSeen = "first seen ${fmt.format(Date(grant.firstSeenAt))}"
    val access = if (grant.accessCount > 0) " · ${grant.accessCount} access attempt${if (grant.accessCount == 1L) "" else "s"}" else ""
    val last = grant.lastAccessAt?.let { " · last ${fmt.format(Date(it))}" } ?: ""
    return "$firstSeen$access$last"
}
