package com.bios.app.ui.traditional

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.TraditionalMedicineContextRepo
import com.bios.app.model.MedicalTradition
import com.bios.app.model.PractitionerFrequency
import com.bios.app.model.TraditionalMedicineContext
import kotlinx.coroutines.launch

/**
 * Settings → Identity → Traditional-medicine context (#193). Multi-row
 * editor where the owner records the traditions they practice within.
 *
 * Eleven audits converge on this surface (TCM, Ayurveda, Siddha, Sowa
 * Rigpa, Unani, Korean, Kampo, African Traditional, Indigenous
 * Americas, Oceanic/Arctic, Modern Non-Allopathic). The shape is the
 * same across all of them: owner-declared rows, no in-tradition
 * classification by Bios.
 *
 * Manifesto guard: pull-side only. Nothing on this screen pushes a
 * notification or modifies which patterns Bios detects. Only the
 * (deferred) alert-detail footnote — gated row-by-row by
 * [TraditionalMedicineContext.vocabularyOverlayConsent] — reads from
 * what is saved here. Default consent is **false**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraditionalMedicineContextScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) {
        TraditionalMedicineContextRepo(BiosDatabase.getInstance(context))
    }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<TraditionalMedicineContext>>(emptyList()) }
    var editing by remember { mutableStateOf<TraditionalMedicineContext?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun refresh() { entries = repo.fetchAll() }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traditional medicine") },
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
            ManifestoCard(onAdd = { showAddDialog = true })

            if (entries.isEmpty()) {
                Text(
                    "No tradition context recorded. Tap \"Add tradition\" to " +
                        "record a practice context — Bios will hold what you " +
                        "say about yourself without classifying within the tradition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        ContextRow(
                            entry = entry,
                            onEdit = { editing = entry },
                            onDelete = {
                                scope.launch {
                                    repo.remove(entry.id)
                                    refresh()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddOrEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { draft ->
                scope.launch {
                    repo.add(
                        tradition = draft.tradition,
                        traditionFreeText = draft.traditionFreeText,
                        practitionerConsultedFrequency = draft.practitionerConsultedFrequency,
                        notes = draft.notes,
                        regionOfPractice = draft.regionOfPractice,
                        vocabularyOverlayConsent = draft.vocabularyOverlayConsent,
                    )
                    refresh()
                }
                showAddDialog = false
            }
        )
    }

    editing?.let { row ->
        AddOrEditDialog(
            initial = row,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    repo.save(updated.copy(id = row.id, createdAt = row.createdAt))
                    refresh()
                }
                editing = null
            }
        )
    }
}

@Composable
private fun ManifestoCard(onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Why this matters",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Bios is the instrument you read; the traditions you practice " +
                    "within are part of how you read your own life. Record any " +
                    "tradition you want Bios to be aware of — a TCM practitioner " +
                    "and formula, an Ayurvedic routine, a curandera, Hoʻoponopono. " +
                    "Bios stores what you say about your practice and never " +
                    "classifies within the tradition: no dosha typing, no pulse " +
                    "classifier, no Sasang typing, no Mizaj inference. Recording " +
                    "is not endorsement; nothing here is inferred.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text("Add tradition")
            }
        }
    }
}

@Composable
private fun ContextRow(
    entry: TraditionalMedicineContext,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            val title = if (entry.tradition == MedicalTradition.OTHER && !entry.traditionFreeText.isNullOrBlank()) {
                entry.traditionFreeText
            } else {
                entry.tradition.displayName
            }
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            entry.practitionerConsultedFrequency?.let {
                Text(
                    "Practitioner: ${it.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.regionOfPractice?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Region: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (entry.vocabularyOverlayConsent) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Vocabulary overlay: on",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun AddOrEditDialog(
    initial: TraditionalMedicineContext?,
    onDismiss: () -> Unit,
    onSave: (TraditionalMedicineContext) -> Unit,
) {
    var tradition by remember { mutableStateOf(initial?.tradition ?: MedicalTradition.TCM) }
    var traditionFreeText by remember { mutableStateOf(initial?.traditionFreeText.orEmpty()) }
    var frequency by remember { mutableStateOf(initial?.practitionerConsultedFrequency) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var region by remember { mutableStateOf(initial?.regionOfPractice.orEmpty()) }
    var overlay by remember { mutableStateOf(initial?.vocabularyOverlayConsent ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add tradition" else "Edit tradition") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EnumDropdown(
                    label = "Tradition",
                    options = MedicalTradition.entries,
                    selected = tradition,
                    displayName = { it.displayName },
                    onSelect = { tradition = it },
                )
                if (tradition == MedicalTradition.OTHER) {
                    OutlinedTextField(
                        value = traditionFreeText,
                        onValueChange = { traditionFreeText = it },
                        label = { Text("Tradition name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                NullableFrequencyDropdown(
                    selected = frequency,
                    onSelect = { frequency = it },
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Region of practice (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Vocabulary overlay",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "When on, alert details may show one extra line using this tradition's vocabulary. Default off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = overlay, onCheckedChange = { overlay = it })
                }
            }
        },
        confirmButton = {
            val canSave = tradition != MedicalTradition.OTHER || traditionFreeText.isNotBlank()
            TextButton(
                onClick = {
                    onSave(
                        TraditionalMedicineContext(
                            id = initial?.id ?: "",
                            tradition = tradition,
                            traditionFreeText = traditionFreeText.takeIf {
                                tradition == MedicalTradition.OTHER && it.isNotBlank()
                            }?.trim(),
                            practitionerConsultedFrequency = frequency,
                            notes = notes.trim().takeIf { it.isNotBlank() },
                            regionOfPractice = region.trim().takeIf { it.isNotBlank() },
                            vocabularyOverlayConsent = overlay,
                        )
                    )
                },
                enabled = canSave,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NullableFrequencyDropdown(
    selected: PractitionerFrequency?,
    onSelect: (PractitionerFrequency?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Practitioner consulted", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    selected?.displayName ?: "Not specified",
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Not specified") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                for (option in PractitionerFrequency.entries) {
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    displayName: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(displayName(selected), modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (option in options) {
                    DropdownMenuItem(
                        text = { Text(displayName(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
