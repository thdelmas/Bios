package com.bios.app.ui.headache

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.contracts.MetricType

/**
 * Cluster-headache periodicity histogram (#284).
 *
 * Renders a 24-bar horizontal histogram of cluster-event onsets
 * over the last 90 days, hour-of-day on the y-axis (00–23 top to
 * bottom). The pull-side diagnostic substrate the headache-medicine
 * literature describes — the canonical "clockwork" nocturnal-onset
 * pattern is visible as a single tall bar in the small-hours range.
 *
 * Manifesto: no judgment, no alert, no pattern. The owner reads
 * the periodicity; the owner decides what (if anything) to do.
 * Bios is the instrument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterPeriodicityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { BiosDatabase.getInstance(context) }
    var bins by remember { mutableStateOf<List<ClusterPeriodicityHistogram.HourBin>>(emptyList()) }
    var totalEvents by remember { mutableStateOf(0) }
    var peakHour by remember { mutableStateOf<ClusterPeriodicityHistogram.HourBin?>(null) }

    LaunchedEffect(Unit) {
        val end = System.currentTimeMillis()
        val start = end - WINDOW_DAYS * 24L * 60L * 60L * 1000L
        val rows = db.metricReadingDao()
            .fetch(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT.key, start, end)
        bins = ClusterPeriodicityHistogram.bin(rows)
        totalEvents = ClusterPeriodicityHistogram.totalEvents(bins)
        peakHour = ClusterPeriodicityHistogram.peakHour(bins)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cluster periodicity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
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
            item { PeriodicityHeader(totalEvents = totalEvents, peakHour = peakHour) }
            if (totalEvents == 0) {
                item { PeriodicityEmpty() }
            } else {
                item { PeriodicityHistogram(bins = bins) }
            }
        }
    }
}

@Composable
private fun PeriodicityHeader(
    totalEvents: Int,
    peakHour: ClusterPeriodicityHistogram.HourBin?,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "What this view shows",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Onset hour of every cluster-headache event logged in the last $WINDOW_DAYS days, " +
                    "binned by hour-of-day in your local timezone. Cluster headache is " +
                    "characterised in the headache-medicine literature by clockwork onset within " +
                    "a narrow window night after night — visible here as a tall bar in a single " +
                    "hour.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Total cluster events in window: $totalEvents",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (peakHour != null) {
                Text(
                    "Peak hour: ${formatHour(peakHour.hourOfDay)} (${peakHour.count} events)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeriodicityEmpty() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "No cluster-headache events logged in the last $WINDOW_DAYS days. " +
                    "Log a cluster headache from the diary screen to populate the histogram.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PeriodicityHistogram(bins: List<ClusterPeriodicityHistogram.HourBin>) {
    val maxCount = bins.maxOf { it.count }.coerceAtLeast(1)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (bin in bins) {
                PeriodicityRow(bin = bin, maxCount = maxCount)
            }
        }
    }
}

@Composable
private fun PeriodicityRow(
    bin: ClusterPeriodicityHistogram.HourBin,
    maxCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatHour(bin.hourOfDay),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val fraction = bin.count.toFloat() / maxCount.toFloat()
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = bin.count.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(32.dp),
        )
    }
}

/** Pads single-digit hours and appends `:00` so the y-axis labels
 *  read as wall-clock times rather than bare integers. */
private fun formatHour(hour: Int): String = "%02d:00".format(hour)

private const val WINDOW_DAYS: Int = 90
