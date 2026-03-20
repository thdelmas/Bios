package com.bios.app.ui.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.AlertCard

@Composable
fun AlertsScreen(viewModel: AppViewModel) {
    val unacknowledged by viewModel.unacknowledgedAlerts.collectAsState()
    val recent by viewModel.recentAlerts.collectAsState()
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Alerts", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        if (recent.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No alerts yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (dataAge < BaselineEngine.MINIMUM_DATA_DAYS) {
                            "Bios is still building your personal baseline. Anomaly detection will activate once ${BaselineEngine.MINIMUM_DATA_DAYS} days of data have been collected."
                        } else {
                            "All your vitals are within your personal baseline. Bios will notify you if it detects any unusual patterns."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Unacknowledged
            if (unacknowledged.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Needs Attention",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Badge { Text("${unacknowledged.size}") }
                }

                unacknowledged.forEach { anomaly ->
                    AlertCard(
                        anomaly = anomaly,
                        onAcknowledge = { viewModel.acknowledgeAlert(anomaly.id) }
                    )
                }
            }

            // Recent / acknowledged
            val acknowledged = recent.filter { it.acknowledged }
            if (acknowledged.isNotEmpty()) {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                acknowledged.forEach { anomaly ->
                    AlertCard(
                        anomaly = anomaly,
                        onAcknowledge = {}
                    )
                }
            }
        }
    }
}
