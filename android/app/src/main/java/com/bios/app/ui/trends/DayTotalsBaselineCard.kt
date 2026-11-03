package com.bios.app.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.contracts.MetricType

/**
 * Baseline card for cumulative-daily metrics (steps, active calories,
 * active minutes): mean and range of whole days, in the same unit as the
 * bars above it. The raw-reading baseline ("mean 87.2, range 1 to 218"
 * under a chart of days in the thousands) read as nonsense here
 * (docs/specs/day-totals.md, criterion 11).
 */
@Composable
fun DayTotalsBaselineCard(baseline: DailyBaseline?, metric: MetricType) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Usual day", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(16.dp))
            if (baseline == null) {
                Text(
                    "No complete day with readings yet. Today does not count until it ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DayStatCell("Mean", formatTotal(baseline.mean, metric))
                DayStatCell("Lowest", formatTotal(baseline.min, metric))
                DayStatCell("Highest", formatTotal(baseline.max, metric))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Over the last ${baseline.days} complete ${if (baseline.days == 1) "day" else "days"} with readings. Today not counted.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayStatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
