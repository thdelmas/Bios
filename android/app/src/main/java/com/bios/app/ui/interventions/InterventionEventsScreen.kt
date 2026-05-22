package com.bios.app.ui.interventions

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.bios.app.data.InterventionEventRepo
import com.bios.app.data.TreatmentCourseRepo
import com.bios.app.model.InterventionCategory
import com.bios.app.model.InterventionEvent
import com.bios.app.model.MedicalTradition
import com.bios.app.model.TreatmentCourse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Identity → Intervention events. Owner records discrete
 * intervention sessions — acupuncture, OMM, cupping, IV therapy,
 * ceremony, Reiki, PT visits, etc. (#192).
 *
 * **Manifesto stance.** This screen exists to hold what the owner
 * declares happened. It never validates efficacy, never compares
 * against any condition pattern, never prompts "did the treatment
 * work?". Recording is not endorsement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterventionEventsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { BiosDatabase.getInstance(context) }
    val repo = remember(db) { InterventionEventRepo(db) }
    val courseRepo = remember(db) { TreatmentCourseRepo(db) }
    val scope = rememberCoroutineScope()

    var events by remember { mutableStateOf<List<InterventionEvent>>(emptyList()) }
    var courses by remember { mutableStateOf<List<TreatmentCourse>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        events = repo.fetchAll()
        courses = courseRepo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intervention events") },
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What goes here?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Individual sessions you want on record — acupuncture, " +
                            "cupping, manual therapy, OMM, IV therapy, Reiki, " +
                            "ceremony, PT visits, and so on. Bios stores what " +
                            "you say happened. It never judges whether the " +
                            "intervention worked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add intervention")
                    }
                }
            }

            if (events.isEmpty()) {
                Text(
                    "Nothing on record. Tap \"Add intervention\" to log a session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(events, key = { it.id }) { ev ->
                        val courseLabel = ev.treatmentCourseId
                            ?.let { id -> courses.firstOrNull { it.id == id }?.label }
                        InterventionRow(
                            event = ev,
                            courseLabel = courseLabel,
                            onDelete = {
                                scope.launch {
                                    repo.remove(ev.id)
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
        AddInterventionDialog(
            availableCourses = courses,
            onDismiss = { showAddDialog = false },
            onSave = { category, ts, subType, tradition, region, notes, courseId ->
                scope.launch {
                    repo.add(
                        category = category,
                        timestamp = ts,
                        subType = subType,
                        practitionerTradition = tradition,
                        bodyRegion = region,
                        notes = notes,
                        treatmentCourseId = courseId,
                    )
                    refresh()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun InterventionRow(
    event: InterventionEvent,
    courseLabel: String?,
    onDelete: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    event.subType?.takeIf { it.isNotBlank() } ?: event.category.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatDate(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                event.category.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.practitionerTradition?.let {
                Text(
                    "Tradition: ${it.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            event.bodyRegion?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Region: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            courseLabel?.let {
                Text(
                    "Course: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            event.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showActions = !showActions }) {
                Text(if (showActions) "Hide actions" else "Actions")
            }
            if (showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInterventionDialog(
    availableCourses: List<TreatmentCourse>,
    onDismiss: () -> Unit,
    onSave: (
        category: InterventionCategory,
        timestamp: Long,
        subType: String?,
        tradition: MedicalTradition?,
        bodyRegion: String?,
        notes: String?,
        courseId: String?,
    ) -> Unit,
) {
    var category by remember { mutableStateOf(InterventionCategory.MANUAL_THERAPY) }
    var subType by remember { mutableStateOf("") }
    var tradition by remember { mutableStateOf<MedicalTradition?>(null) }
    var bodyRegion by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var courseId by remember { mutableStateOf<String?>(null) }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var traditionMenu by remember { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add intervention") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Category: ${category.displayName}")
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        InterventionCategory.entries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.displayName) },
                                onClick = { category = c; categoryMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subType,
                    onValueChange = { subType = it },
                    label = { Text("Specifics (optional)") },
                    placeholder = { Text("e.g. Tuina, Lomi lomi, OMM lumbar HVLA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(
                        onClick = { traditionMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tradition: ${tradition?.displayName ?: "not specified"}")
                    }
                    DropdownMenu(expanded = traditionMenu, onDismissRequest = { traditionMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("(none)") },
                            onClick = { tradition = null; traditionMenu = false }
                        )
                        MedicalTradition.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.displayName) },
                                onClick = { tradition = t; traditionMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = bodyRegion,
                    onValueChange = { bodyRegion = it },
                    label = { Text("Body region (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (availableCourses.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { courseMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = courseId?.let { id -> availableCourses.firstOrNull { it.id == id }?.label }
                            Text("Course: ${label ?: "(standalone)"}")
                        }
                        DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("(standalone)") },
                                onClick = { courseId = null; courseMenu = false }
                            )
                            availableCourses.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.label) },
                                    onClick = { courseId = c.id; courseMenu = false }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("On: ${formatDate(timestamp)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        category,
                        timestamp,
                        subType.trim().takeIf { it.isNotBlank() },
                        tradition,
                        bodyRegion.trim().takeIf { it.isNotBlank() },
                        notes.trim().takeIf { it.isNotBlank() },
                        courseId,
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { timestamp = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
