package com.bios.app.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val results by viewModel.diagnosticResults.collectAsState()
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshDiagnostics()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Health Diagnostics") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (dataAge < BaselineEngine.MINIMUM_DATA_DAYS) {
            // Insufficient data state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.HourglassBottom,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Building Your Baseline",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    val remaining = BaselineEngine.MINIMUM_DATA_DAYS - dataAge
                    Text(
                        "Diagnostics require at least ${BaselineEngine.MINIMUM_DATA_DAYS} days of data to establish your personal baselines. $remaining more day${if (remaining == 1) "" else "s"} needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { dataAge.toFloat() / BaselineEngine.MINIMUM_DATA_DAYS.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results) { result ->
                    DiagnosticCard(result, onNavigateToDetail = onNavigateToDetail)
                }
            }
        }
    }
}
