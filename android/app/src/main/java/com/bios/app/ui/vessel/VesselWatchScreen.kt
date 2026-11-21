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
 * The Vessel Watch: a read-only page of dials, one measurement each, laid
 * out from [vesselWatchGroups]. Nothing sums them. It reads on open and
 * raises no alert or notification.
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
                "The latest value of each measurement, with its own trend. " +
                    "Each reading stands alone; you do the reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            for (group in vesselWatchGroups) {
                GroupSection(group = group, viewModel = viewModel, refreshKey = lastSync)
            }
        }
    }
}

/** One group: heading and its dials. */
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
        for (dial in group.dials) {
            VesselWatchDialCard(dial = dial, viewModel = viewModel, refreshKey = refreshKey)
        }
    }
}
