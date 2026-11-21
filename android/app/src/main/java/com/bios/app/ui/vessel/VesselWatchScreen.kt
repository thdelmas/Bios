package com.bios.app.ui.vessel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.ui.AppViewModel

/**
 * The Vessel Watch — a read-only, owner-subject surface over the dials that
 * read the body/agency substrate. Spec: docs/subjects/owner/VESSEL_WATCH.md.
 *
 * Manifesto guarantees enforced structurally here:
 *  - **No composite score.** The screen only ever lays out per-dial cards from
 *    [vesselWatchGroups]; nothing sums them. See [VesselDial].
 *  - **Pull, not push.** It reads on open and stays silent — no alerts, no
 *    nudges, no notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VesselWatchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val lastSync by viewModel.ingestManager.lastSyncTime.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vessel Watch") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "The long dial — the body as the one carrier of agency that can't be " +
                    "backed up. Each reading stands alone; you do the reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            for (group in vesselWatchGroups) {
                GroupSection(group = group, viewModel = viewModel, refreshKey = lastSync)
            }
        }
    }
}

/** One spec group (A–F): heading + honest blurb + its dials. */
@Composable
private fun GroupSection(
    group: VesselGroup,
    viewModel: AppViewModel,
    refreshKey: Long?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            group.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            group.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (dial in group.dials) {
            VesselWatchDialCard(dial = dial, viewModel = viewModel, refreshKey = refreshKey)
        }
    }
}
