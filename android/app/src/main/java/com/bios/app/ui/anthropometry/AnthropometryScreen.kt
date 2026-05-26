package com.bios.app.ui.anthropometry

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.GrowthMeasurementRepo
import com.bios.app.engine.GrowthChartEngine
import com.bios.app.engine.GrowthIndicator
import com.bios.app.engine.GrowthSex
import com.bios.app.model.GrowthChartReference
import com.bios.app.model.GrowthMeasurement
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Identity → Anthropometry (#199, audit gap §2.7).
 *
 * Owner-records height + weight + head circumference. For paediatric
 * owners (PhysiologyState.PAEDIATRIC) the growth-chart percentile curve
 * is plotted via [GrowthChartCanvas]. For adults the screen is a plain
 * trajectory log; the sarcopenia / cachexia screens consume the underlying
 * measurements directly.
 *
 * Per the manifesto: pull-side surface. The owner navigates here to see
 * the data; no nudges, no percentile-band warnings pushed at the person.
 * The percentile is rendered as a data statement next to each measurement.
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
                title = { Text("Anthropometry") },
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
                    Text("Growth and body composition", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Record height, weight, and head circumference at routine paediatric visits, " +
                            "or weight and body composition at adult check-ins. " +
                            "The growth-chart percentile is plotted against the WHO 0–5y reference for " +
                            "paediatric owners; adult readings feed the body-composition trajectory view " +
                            "consumed by the sarcopenia and cachexia screens.",
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

            // Percentile-curve plot (paediatric)
            if (measurements.any { it.growthChartReference == GrowthChartReference.WHO_0_5Y && it.weightKg != null }) {
                GrowthChartCanvas(measurements = measurements)
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
                        growthChartReference = input.reference,
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

            // Percentile statement (paediatric WHO 0–5y only)
            val sex = GrowthSex.MALE // default; sex routing is a follow-up
            val ref = measurement.growthChartReference
            val weightKg = measurement.weightKg
            val ageDays = measurement.ageInDays
            if (ref == GrowthChartReference.WHO_0_5Y && weightKg != null && ageDays in 0..731) {
                val point = GrowthChartEngine.compute(
                    reference = ref,
                    sex = sex,
                    indicator = GrowthIndicator.WEIGHT_FOR_AGE,
                    ageInDays = ageDays,
                    measurement = weightKg.toDouble(),
                )
                point?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Weight-for-age (WHO 0–5y): ${"%.0f".format(it.percentile)}th percentile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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

/**
 * Minimal Compose Canvas percentile-curve plot. Renders the 3rd / 50th /
 * 97th percentile bands across the WHO 0–24 month range plus the owner's
 * own measurements as dots. Sex-routing and CDC tables are follow-ups.
 */
@Composable
private fun GrowthChartCanvas(measurements: List<GrowthMeasurement>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Weight-for-age (WHO 0–5y)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                val w = size.width
                val h = size.height
                val maxAge = 731
                val minWeight = 0.0
                val maxWeight = 16.0

                fun mapPoint(ageDays: Int, weightKg: Double): Offset {
                    val x = (ageDays.toDouble() / maxAge) * w
                    val y = h - ((weightKg - minWeight) / (maxWeight - minWeight)) * h
                    return Offset(x.toFloat(), y.toFloat())
                }

                // Reference bands (3rd / 50th / 97th)
                val sex = GrowthSex.MALE
                val ages = (0..maxAge step 30).toList()
                listOf(3.0, 50.0, 97.0).forEach { pct ->
                    val color = when (pct) {
                        50.0 -> Color(0xFF1976D2)
                        else -> Color(0xFF90A4AE)
                    }
                    val pts = ages.mapNotNull { age ->
                        // Invert: from percentile we want the weight at that percentile
                        weightForPercentile(sex, age, pct)?.let { mapPoint(age, it) }
                    }
                    for (i in 1 until pts.size) {
                        drawLine(color, pts[i - 1], pts[i], strokeWidth = 2f)
                    }
                }

                // Owner points
                measurements
                    .filter { it.growthChartReference == GrowthChartReference.WHO_0_5Y && it.weightKg != null && it.ageInDays in 0..maxAge }
                    .forEach {
                        val p = mapPoint(it.ageInDays, it.weightKg!!.toDouble())
                        drawCircle(Color(0xFFD32F2F), radius = 6f, center = p, style = Stroke(width = 2f))
                        drawCircle(Color(0xFFD32F2F), radius = 3f, center = p)
                    }
            }
            Text(
                "Blue: median. Grey: 3rd / 97th. Red dots: recorded measurements.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Inverse LMS lookup: weight at the requested percentile for a given age.
 * Numerical bisection over the standard range; good enough for a 220-px
 * canvas (the published WHO tables agree to one decimal).
 */
private fun weightForPercentile(sex: GrowthSex, ageInDays: Int, percentile: Double): Double? {
    var lo = 0.5
    var hi = 25.0
    var iters = 0
    while (hi - lo > 0.01 && iters < 30) {
        val mid = (lo + hi) / 2.0
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = sex,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = ageInDays,
            measurement = mid,
        ) ?: return null
        if (point.percentile < percentile) lo = mid else hi = mid
        iters++
    }
    return (lo + hi) / 2.0
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

private fun formatAge(ageInDays: Int): String {
    if (ageInDays < 60) return "$ageInDays d"
    val months = ageInDays / 30
    return if (months < 24) "$months mo" else "${months / 12} y"
}
