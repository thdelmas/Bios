package com.bios.app.ui.screening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.RiskProfileRepo
import com.bios.app.data.ScreeningEntryRepo
import com.bios.app.model.RiskProfile
import com.bios.app.screening.AnatomyProfile
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
    var anatomy by remember { mutableStateOf(demographicsStore.anatomy()) }
    var entries by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var riskProfile by remember { mutableStateOf<RiskProfile?>(null) }
    var showRecordDialog by remember { mutableStateOf<ScreeningCatalogEntry?>(null) }
    var showDemographicsDialog by remember { mutableStateOf(false) }
    var notApplicableExpanded by remember { mutableStateOf(false) }

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val now = remember { System.currentTimeMillis() }

    suspend fun refresh() {
        // Most-recent-per-key, merging the manual ledger with dates Bios can
        // derive from existing health data (e.g. a BP reading satisfies the
        // blood-pressure check). Shared with the Settings due badge so both
        // compute "what's due" the same way (#400).
        entries = PreventiveCareData.latestPerformedByKey(context)
        riskProfile = riskRepo.fetch()
    }

    LaunchedEffect(Unit) { refresh() }

    val demographics = birthYear?.let {
        com.bios.app.screening.OwnerDemographics(
            ageYears = (currentYear - it).coerceAtLeast(0),
            // presentsAs remains null in the new anatomy-direct flow;
            // the engine consults `anatomy` first and only falls back
            // to `presentsAs` for legacy installs (#209).
            presentsAs = null,
            anatomy = anatomy,
        )
    }

    // Build the chronological timeline once per recomposition from the
    // engine's per-entry statuses. Risk-gated hereditary entries the owner
    // doesn't qualify for are dropped first — showing the whole NCCN list to
    // every non-carrier would be noise; they reappear once the owner sets the
    // matching flag on the risk-profile screen.
    val timeline = demographics?.let { demo ->
        val statuses = ScreeningCadenceEngine.evaluateAll(
            catalog = ScreeningCatalog.combined,
            demographics = demo,
            latestByKey = { key ->
                entries[key]?.let { date ->
                    com.bios.app.model.ScreeningEntry(screeningKey = key, performedDate = date)
                }
            },
            riskProfile = riskProfile,
            now = now,
        )
        val visible = statuses.filterNot { (_, status) ->
            status is ScreeningStatus.NotEligible &&
                status.reason.startsWith("Requires owner-recorded")
        }
        com.bios.app.screening.PreventiveCareTimeline.build(visible, entries, now)
    } ?: emptyList()

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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "demographics") {
                DemographicsCard(
                    birthYear = birthYear,
                    anatomy = anatomy,
                    onEdit = { showDemographicsDialog = true },
                )
            }
            item(key = "manifesto") { ManifestoCard() }

            if (demographics == null) {
                item(key = "prompt") {
                    Text(
                        "Set your birth year above to see what's recommended for your age.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                preventiveCareTimeline(
                    items = timeline,
                    now = now,
                    notApplicableExpanded = notApplicableExpanded,
                    onToggleNotApplicable = { notApplicableExpanded = !notApplicableExpanded },
                    onRecord = { showRecordDialog = it },
                )
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
            initialAnatomy = anatomy,
            onDismiss = { showDemographicsDialog = false },
            onSave = { year, newAnatomy ->
                demographicsStore.setBirthYear(year)
                demographicsStore.setAnatomy(newAnatomy)
                // Clear any legacy presentsAs flag so the anatomy answers
                // are the single source of truth going forward.
                demographicsStore.setPresentsAs(null)
                birthYear = year
                anatomy = newAnatomy
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
                "Bios reads the USPSTF and WHO adult screening schedules, " +
                    "routine checkup intervals (dental, eye, periodic exam), and " +
                    "your own history, then lays them on one timeline: what's " +
                    "coming up sits above today, what you've done and are current " +
                    "on sits below. Routine checkups show a recommended delay " +
                    "since your last visit — never an 'overdue', because that's " +
                    "advice, not a deadline. Nothing here pushes a notification; " +
                    "the screen waits until you ask. Cadence math is just math — " +
                    "your provider applies the local guideline for diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DemographicsCard(
    birthYear: Int?,
    anatomy: AnatomyProfile,
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
                        val anatomySummary = anatomySummary(anatomy)
                        if (anatomySummary.isNotEmpty()) append(" • $anatomySummary")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) { Text(if (birthYear == null) "Set" else "Edit") }
        }
    }
}

private fun anatomySummary(anatomy: AnatomyProfile): String {
    if (anatomy.isAllUnset) return ""
    val parts = mutableListOf<String>()
    if (anatomy.hasBreastTissue == true) parts += "breast tissue"
    if (anatomy.hasCervix == true) parts += "cervix"
    if (anatomy.hasUterus == true) parts += "uterus"
    if (anatomy.hasProstate == true) parts += "prostate"
    if (parts.isEmpty()) return "anatomy noted"
    return parts.joinToString(", ")
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

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
