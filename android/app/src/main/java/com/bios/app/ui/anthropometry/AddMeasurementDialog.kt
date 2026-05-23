package com.bios.app.ui.anthropometry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.bios.app.model.GrowthChartReference

/**
 * Compact dialog for entering a new anthropometric measurement. The data
 * shape mirrors [com.bios.app.model.GrowthMeasurement] — height + weight +
 * head circumference + lean / fat mass + age-in-days (for paediatric) +
 * growth-chart reference selection.
 *
 * The age-in-days field is a deliberate UI choice: for paediatric owners
 * the age is what anchors the percentile lookup, so it has to live on the
 * entry surface. For adult owners the field stays optional and defaults to
 * 0 (no percentile lookup).
 */
data class GrowthMeasurementInput(
    val ageInDays: Int,
    val heightCm: Float?,
    val weightKg: Float?,
    val headCircumferenceCm: Float?,
    val leanBodyMassKg: Float?,
    val fatMassKg: Float?,
    val reference: GrowthChartReference?,
    val note: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementDialog(
    onDismiss: () -> Unit,
    onSave: (GrowthMeasurementInput) -> Unit,
) {
    var ageText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var headText by remember { mutableStateOf("") }
    var lbmText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf<GrowthChartReference?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add measurement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it.filter { c -> c.isDigit() } },
                    label = { Text("Age in days (paediatric, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Height / length (cm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = headText,
                    onValueChange = { headText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Head circumference (cm, paediatric)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lbmText,
                    onValueChange = { lbmText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Lean body mass (kg, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Fat mass (kg, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                Text("Growth-chart reference (paediatric)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GrowthChartReference.entries.forEach { entry ->
                        FilterChip(
                            selected = reference == entry,
                            onClick = { reference = if (reference == entry) null else entry },
                            label = { Text(entry.displayName) },
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        GrowthMeasurementInput(
                            ageInDays = ageText.toIntOrNull() ?: 0,
                            heightCm = heightText.toFloatOrNull(),
                            weightKg = weightText.toFloatOrNull(),
                            headCircumferenceCm = headText.toFloatOrNull(),
                            leanBodyMassKg = lbmText.toFloatOrNull(),
                            fatMassKg = fatText.toFloatOrNull(),
                            reference = reference,
                            note = noteText.trim().takeIf { it.isNotBlank() },
                        )
                    )
                },
                enabled = listOf(
                    heightText, weightText, headText, lbmText, fatText,
                ).any { it.toFloatOrNull() != null },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
