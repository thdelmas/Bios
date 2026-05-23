package com.bios.app.ui.screening

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.RiskProfileRepo
import com.bios.app.data.ScreeningEntryRepo
import com.bios.app.model.RiskProfile
import com.bios.app.screening.Applicability
import com.bios.app.screening.OwnerDemographicsStore
import com.bios.app.screening.ScreeningCadenceEngine
import com.bios.app.screening.ScreeningCatalog
import com.bios.app.screening.ScreeningCatalogEntry
import com.bios.app.screening.ScreeningStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Settings → Preventive Care. Closes audit gap §2.2.
 *
 * Pull-side surface. Owner navigates in to ask "what am I due for"; the
 * screen reads the static USPSTF catalog + the owner's screening history
 * + their birth year / anatomy presentation and renders one card per
 * recommended screening with a status (current / due now / no record /
 * not eligible).
 *
 * No notifications fire from this screen. Silence remains a feature; the
 * owner reads what the catalog says, the owner acts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreventiveCareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { ScreeningEntryRepo(BiosDatabase.getInstance(context)) }
    val riskRepo = remember(context) { RiskProfileRepo(BiosDatabase.getInstance(context)) }
    val demographicsStore = remember(context) { OwnerDemographicsStore(context) }
    val scope = rememberCoroutineScope()

    var birthYear by remember { mutableStateOf(demographicsStore.birthYear()) }
    var presentsAs by remember { mutableStateOf(demographicsStore.presentsAs()) }
    var entries by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var riskProfile by remember { mutableStateOf<RiskProfile?>(null) }
    var showRecordDialog by remember { mutableStateOf<ScreeningCatalogEntry?>(null) }
    var showDemographicsDialog by remember { mutableStateOf(false) }

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    suspend fun refresh() {
        val all = repo.fetchAll()
        // Group most-recent per key.
        entries = all.groupBy { it.screeningKey }
            .mapValues { (_, list) -> list.maxOfOrNull { it.performedDate } }
        riskProfile = riskRepo.fetch()
    }

    LaunchedEffect(Unit) { refresh() }

    val demographics = birthYear?.let {
        com.bios.app.screening.OwnerDemographics(
            ageYears = (currentYear - it).coerceAtLeast(0),
            presentsAs = presentsAs,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preventive Care") },
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
            DemographicsCard(
                birthYear = birthYear,
                presentsAs = presentsAs,
                onEdit = { showDemographicsDialog = true },
            )
            ManifestoCard()

            if (demographics == null) {
                Text(
                    "Set your birth year above to see what's recommended for your age.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val statuses = ScreeningCadenceEngine.evaluateAll(
                    catalog = ScreeningCatalog.combined,
                    demographics = demographics,
                    latestByKey = { key ->
                        entries[key]?.let { date ->
                            com.bios.app.model.ScreeningEntry(
                                screeningKey = key,
                                performedDate = date,
                            )
                        }
                    },
                    riskProfile = riskProfile,
                )
                // Hide risk-gated entries the owner doesn't qualify for —
                // showing the entire NCCN hereditary list to every
                // non-carrier owner would be noise. The owner's
                // risk-profile screen is the entry point to surface
                // these; once a syndrome flag is set, the engine
                // returns a non-`Requires owner-recorded ...` status
                // and the row appears here.
                val visible = statuses.filterNot { (_, status) ->
                    status is ScreeningStatus.NotEligible &&
                        status.reason.startsWith("Requires owner-recorded")
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(visible, key = { it.first.key }) { (entry, status) ->
                        ScreeningRow(
                            entry = entry,
                            status = status,
                            onRecord = { showRecordDialog = entry },
                        )
                    }
                }
            }
        }
    }

    showRecordDialog?.let { entry ->
        RecordScreeningDialog(
            entry = entry,
            onDismiss = { showRecordDialog = null },
            onSave = { date, note ->
                scope.launch {
                    repo.add(entry.key, date, note)
                    refresh()
                }
                showRecordDialog = null
            }
        )
    }

    if (showDemographicsDialog) {
        DemographicsDialog(
            initialBirthYear = birthYear,
            initialPresentsAs = presentsAs,
            onDismiss = { showDemographicsDialog = false },
            onSave = { year, applicability ->
                demographicsStore.setBirthYear(year)
                demographicsStore.setPresentsAs(applicability)
                birthYear = year
                presentsAs = applicability
                showDemographicsDialog = false
            },
        )
    }
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How Bios uses this", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bios reads the USPSTF adult screening schedule and your own " +
                    "history to answer one question: what are you due for? " +
                    "Nothing here pushes a notification; the screen waits until " +
                    "you ask. Cadence math is just math — your provider applies " +
                    "the local guideline for diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DemographicsCard(
    birthYear: Int?,
    presentsAs: Applicability?,
    onEdit: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Your demographics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(birthYear?.let { "Born $it" } ?: "Birth year not set")
                        presentsAs?.let { append(" • ${applicabilityLabel(it)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) { Text(if (birthYear == null) "Set" else "Edit") }
        }
    }
}

@Composable
private fun ScreeningRow(
    entry: ScreeningCatalogEntry,
    status: ScreeningStatus,
    onRecord: () -> Unit,
) {
    val statusText = when (status) {
        is ScreeningStatus.NotEligible -> status.reason
        ScreeningStatus.NoRecord -> "No record yet"
        is ScreeningStatus.Current ->
            "Last ${status.monthsSinceLast} mo ago — next in ${status.monthsUntilDue} mo"
        is ScreeningStatus.DueNow ->
            if (status.monthsOverdue > 0)
                "Overdue by ${status.monthsOverdue} mo"
            else "Due now (last ${status.monthsSinceLast} mo ago)"
    }
    val statusColor = when (status) {
        is ScreeningStatus.DueNow -> MaterialTheme.colorScheme.error
        ScreeningStatus.NoRecord -> MaterialTheme.colorScheme.secondary
        is ScreeningStatus.Current -> MaterialTheme.colorScheme.primary
        is ScreeningStatus.NotEligible -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
            Text(
                "Cadence: every ${cadenceLabel(entry.cadenceMonths)} • ${ageLabel(entry)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status !is ScreeningStatus.NotEligible) {
                OutlinedButton(
                    onClick = onRecord,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Record date") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordScreeningDialog(
    entry: ScreeningCatalogEntry,
    onDismiss: () -> Unit,
    onSave: (date: Long, note: String?) -> Unit,
) {
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record ${entry.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.citation, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Performed on: ${formatDate(date)}") }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, note.trim().takeIf { it.isNotBlank() }) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DemographicsDialog(
    initialBirthYear: Int?,
    initialPresentsAs: Applicability?,
    onDismiss: () -> Unit,
    onSave: (Int?, Applicability?) -> Unit,
) {
    var yearText by remember { mutableStateOf(initialBirthYear?.toString() ?: "") }
    var presentsAs by remember { mutableStateOf(initialPresentsAs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demographics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { v -> yearText = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Birth year (e.g. 1985)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Anatomy presentation", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Used so the catalog hides screenings that don't apply " +
                        "(mammogram, AAA). Leave unset to see them all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresentsAsChip("Female-bodied", Applicability.PRESENTS_AS_FEMALE, presentsAs) { presentsAs = it }
                    PresentsAsChip("Male-bodied", Applicability.PRESENTS_AS_MALE, presentsAs) { presentsAs = it }
                    PresentsAsChip("Unset", null, presentsAs) { presentsAs = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(yearText.toIntOrNull()?.takeIf { it in 1900..Calendar.getInstance().get(Calendar.YEAR) }, presentsAs)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PresentsAsChip(
    label: String,
    target: Applicability?,
    current: Applicability?,
    onClick: (Applicability?) -> Unit,
) {
    val selected = current == target
    OutlinedButton(
        onClick = { onClick(target) },
        colors = if (selected) {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
    ) { Text(label) }
}

private fun applicabilityLabel(a: Applicability): String = when (a) {
    Applicability.UNIVERSAL -> "All bodies"
    Applicability.PRESENTS_AS_FEMALE -> "Female-bodied"
    Applicability.PRESENTS_AS_MALE -> "Male-bodied"
}

private fun cadenceLabel(months: Int): String = when {
    months == Int.MAX_VALUE -> "one-time"
    months % 12 == 0 -> "${months / 12} yr"
    else -> "$months mo"
}

private fun ageLabel(entry: ScreeningCatalogEntry): String =
    if (entry.maxAge != null) "ages ${entry.minAge}–${entry.maxAge}"
    else "from age ${entry.minAge}"

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
