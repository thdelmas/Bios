package com.bios.app.ui.anthropometry

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
import com.bios.app.data.BiosDatabase
import com.bios.app.data.GrowthMeasurementRepo
import com.bios.app.model.GrowthMeasurement
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Body measurements (#199): the owner records height, weight, head
 * circumference and lean / fat mass, and reads them back as a dated list.
 * Values are shown as recorded, with the derived BMI; nothing compares
 * them with a population reference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnthropometryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) {
        GrowthMeasurementRepo(BiosDatabase.getInstance(context))
    }
    val scope = rememberCoroutineScope()

    var measurements by remember { mutableStateOf<List<GrowthMeasurement>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        measurements = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Body measurements") },
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
                    Text("Height, weight and body composition", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Record height, weight, head circumference, lean mass and fat mass. " +
                            "Each entry is kept as you recorded it and listed by date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add measurement")
                    }
                }
            }

            if (measurements.isEmpty()) {
                Text(
                    "No measurements on record. Tap \"Add measurement\" to record height, weight, " +
                        "and head circumference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(measurements, key = { it.id }) { m ->
                        MeasurementRow(
                            measurement = m,
                            onDelete = {
                                scope.launch {
                                    repo.remove(m.id)
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
        AddMeasurementDialog(
            onDismiss = { showAddDialog = false },
            onSave = { input ->
                scope.launch {
                    repo.record(
                        timestamp = System.currentTimeMillis(),
                        ageInDays = input.ageInDays,
                        heightCm = input.heightCm,
                        weightKg = input.weightKg,
                        headCircumferenceCm = input.headCircumferenceCm,
                        leanBodyMassKg = input.leanBodyMassKg,
                        fatMassKg = input.fatMassKg,
                        note = input.note,
                    )
                    refresh()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MeasurementRow(measurement: GrowthMeasurement, onDelete: () -> Unit) {
    var showActions by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(measurement.timestamp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (measurement.ageInDays > 0) {
                    Text(
                        "Age ${formatAge(measurement.ageInDays)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val parts = buildList {
                measurement.heightCm?.let { add("Height ${"%.1f".format(it)} cm") }
                measurement.weightKg?.let { add("Weight ${"%.2f".format(it)} kg") }
                measurement.headCircumferenceCm?.let { add("HC ${"%.1f".format(it)} cm") }
                measurement.bmiKgPerM2?.let { add("BMI ${"%.1f".format(it)}") }
                measurement.leanBodyMassKg?.let { add("LBM ${"%.1f".format(it)} kg") }
                measurement.fatMassKg?.let { add("Fat ${"%.1f".format(it)} kg") }
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)

            measurement.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showActions = !showActions }) {
                Text(if (showActions) "Hide actions" else "Actions")
            }
            if (showActions) {
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

private fun formatAge(ageInDays: Int): String {
    if (ageInDays < 60) return "$ageInDays d"
    val months = ageInDays / 30
    return if (months < 24) "$months mo" else "${months / 12} y"
}
