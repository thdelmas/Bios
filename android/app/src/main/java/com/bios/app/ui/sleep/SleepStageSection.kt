package com.bios.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage timeline + derived score for one selected night. Pull-side only:
 * rendered inside the sleep dashboard the owner navigated into, silent
 * (renders nothing) when the night has no stage rows — most manual and
 * duration-only sources. The night's window is reconstructed from the
 * SLEEP_DURATION row: timestamp is the session end, durationSec the
 * time-in-bed span (falling back to the TST value when null).
 */
@Composable
fun SleepStageSection(viewModel: AppViewModel, night: MetricReading?) {
    if (night == null) return
    var stageRows by remember(night.id) { mutableStateOf<List<MetricReading>>(emptyList()) }
    var score by remember(night.id) { mutableStateOf<MetricReading?>(null) }

    LaunchedEffect(night.id) {
        val end = night.timestamp + WINDOW_MARGIN_MS
        val spanMs = (night.durationSec ?: night.value.toInt()) * 1000L
        val start = night.timestamp - spanMs - WINDOW_MARGIN_MS
        stageRows = viewModel.db.metricReadingDao().fetch(MetricType.SLEEP_STAGE.key, start, end)
        score = viewModel.db.metricReadingDao()
            .fetch(MetricType.SLEEP_SCORE.key, start, end)
            .lastOrNull()
    }

    val bands = remember(stageRows) { HypnogramBands.bands(stageRows) }
    if (bands.isEmpty() && score == null) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (bands.isNotEmpty()) {
            HypnogramCard(bands = bands, nightLabel = formatNight(night.timestamp))
            HypnogramLegend(bands)
        }
        score?.let { ScoreLine(it) }
    }
}

/**
 * The derived 0–100 composite from SleepDerivations — descriptive, not
 * evaluative (see deriveSleepScore's contract): components are published
 * clinical norms aggregated for trending, and nothing here grades the
 * owner against them.
 */
@Composable
private fun ScoreLine(score: MetricReading) {
    Text(
        "Derived sleep score ${score.value.toInt()} / 100 — composite of duration, " +
            "efficiency, latency, fragmentation vs published clinical norms.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Margin around the reconstructed session window for stage/score fetches. */
private const val WINDOW_MARGIN_MS = 2L * 3600_000L

private val nightFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
private fun formatNight(millis: Long): String = nightFormat.format(Date(millis))
