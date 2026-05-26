package com.bios.app.ui.reproductive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bios.app.data.ContraceptionEntryRepo
import com.bios.app.model.ContraceptionEntry
import com.bios.app.model.ContraceptionMethod
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Contraception (#209, OBGYN_POV §2.11).
 *
 * Pull-side surface for the owner to annotate which contraception method
 * they're on (or have been on). The list is owner-readable history; the
 * most-recent entry is the active method. Used by future engine passes
 * to suppress cycle-anomaly false-fires under hormonal contraception.
 *
 * Storage is the separately-encrypted [com.bios.app.data.ReproductiveDatabase]
 * — never the main DB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContraceptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { ContraceptionEntryRepo(context) }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<ContraceptionEntry>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        entries = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contraception") },
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
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ManifestoCard()
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add entry") }
            if (entries.isEmpty()) {
                Text(
                    "No entries yet. The most recent entry is the active method.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            onDelete = {
                                scope.launch {
                                    repo.delete(entry)
                                    refresh()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddContraceptionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { method, startDate, endDate, notes ->
                scope.launch {
                    repo.add(method, startDate, endDate, notes)
                    refresh()
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Why this matters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Hormonal contraception changes BBT pattern, cycle phase, HRV " +
                    "across cycle, and resting heart rate. Annotation is your tool: " +
                    "when Bios sees a cycle change, your record tells the engine " +
                    "(and your future self) that the cause was expected. Stored in " +
                    "the separately-encrypted reproductive database; never the main DB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryRow(entry: ContraceptionEntry, onDelete: () -> Unit) {
    val method = runCatching { ContraceptionMethod.valueOf(entry.method) }.getOrNull()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(method?.displayName ?: entry.method, fontWeight = FontWeight.Medium)
            Text(
                "Started ${dateFormat.format(Date(entry.startDate))}" +
                    (entry.endDate?.let { " — ended ${dateFormat.format(Date(it))}" } ?: " — ongoing"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContraceptionDialog(
    onDismiss: () -> Unit,
    onSave: (method: ContraceptionMethod, startDate: Long, endDate: Long?, notes: String?) -> Unit,
) {
    var method by remember { mutableStateOf(ContraceptionMethod.NONE) }
    var expanded by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add contraception entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = method.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        for (m in ContraceptionMethod.entries) {
                            DropdownMenuItem(
                                text = { Text(m.displayName) },
                                onClick = {
                                    method = m
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("Start date YYYY-MM-DD (blank = today)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("End date YYYY-MM-DD (blank = ongoing)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = parseDate(startText) ?: now
                val end = parseDate(endText)
                onSave(method, start, end, notes.trim().takeIf { it.isNotBlank() })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun parseDate(s: String): Long? =
    s.trim().takeIf { it.length == 10 }?.let {
        runCatching { dateFormat.parse(it)?.time }.getOrNull()
    }
