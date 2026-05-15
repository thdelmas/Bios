package com.bios.app.ui.bbt

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bios.app.data.BbtEntryRepo
import com.bios.app.model.CyclePhase
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbtEntryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val repo = remember(viewModel.db) { BbtEntryRepo(viewModel.db) }
    val scope = rememberCoroutineScope()

    var valueText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<MetricReading>>(emptyList()) }
    var latestPhase by remember { mutableStateOf<MetricReading?>(null) }

    suspend fun refresh() {
        recent = repo.fetchRecentBbts()
        latestPhase = repo.fetchLatestCyclePhase()
    }

    LaunchedEffect(Unit) { refresh() }

    val parsed = valueText.toDoubleOrNull()
    val isValid = parsed != null && parsed in 35.0..39.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Track BBT") },
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Basal body temperature", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Logged values feed the cycle-phase derivation (follicular / ovulatory / luteal) " +
                            "after enough data is in. Stays on device, never used by the sensor baseline engine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Temperature (°C)") },
                        suffix = { Text("°C") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        isError = valueText.isNotEmpty() && !isValid,
                        supportingText = if (valueText.isNotEmpty() && !isValid) {
                            { Text("Must be between 35.0 and 39.0 °C") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Measured on: ${formatDate(selectedDate)}")
                    }

                    OutlinedButton(
                        onClick = {
                            val temp = parsed ?: return@OutlinedButton
                            scope.launch {
                                repo.addBbt(temp, selectedDate)
                                refresh()
                            }
                            valueText = ""
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                }
            }

            latestPhase?.let { phase ->
                val label = CyclePhase.entries.firstOrNull { it.value.toDouble() == phase.value }?.name
                if (label != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Current phase: $label",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Inferred from your BBT series on ${formatDate(phase.timestamp)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Text("Recent entries", style = MaterialTheme.typography.titleSmall)
            if (recent.isEmpty()) {
                Text(
                    "No BBT entries yet. Daily logging during the first half of a cycle is what makes " +
                        "the biphasic shift detectable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(recent, key = { it.id }) { reading ->
                        RecentBbtRow(reading)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = it }
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

@Composable
private fun RecentBbtRow(reading: MetricReading) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "%.2f °C".format(reading.value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatDate(reading.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
