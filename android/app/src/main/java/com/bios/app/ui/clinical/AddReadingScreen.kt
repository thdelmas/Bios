package com.bios.app.ui.clinical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bios.app.data.ManualReadingContext
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.metricMatchesQuery
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single-metric entry screen reached from the global FAB or a metric
 * dashboard's "+ New entry" button. The owner picks one metric, types one
 * value, and the screen captures when + where the reading came from. After
 * save the value clears but the timestamp + source persist so a pharmacy
 * session of N readings is N quick taps rather than N round-trips through
 * the navigation stack.
 *
 * Writes route through [com.bios.app.data.ManualReadingRepo] → SELF_REPORTED
 * so single manual points don't poison sensor baselines
 * (docs/SELF_REPORTED_DATA_HOME.md decision 3).
 *
 * Lab biomarkers stay on the dedicated `BiomarkerEntryScreen` (specimen +
 * fasting + lab name provenance is richer than this surface) and
 * reproductive metrics on their isolated repos — domain filter mirrors
 * [ClinicalReadingRows.clinicalEntryMetrics].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    initialMetric: MetricType? = null,
) {
    val scope = rememberCoroutineScope()
    val repo = viewModel.manualReadingRepo

    var selectedMetric by remember { mutableStateOf<MetricType?>(initialMetric) }
    var valueText by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var source by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var savedCount by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val canSave by remember {
        derivedStateOf {
            !saving && selectedMetric != null && valueText.toDoubleOrNull() != null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (savedCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        "Saved $savedCount reading${if (savedCount == 1) "" else "s"} in this session. " +
                            "Add another below, or go back when done.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            MetricSearchPicker(
                selected = selectedMetric,
                onSelect = { selectedMetric = it; valueText = "" },
            )

            selectedMetric?.let { metric ->
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { input ->
                        valueText = input.filter { c -> c.isDigit() || c == '.' }
                    },
                    label = { Text("Value") },
                    suffix = { Text(metric.unit.symbol.ifEmpty { "—" }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                "When",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(formatDate(timestamp))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(formatTime(timestamp))
                }
            }

            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text("Where from") },
                placeholder = { Text("e.g. Pharmacy scale, home cuff, Dr. Smith") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Button(
                enabled = canSave,
                onClick = {
                    val metric = selectedMetric ?: return@Button
                    val parsed = valueText.toDoubleOrNull() ?: return@Button
                    saving = true
                    val capturedAt = timestamp
                    val capturedSource = source.trim().ifBlank { null }
                    val capturedNote = note.trim().ifBlank { null }
                    scope.launch {
                        repo.add(
                            metricType = metric,
                            value = parsed,
                            timestamp = capturedAt,
                            context = ManualReadingContext(
                                clinicName = capturedSource,
                                note = capturedNote,
                            ),
                        )
                        savedCount += 1
                        // Clear the per-reading fields, keep when + source so a
                        // pharmacy / clinic session of N readings stays fast.
                        selectedMetric = null
                        valueText = ""
                        note = ""
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save and add another")
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        timestamp = combineDateAndTime(date = picked, time = timestamp)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val cal = remember(timestamp) {
            Calendar.getInstance().apply { timeInMillis = timestamp }
        }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    timestamp = setHourMinute(timestamp, state.hour, state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        }
    }
}

@Composable
private fun MetricSearchPicker(
    selected: MetricType?,
    onSelect: (MetricType?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by remember {
        derivedStateOf {
            val universe = manualEntryUniverse()
            if (query.isBlank()) universe.take(20)
            else universe.filter { metricMatchesQuery(it, query) }.take(30)
        }
    }

    Text(
        "What",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )

    if (selected != null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column {
                    Text(selected.readableName, style = MaterialTheme.typography.bodyLarge)
                    val unitSymbol = selected.unit.symbol.ifEmpty { "—" }
                    Text(unitSymbol, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { query = ""; onSelect(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Change metric")
                }
            }
        }
    } else {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search metric") },
            placeholder = { Text("Weight, HRV, ApoB, BMI…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (results.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(results) { metric ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(metric)
                                    query = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(metric.readableName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                metric.unit.symbol.ifEmpty { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The universe of metrics the search picker offers — every manual-entry
 * key the contract allows except the domains whose provenance shape this
 * screen can't capture: BIOMARKER (lab specimen / fasting fields live on
 * `BiomarkerEntryScreen`) and WOMENS_HEALTH (rows belong in the isolated
 * `ReproductiveDatabase` via BBT/Period repos). SLEEP stays in — its
 * dedicated `SleepEntryScreen` writes to the same `metric_readings` table
 * with no isolation, so excluding it just buried the metric one nav level
 * deeper when an owner needed a quick override (e.g. wearable missed a
 * night and they want to log it from the FAB).
 */
internal fun manualEntryUniverse(): List<MetricType> =
    MetricType.entries
        .filter {
            it.allowsManualEntry &&
                it.domain != MetricDomain.BIOMARKER &&
                it.domain != MetricDomain.WOMENS_HEALTH
        }
        .sortedBy { it.readableName }

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(epochMs))

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun combineDateAndTime(date: Long, time: Long): Long {
    val datePart = Calendar.getInstance().apply { timeInMillis = date }
    val timePart = Calendar.getInstance().apply { timeInMillis = time }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, datePart.get(Calendar.YEAR))
        set(Calendar.MONTH, datePart.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, datePart.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, timePart.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, timePart.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun setHourMinute(epochMs: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
