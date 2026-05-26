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
import com.bios.app.data.GenderAffirmingCareEntryRepo
import com.bios.app.model.GenderAffirmingCareEntry
import com.bios.app.model.GenderAffirmingHormoneType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Gender-affirming care (#209).
 *
 * The owner records what they're taking — estrogen, testosterone, anti-
 * androgens, progesterone — and Bios uses that to interpret baseline
 * signals correctly. There is no gender-identity field. The Indigenous
 * Americas audit (§2.4 two-spirit / nádleehi / winkte / lhamana) named
 * the imposition of a binary gender model as itself a design defect;
 * Bios stores hormones, not labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenderAffirmingCareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { GenderAffirmingCareEntryRepo(context) }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<GenderAffirmingCareEntry>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        entries = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gender-affirming care") },
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
            ) { Text("Add hormone entry") }
            if (entries.isEmpty()) {
                Text(
                    "No entries yet.",
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
        AddDialog(
            onDismiss = { showAddDialog = false },
            onSave = { type, startDate, endDate, dose, notes ->
                scope.launch {
                    repo.add(type, startDate, endDate, dose, notes)
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
            Text("Hormones, not labels", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bios stores what you're taking, not who you are. Hormone class " +
                    "matters for baseline interpretation: estrogen affects HRV, " +
                    "body-temperature pattern, bone-density trend; testosterone " +
                    "raises haematocrit and resting HR. The owner records the " +
                    "data; the owner is final on identity. Stored in the " +
                    "separately-encrypted reproductive database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryRow(entry: GenderAffirmingCareEntry, onDelete: () -> Unit) {
    val type = runCatching { GenderAffirmingHormoneType.valueOf(entry.hormoneType) }.getOrNull()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(type?.displayName ?: entry.hormoneType, fontWeight = FontWeight.Medium)
            Text(
                "Started ${dateFormat.format(Date(entry.startDate))}" +
                    (entry.endDate?.let { " — ended ${dateFormat.format(Date(it))}" } ?: " — ongoing"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.dose?.let { Text("Dose: $it", style = MaterialTheme.typography.bodySmall) }
            entry.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onSave: (type: GenderAffirmingHormoneType, startDate: Long, endDate: Long?, dose: String?, notes: String?) -> Unit,
) {
    var type by remember { mutableStateOf(GenderAffirmingHormoneType.ESTROGEN) }
    var expanded by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add hormone entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = type.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hormone") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        for (t in GenderAffirmingHormoneType.entries) {
                            DropdownMenuItem(
                                text = { Text(t.displayName) },
                                onClick = {
                                    type = t
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
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dose (optional)") },
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
                val start = parseDateGac(startText) ?: now
                val end = parseDateGac(endText)
                onSave(
                    type,
                    start,
                    end,
                    dose.trim().takeIf { it.isNotBlank() },
                    notes.trim().takeIf { it.isNotBlank() },
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun parseDateGac(s: String): Long? =
    s.trim().takeIf { it.length == 10 }?.let {
        runCatching { dateFormat.parse(it)?.time }.getOrNull()
    }
