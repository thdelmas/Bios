package com.bios.app.ui.headache

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.HeadacheLogRepo
import com.bios.app.data.MigraineAttackRepo
import com.bios.app.model.HeadacheLog
import com.bios.app.model.MigraineAttack
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Headache diary (issue #207). The owner records headaches and migraine
 * attacks here and reads them back as a dated list.
 *
 * Two entry shapes live on the same screen:
 *  - **Migraine attack** ([MigraineAttack]): aura, triggers, peak
 *    intensity, acute treatment.
 *  - **Headache log** ([HeadacheLog]): intensity, the type the owner
 *    picks, duration.
 *
 * Pull-side only — no notification, no reminder, no streak. Entries are
 * shown as written; nothing here classifies or counts them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadacheDiaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { BiosDatabase.getInstance(context) }
    val migraineRepo = remember(db) { MigraineAttackRepo(db) }
    val headacheRepo = remember(db) { HeadacheLogRepo(db) }
    val scope = rememberCoroutineScope()

    var migraines by remember { mutableStateOf<List<MigraineAttack>>(emptyList()) }
    var headaches by remember { mutableStateOf<List<HeadacheLog>>(emptyList()) }
    var showAddMigraine by remember { mutableStateOf(false) }
    var showAddHeadache by remember { mutableStateOf(false) }

    suspend fun refresh() {
        migraines = migraineRepo.fetchAll()
        headaches = headacheRepo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Headache diary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showAddMigraine = true },
                                modifier = Modifier.weight(1f),
                            ) { Text("Log migraine attack") }
                            OutlinedButton(
                                onClick = { showAddHeadache = true },
                                modifier = Modifier.weight(1f),
                            ) { Text("Log headache") }
                        }
                    }
                }
            }

            if (migraines.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Migraine attacks",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(migraines, key = { "m-${it.id}" }) { attack ->
                    MigraineAttackRow(
                        attack = attack,
                        onDelete = {
                            scope.launch {
                                migraineRepo.remove(attack.id)
                                refresh()
                            }
                        },
                    )
                }
            }

            if (headaches.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Headaches",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(headaches, key = { "h-${it.id}" }) { log ->
                    HeadacheLogRow(
                        log = log,
                        onDelete = {
                            scope.launch {
                                headacheRepo.remove(log.id)
                                refresh()
                            }
                        },
                    )
                }
            }

            if (migraines.isEmpty() && headaches.isEmpty()) {
                item {
                    Text(
                        "No entries yet. Tap \"Log migraine attack\" or \"Log headache\" to record what you experienced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAddMigraine) {
        AddMigraineAttackDialog(
            onDismiss = { showAddMigraine = false },
            onSave = { onsetMs, peakIntensity, aura, triggers, medication, notes ->
                scope.launch {
                    migraineRepo.add(
                        onsetTimestamp = onsetMs,
                        peakIntensity = peakIntensity,
                        aura = aura,
                        triggers = triggers,
                        medicationTaken = medication,
                        notes = notes,
                    )
                    refresh()
                }
                showAddMigraine = false
            },
        )
    }

    if (showAddHeadache) {
        AddHeadacheLogDialog(
            onDismiss = { showAddHeadache = false },
            onSave = { timestampMs, intensity, type, durationMin, medication, notes ->
                scope.launch {
                    headacheRepo.add(
                        timestamp = timestampMs,
                        intensity = intensity,
                        type = type,
                        durationMinutes = durationMin,
                        medicationTaken = medication,
                        notes = notes,
                    )
                    refresh()
                }
                showAddHeadache = false
            },
        )
    }
}

@Composable
private fun MigraineAttackRow(attack: MigraineAttack, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDateTime(attack.onsetTimestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "NRS ${attack.peakIntensity}/10",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            if (attack.aura) {
                Text("with aura", style = MaterialTheme.typography.bodySmall)
            }
            if (attack.triggers.isNotEmpty()) {
                Text(
                    "Triggers: ${attack.triggers.joinToString(", ") { it.name.lowercase().replace('_', ' ') }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            attack.medicationTaken?.let {
                Text("Treatment: $it", style = MaterialTheme.typography.bodySmall)
            }
            attack.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun HeadacheLogRow(log: HeadacheLog, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDateTime(log.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${log.type.name.lowercase().replace('_', ' ')} · NRS ${log.intensity}/10",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            log.durationMinutes?.let {
                Text("Duration: $it min", style = MaterialTheme.typography.bodySmall)
            }
            log.medicationTaken?.let {
                Text("Treatment: $it", style = MaterialTheme.typography.bodySmall)
            }
            log.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.US)
internal fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))
