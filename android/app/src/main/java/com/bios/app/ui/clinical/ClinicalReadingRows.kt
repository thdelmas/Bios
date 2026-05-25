package com.bios.app.ui.clinical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bios.app.data.AvpuLevel
import com.bios.app.data.BundleReading
import com.bios.app.data.GcsBounds
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingRowCard(
    state: ReadingRowState,
    onChange: (ReadingRowState) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricPicker(
                    selected = state.metric,
                    onSelect = { onChange(state.copy(metric = it, valueText = "", originalValue = null)) },
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove reading")
                    }
                }
            }

            when (state.metric) {
                MetricType.CONSCIOUSNESS_LEVEL -> ConsciousnessInput(state, onChange)
                else -> NumericInput(state, onChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricPicker(
    selected: MetricType,
    onSelect: (MetricType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.readableName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Metric") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            clinicalEntryMetrics.forEach { metric ->
                DropdownMenuItem(
                    text = { Text("${metric.readableName} (${metric.unit.symbol.ifEmpty { "—" }})") },
                    onClick = {
                        onSelect(metric)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NumericInput(state: ReadingRowState, onChange: (ReadingRowState) -> Unit) {
    OutlinedTextField(
        value = state.valueText,
        onValueChange = { onChange(state.copy(valueText = it.filter { c -> c.isDigit() || c == '.' })) },
        label = { Text("Value") },
        suffix = { Text(state.metric.unit.symbol.ifEmpty { "—" }) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConsciousnessInput(state: ReadingRowState, onChange: (ReadingRowState) -> Unit) {
    Text(
        "AVPU shortcut maps to GCS losslessly — original scale + value " +
            "are kept alongside for provenance.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AvpuLevel.entries.forEach { level ->
            AssistChip(
                onClick = {
                    onChange(
                        state.copy(
                            valueText = level.gcsTotal.toString(),
                            originalScale = "AVPU",
                            originalValue = level.name.first().toString(),
                        )
                    )
                },
                label = { Text(level.name.first().toString()) },
            )
        }
    }
    OutlinedTextField(
        value = state.valueText,
        onValueChange = {
            val cleaned = it.filter { c -> c.isDigit() }
            onChange(
                state.copy(
                    valueText = cleaned,
                    originalScale = if (cleaned.isBlank()) null else "GCS",
                    originalValue = null,
                )
            )
        },
        label = { Text("GCS total") },
        suffix = { Text("${GcsBounds.MIN}–${GcsBounds.MAX}") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    state.originalScale?.let { scale ->
        val original = state.originalValue?.let { " ($it)" } ?: ""
        FilterChip(
            selected = true,
            onClick = {},
            label = { Text("Source: $scale$original") },
        )
    }
}

internal data class ReadingRowState(
    val metric: MetricType = MetricType.BLOOD_PRESSURE_SYSTOLIC,
    val valueText: String = "",
    val originalScale: String? = null,
    val originalValue: String? = null,
) {
    fun isFilled(): Boolean = valueText.toDoubleOrNull() != null

    fun toBundleReading(): BundleReading? {
        val parsed = valueText.toDoubleOrNull() ?: return null
        val (scale, value) = if (metric == MetricType.CONSCIOUSNESS_LEVEL) {
            (originalScale ?: "GCS") to (originalValue ?: parsed.toInt().toString())
        } else {
            null to null
        }
        return BundleReading(
            metricType = metric,
            value = parsed,
            originalScale = scale,
            originalValue = value,
        )
    }
}

/**
 * Pre-populated row sets the entry screen offers as chip-selectable presets.
 * Each maps to a real owner workflow so the screen doesn't force a delete-
 * then-re-add when the use case isn't triage.
 *  - [TRIAGE] is the Spanish ER chart shape (TAS / TAD / Sat O2 / FC / Temp /
 *    FR / EVA / GCS / Flujo O2) that originally motivated this surface.
 *  - [BODY_COMPOSITION] is the pharmacy / scale shape (weight, height, BMI,
 *    body fat %, lean + fat mass, blood pressure) — the bundle a body-
 *    composition station prints at a weekly weigh-in.
 */
enum class MeasurementPreset { TRIAGE, BODY_COMPOSITION }

internal fun defaultRowsFor(preset: MeasurementPreset): List<ReadingRowState> = when (preset) {
    MeasurementPreset.TRIAGE -> listOf(
        ReadingRowState(metric = MetricType.BLOOD_PRESSURE_SYSTOLIC),
        ReadingRowState(metric = MetricType.BLOOD_PRESSURE_DIASTOLIC),
        ReadingRowState(metric = MetricType.BLOOD_OXYGEN),
        ReadingRowState(metric = MetricType.HEART_RATE),
        ReadingRowState(metric = MetricType.SKIN_TEMPERATURE),
        ReadingRowState(metric = MetricType.RESPIRATORY_RATE),
        ReadingRowState(metric = MetricType.PAIN_SCORE),
        ReadingRowState(metric = MetricType.CONSCIOUSNESS_LEVEL),
        ReadingRowState(metric = MetricType.OXYGEN_FLOW_RATE),
    )
    MeasurementPreset.BODY_COMPOSITION -> listOf(
        ReadingRowState(metric = MetricType.BODY_MASS),
        ReadingRowState(metric = MetricType.HEIGHT_CM),
        ReadingRowState(metric = MetricType.BMI_KG_PER_M2),
        ReadingRowState(metric = MetricType.BODY_FAT_PCT),
        ReadingRowState(metric = MetricType.LEAN_BODY_MASS_KG),
        ReadingRowState(metric = MetricType.FAT_MASS_KG),
        ReadingRowState(metric = MetricType.BLOOD_PRESSURE_SYSTOLIC),
        ReadingRowState(metric = MetricType.BLOOD_PRESSURE_DIASTOLIC),
    )
}

internal fun defaultTriageRows(): List<ReadingRowState> = defaultRowsFor(MeasurementPreset.TRIAGE)

/**
 * Metrics offered in the clinical-reading picker: everything that opts in
 * to manual entry except the lab/biomarker surface (which has its own
 * picker on BiomarkerEntryScreen) and reproductive metrics (which route
 * through their isolated DB via BbtEntryRepo / PeriodEntryRepo).
 */
private val clinicalEntryMetrics: List<MetricType> by lazy {
    MetricType.entries.filter {
        it.allowsManualEntry &&
            it.domain != MetricDomain.BIOMARKER &&
            it.domain != MetricDomain.WOMENS_HEALTH &&
            it.domain != MetricDomain.SLEEP
    }
}
