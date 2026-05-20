package com.bios.app.ui.intake

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.EliminationKinetics
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.intake.ActiveSubstancesAggregator.DoseDisplay
import com.bios.app.ui.intake.ActiveSubstancesAggregator.SubstanceCard
import com.bios.contracts.MetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "What's still in your body" dashboard (issue #138). One tile per
 * substance Bios can compute a dose-based concentration for (currently
 * caffeine + alcohol). Tobacco / cannabis intakes ship as opaque event
 * markers via Smokeless and have no dose attached, so they're absent
 * from this surface until a dose-bearing companion key lands.
 *
 * The screen is a thin renderer over [ActiveSubstancesAggregator]; the
 * data shape and empty-state semantics live there so the math layer can
 * be exercised end-to-end without Compose. Manifesto-clean: the surface
 * is descriptive output of the #136 pharmacokinetic engine, no nudges,
 * no "should you have another" coaching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSubstancesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    var cards by remember { mutableStateOf<List<SubstanceCard>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val windowStart = now - ActiveSubstancesAggregator.DEFAULT_WINDOW_MS
        val dao = viewModel.db.metricReadingDao()
        // Read each intake key separately — the DAO indexes by
        // metricType + timestamp, so two short scans beat one big one.
        val intakes = listOf(MetricType.CAFFEINE_INTAKE, MetricType.ALCOHOL_INTAKE)
            .flatMap { dao.fetch(it.key, windowStart, now) }
        val sources = viewModel.db.dataSourceDao().getAll()
            .associate { it.id to (it.deviceName ?: it.sourceType) }
        cards = ActiveSubstancesAggregator.compute(intakes, sources, nowMs = now)
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Substances") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Intro() }
            if (!loaded) {
                item {
                    Text(
                        "Loading recent intake…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (cards.isEmpty()) {
                item {
                    Text(
                        "No substances configured. Log a caffeine or " +
                            "alcohol intake to populate this dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(cards, key = { it.metricKey }) { card ->
                    SubstanceTile(card)
                }
            }
        }
    }
}

@Composable
private fun Intro() {
    Text(
        "Pull-side decay curves for substances Bios knows the " +
            "pharmacokinetics of. Math only — you read the number.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubstanceTile(card: SubstanceCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    substanceLabel(card),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (card.hasRecentIntake) {
                    Text(
                        formatAmount(card.currentAmountMg, card.metricKey),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!card.hasRecentIntake) {
                Text(
                    "No recent intake logged in the last 24h. Unknown, not zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Sparkline(card)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("Last dose", lastDoseLabel(card))
                card.thresholdMg?.let {
                    StatCell(
                        "Below ${formatAmount(it, card.metricKey)} at",
                        belowThresholdLabel(card),
                    )
                }
                StatCell("Kinetics", kineticsLabel(card.pk.kinetics))
            }

            if (card.recentDoses.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recent doses",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                card.recentDoses.sortedByDescending { it.timestampMs }.forEach { dose ->
                    DoseRow(dose, card.metricKey)
                }
            }
        }
    }
}

@Composable
private fun Sparkline(card: SubstanceCard) {
    val curve = card.curve
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary
    val thresholdColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxY = (curve.maxOfOrNull { it.second } ?: 0.0)
        .coerceAtLeast(card.thresholdMg ?: 0.0)
        .coerceAtLeast(1e-6)
    val firstTs = curve.firstOrNull()?.first ?: return
    val lastTs = curve.lastOrNull()?.first ?: return
    val spanMs = (lastTs - firstTs).coerceAtLeast(1L).toDouble()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        val w = size.width
        val h = size.height
        if (curve.size >= 2) {
            for (i in 1 until curve.size) {
                val (t0, c0) = curve[i - 1]
                val (t1, c1) = curve[i]
                val x0 = ((t0 - firstTs) / spanMs).toFloat() * w
                val x1 = ((t1 - firstTs) / spanMs).toFloat() * w
                val y0 = h - (c0 / maxY).toFloat() * h
                val y1 = h - (c1 / maxY).toFloat() * h
                drawLine(
                    color = lineColor,
                    start = Offset(x0, y0),
                    end = Offset(x1, y1),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }
        card.thresholdMg?.let { thr ->
            val y = h - (thr / maxY).toFloat() * h
            drawLine(
                color = thresholdColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 2f,
            )
        }
        for (dose in card.recentDoses) {
            val x = ((dose.timestampMs - firstTs).coerceAtLeast(0L) / spanMs).toFloat() * w
            drawCircle(
                color = markerColor,
                radius = 5f,
                center = Offset(x.coerceIn(0f, w), h - 3f),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DoseRow(dose: DoseDisplay, metricKey: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${formatTime(dose.timestampMs)} · ${formatAmount(dose.doseMg, metricKey)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            dose.sourceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Box(modifier = Modifier.height(2.dp))
}

private fun substanceLabel(card: SubstanceCard): String = when (card.metricKey) {
    MetricType.CAFFEINE_INTAKE.key -> "Caffeine"
    MetricType.ALCOHOL_INTAKE.key -> "Alcohol"
    else -> card.pk.substanceKey
}

private fun kineticsLabel(k: EliminationKinetics): String = when (k) {
    EliminationKinetics.FIRST_ORDER -> "1st-order"
    EliminationKinetics.ZERO_ORDER -> "Zero-order"
}

private fun lastDoseLabel(card: SubstanceCard): String {
    val ts = card.lastDoseTimestampMs ?: return "—"
    val deltaMin = ((System.currentTimeMillis() - ts) / 60_000L).coerceAtLeast(0L)
    val src = card.lastDoseLabel?.let { " · $it" } ?: ""
    return when {
        deltaMin < 1L -> "just now$src"
        deltaMin < 60L -> "${deltaMin}m ago$src"
        else -> "${deltaMin / 60L}h ${deltaMin % 60L}m ago$src"
    }
}

private fun belowThresholdLabel(card: SubstanceCard): String {
    val ms = card.timeUntilBelowThresholdMs ?: return "no crossing in 10 days"
    if (ms <= 0L) return "already below"
    val target = System.currentTimeMillis() + ms
    return clockFormat.format(Date(target))
}

private fun formatAmount(mg: Double, metricKey: String): String {
    // Display amounts in the metric's native unit so the owner sees the
    // same number they entered: caffeine in mg, alcohol back in g.
    return if (metricKey == MetricType.ALCOHOL_INTAKE.key) {
        String.format(Locale.US, "%.1f g", mg / 1000.0)
    } else {
        String.format(Locale.US, "%.0f mg", mg)
    }
}

private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)
private val dateTimeFormat = SimpleDateFormat("EEE HH:mm", Locale.US)
private fun formatTime(millis: Long): String = dateTimeFormat.format(Date(millis))
