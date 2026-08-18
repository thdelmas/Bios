package com.bios.app.ui.period

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.bios.app.data.PeriodEntryRepo
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodEntryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenDashboard: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember(context) { PeriodEntryRepo(context) }
    val scope = rememberCoroutineScope()

    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<MetricReading>>(emptyList()) }
    var latestCycleDay by remember { mutableStateOf<MetricReading?>(null) }

    suspend fun refresh() {
        recent = repo.fetchRecentOnsets()
        latestCycleDay = repo.fetchLatestCycleDay()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Period Start") },
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
            OnsetLogCard(
                selectedDate = selectedDate,
                onPickDate = { showDatePicker = true },
                onSave = {
                    scope.launch {
                        repo.addOnset(selectedDate)
                        refresh()
                    }
                }
            )

            latestCycleDay?.let { CycleDayCard(it) }

            OutlinedButton(
                onClick = onOpenDashboard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cycle calendar & lengths")
            }

            RecentOnsets(recent)
        }
    }

    if (showDatePicker) {
        OnsetDatePickerDialog(
            initialMillis = selectedDate,
            onConfirm = { selectedDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun CycleDayCard(day: MetricReading) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Cycle day ${day.value.toInt()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Anchored on the most recent onset you logged",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RecentOnsets(recent: List<MetricReading>) {
    Text("Recent onsets", style = MaterialTheme.typography.titleSmall)
    if (recent.isEmpty()) {
        Text(
            "No onsets logged yet. Each entry resets the cycle-day counter to 1.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(recent, key = { it.id }) { onset ->
                RecentOnsetRow(onset)
            }
        }
    }
}

/** The onset-logging card: date choice + save. Extracted for the method-length limit. */
@Composable
private fun OnsetLogCard(
    selectedDate: Long,
    onPickDate: () -> Unit,
    onSave: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Menstruation onset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Logging the first day of your period anchors cycle-day numbering " +
                    "(day 1 = first day of menstruation) and marks the first five days as " +
                    "the menstrual phase. Stays on device in the reproductive database, " +
                    "never used by the sensor baseline engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(onClick = onPickDate, modifier = Modifier.fillMaxWidth()) {
                Text("Period started on: ${formatDate(selectedDate)}")
            }

            OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnsetDatePickerDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onConfirm) ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun RecentOnsetRow(reading: MetricReading) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                formatDate(reading.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Period start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
