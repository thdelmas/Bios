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
import com.bios.app.data.TreatmentCourseRepo
import com.bios.app.model.MedicalTradition
import com.bios.app.model.TreatmentCourse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Identity → Treatment courses. Owner records multi-session
 * series (Panchakarma block, cardiac rehab phase II, chiropractic
 * course) (#192).
 *
 * **Manifesto stance.** The course is bookkeeping metadata. Bios does
 * not track adherence, does not remind owners of remaining sessions,
 * does not flag owners as "behind". Closing a course is an explicit
 * owner action; an open course with an expectedEndAt in the past just
 * means the owner hasn't closed it yet — Bios doesn't nudge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentCoursesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) {
        TreatmentCourseRepo(BiosDatabase.getInstance(context))
    }
    val scope = rememberCoroutineScope()

    var courses by remember { mutableStateOf<List<TreatmentCourse>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        courses = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treatment courses") },
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
                        "A treatment course groups individual sessions — a " +
                            "14-day Panchakarma block, cardiac rehab phase " +
                            "II, a 12-session chiropractic course. Add a " +
                            "course, then attach intervention events to it " +
                            "from the Intervention events screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add course")
                    }
                }
            }

            if (courses.isEmpty()) {
                Text(
                    "No courses on record. Tap \"Add course\" to create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(courses, key = { it.id }) { course ->
                        CourseRow(
                            course = course,
                            onClose = {
                                scope.launch {
                                    repo.close(course.id)
                                    refresh()
                                }
                            },
                            onReopen = {
                                scope.launch {
                                    repo.reopen(course.id)
                                    refresh()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repo.remove(course.id)
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
        AddCourseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { label, started, expected, tradition, goal, notes ->
                scope.launch {
                    repo.add(
                        label = label,
                        startedAt = started,
                        tradition = tradition,
                        expectedEndAt = expected,
                        goal = goal,
                        notes = notes,
                    )
                    refresh()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CourseRow(
    course: TreatmentCourse,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
) {
    val open = course.actualEndAt == null
    var showActions by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (open) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    course.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (open) "Open" else "Closed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (open) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("Started ${formatDate(course.startedAt)}")
                    course.expectedEndAt?.let { append(" — expected ${formatDate(it)}") }
                    course.actualEndAt?.let { append(" — closed ${formatDate(it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            course.tradition?.let {
                Text(
                    "Tradition: ${it.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            course.goal?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Goal: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            course.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showActions = !showActions }) {
                Text(if (showActions) "Hide actions" else "Actions")
            }
            if (showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (open) {
                        OutlinedButton(onClick = onClose) { Text("Mark closed") }
                    } else {
                        OutlinedButton(onClick = onReopen) { Text("Reopen") }
                    }
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCourseDialog(
    onDismiss: () -> Unit,
    onSave: (
        label: String,
        startedAt: Long,
        expectedEndAt: Long?,
        tradition: MedicalTradition?,
        goal: String?,
        notes: String?,
    ) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tradition by remember { mutableStateOf<MedicalTradition?>(null) }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expectedEndAt by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var traditionMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add course") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text("e.g. Panchakarma — Spring 2026") },
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
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Goal (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Started on: ${formatDate(startedAt)}")
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Expected end: " + (expectedEndAt?.let { formatDate(it) } ?: "not set")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        label.trim(),
                        startedAt,
                        expectedEndAt,
                        tradition,
                        goal.trim().takeIf { it.isNotBlank() },
                        notes.trim().takeIf { it.isNotBlank() },
                    )
                },
                enabled = label.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startedAt)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startedAt = it }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expectedEndAt ?: startedAt)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expectedEndAt = state.selectedDateMillis
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    expectedEndAt = null
                    showEndPicker = false
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
