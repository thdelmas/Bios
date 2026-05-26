package com.bios.app.ui.tremor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.model.MetricReading
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore
import com.bios.contracts.MetricType

/**
 * Pull-side tremor trend surface (#206, NEUROLOGY_POV §2.4).
 *
 * Settings → Identity → "Tremor / movement trends" → [TremorTrendScreen].
 * Visible only when the owner's [PhysiologyState] is in
 * [PhysiologyState.MOVEMENT_DISORDER_CONTEXTS] (KNOWN_PD, KNOWN_ET, or
 * MOVEMENT_DISORDER_TRACKING). The visibility check is performed at the
 * navigation layer; callers that reach this screen anyway will see a
 * disclosure card rather than the plots.
 *
 * The screen plots [MetricType.TREMOR_AMPLITUDE_G] and
 * [MetricType.TREMOR_FREQUENCY_HZ] over time. Compose [Canvas] is used
 * directly — no charting library dependency.
 *
 * Manifesto stance: this is a data-display surface. No "you may have
 * Parkinson's" framing, no diagnostic score, no push prompts. The owner
 * reads the plots; evaluation belongs to the owner and their neurologist.
 */
@Composable
fun TremorTrendScreen(onBack: () -> Unit = {}, windowDays: Int = DEFAULT_WINDOW_DAYS) {
    val context = LocalContext.current
    val readingDao = remember(context) { BiosDatabase.getInstance(context).metricReadingDao() }
    val physiologyState = remember(context) { PhysiologyStateStore(context).current() }
    if (physiologyState !in PhysiologyState.MOVEMENT_DISORDER_CONTEXTS) {
        TremorTrendUnavailable()
        return
    }

    var amplitudeReadings by remember { mutableStateOf<List<MetricReading>>(emptyList()) }
    var frequencyReadings by remember { mutableStateOf<List<MetricReading>>(emptyList()) }

    LaunchedEffect(windowDays) {
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - windowDays * 24L * 3600L * 1000L
        amplitudeReadings = readingDao.fetch(
            MetricType.TREMOR_AMPLITUDE_G.key, startMillis, endMillis,
        )
        frequencyReadings = readingDao.fetch(
            MetricType.TREMOR_FREQUENCY_HZ.key, startMillis, endMillis,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Tremor / movement trends",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Tremor amplitude and peak frequency derived from phone " +
                "accelerometer episodes. The data is informational — for " +
                "evaluation, share these readings with the clinician who " +
                "manages your movement-disorder care.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TremorPlotCard(
            title = "Tremor amplitude (m/s²)",
            readings = amplitudeReadings,
            yLabel = "Amplitude",
            yUnitSuffix = " m/s²",
            color = MaterialTheme.colorScheme.primary,
        )

        TremorPlotCard(
            title = "Peak frequency (Hz)",
            readings = frequencyReadings,
            yLabel = "Frequency",
            yUnitSuffix = " Hz",
            color = MaterialTheme.colorScheme.tertiary,
        )

        ClassificationLegend()
    }
}

@Composable
private fun TremorTrendUnavailable() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Tremor / movement trends",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "This view is shown only when an owner-declared " +
                        "movement-disorder context is set — Parkinson's " +
                        "disease, essential tremor, or the movement-disorder " +
                        "tracking opt-in. Set the context in " +
                        "Settings → Physiology state to enable this surface.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TremorPlotCard(
    title: String,
    readings: List<MetricReading>,
    yLabel: String,
    yUnitSuffix: String,
    color: Color,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (readings.isEmpty()) {
                Text(
                    text = "No readings in the selected window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val values = readings.map { it.value }
                val minV = values.min()
                val maxV = values.max()
                val span = (maxV - minV).coerceAtLeast(1e-9)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val n = readings.size
                    if (n <= 1) {
                        drawCircle(
                            color = color,
                            radius = 4.dp.toPx(),
                            center = Offset(w / 2f, h / 2f),
                        )
                    } else {
                        val tMin = readings.first().timestamp
                        val tMax = readings.last().timestamp
                        val tSpan = (tMax - tMin).coerceAtLeast(1L)
                        val points = readings.map { r ->
                            val x = ((r.timestamp - tMin).toFloat() / tSpan) * w
                            val y = h - (((r.value - minV) / span).toFloat() * h)
                            Offset(x, y)
                        }
                        for (i in 1 until points.size) {
                            drawLine(
                                color = color,
                                start = points[i - 1],
                                end = points[i],
                                strokeWidth = Stroke.HairlineWidth.coerceAtLeast(2f),
                            )
                        }
                        for (p in points) {
                            drawCircle(color = color, radius = 3.dp.toPx(), center = p)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$yLabel range: ${formatValue(minV)} – " +
                            "${formatValue(maxV)}$yUnitSuffix",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${readings.size} readings",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassificationLegend() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Reference bands",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Physiologic tremor band: 3–12 Hz. Rest tremor sub-" +
                    "band: 4–6 Hz. Postural / action tremor sits across the " +
                    "wider 4–12 Hz range. The MDS-UPDRS examination " +
                    "structure anchors these reference bands.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatValue(v: Double): String =
    if (v < 0.01 || v >= 100.0) "%.3g".format(v) else "%.2f".format(v)

/** Default window for the trend view: 30 days. */
const val DEFAULT_WINDOW_DAYS = 30
