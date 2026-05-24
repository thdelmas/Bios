package com.bios.app.ui.seizure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.SeizureDetectionPrefs
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pull-side surface for SEIZURE_EVENT rows (#269 Cut 3c). Lists every
 * SEIZURE_EVENT in the database, differentiated by `detection_source`:
 *
 *  - **Wearable-detected** — written by [com.bios.app.ingest.SeizureDetectionService].
 *    Carries the peri-event HR substrate (median + baseline + percentage
 *    rise) so the owner can see the actual ratio behind the detection.
 *  - **Owner-logged** — bare MetricReading rows or rows with an explicit
 *    `detection_source = "owner_logged"` payload field. Reserved space
 *    for the future owner-logging entry surface.
 *
 * Read-only this cut. Manual-log entry is a separate UX design
 * decision (timestamp/duration/character/aura/post-ictal fields per
 * NEUROLOGY_POV §2.12) and lives in a follow-up. The view-only screen
 * is enough to close the manifesto gap from #289: the wearable
 * detector now writes rows the owner can actually see.
 *
 * Reached from [com.bios.app.ui.settings.SettingsSeizureDetectionCard].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeizureTimelineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { BiosDatabase.getInstance(context) }
    var readings by remember { mutableStateOf<List<MetricReading>>(emptyList()) }
    var payloadsById by remember { mutableStateOf<Map<String, List<com.bios.app.model.EventPayloadField>>>(emptyMap()) }
    val detectorOn = remember(context) { SeizureDetectionPrefs.isEnabled(context) }

    LaunchedEffect(Unit) {
        // Fetch a wide window — SEIZURE_EVENT rows are rare; the cost of
        // scanning 90 days is negligible and the owner-visible
        // timeline benefits from showing historical context.
        val end = System.currentTimeMillis()
        val start = end - 90L * 24 * 60 * 60 * 1000
        val readingDao = db.metricReadingDao()
        val payloadDao = db.eventPayloadFieldDao()
        val rows = readingDao
            .fetch(MetricType.SEIZURE_EVENT.key, start, end)
            .sortedByDescending { it.timestamp }
        readings = rows
        payloadsById = payloadDao
            .fetchForReadings(rows.map { it.id })
            .groupBy { it.readingId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seizure detections") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SeizureTimelineHeader(detectorOn) }
            if (readings.isEmpty()) {
                item { SeizureTimelineEmpty(detectorOn) }
            } else {
                items(readings, key = { it.id }) { reading ->
                    val payload = payloadsById[reading.id] ?: emptyList()
                    SeizureTimelineRow(
                        reading = reading,
                        source = SeizureSourceClassifier.classify(payload),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeizureTimelineHeader(detectorOn: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "What this screen shows",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Every SEIZURE_EVENT recorded in Bios — owner-logged entries plus, when the " +
                    "wearable detector is enabled, candidates flagged by the accelerometer + " +
                    "heart-rate corroborator. Wearable-detected events are screening-grade " +
                    "(LOW confidence) and never page emergency services on their own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (detectorOn) "Wearable detector: ON" else "Wearable detector: OFF",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeizureTimelineEmpty(detectorOn: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (detectorOn) {
                    "No detections yet. The detector is watching while charging-independent " +
                        "background sensing is active."
                } else {
                    "No detections yet. Enable the wearable detector from Settings to start " +
                        "background watching, or log a seizure event manually from a future " +
                        "owner-entry surface."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SeizureTimelineRow(
    reading: MetricReading,
    source: DetectionSource,
) {
    val timestampFormat = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    timestampFormat.format(Date(reading.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                SourceChip(source)
            }
            reading.durationSec?.let { sec ->
                val mins = sec / 60
                val secs = sec % 60
                val durationText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                Text(
                    "Duration: $durationText",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (source is DetectionSource.WearableInferred) {
                val sub = source.substrate
                val median = sub.medianHrBpm
                val baseline = sub.baselineHrBpm
                val rise = sub.risePercent
                if (median != null && baseline != null && rise != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Peri-event HR: ${median.toInt()} bpm (baseline ${baseline.toInt()}, " +
                            "+$rise%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: DetectionSource) {
    val label = when (source) {
        is DetectionSource.WearableInferred -> "Wearable-detected"
        DetectionSource.OwnerLogged -> "Owner-logged"
        DetectionSource.OwnerLoggedImplicit -> "Owner-logged"
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}
