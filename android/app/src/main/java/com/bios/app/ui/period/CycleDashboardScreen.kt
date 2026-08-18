package com.bios.app.ui.period

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.PeriodEntryRepo
import com.bios.app.engine.CycleStats
import com.bios.app.model.CyclePhase
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Pull-side cycle surface: a month calendar painted by the derived
 * CYCLE_PHASE series, plus cycle-length history straight from logged
 * onsets. Reads the isolated reproductive DB via [PeriodEntryRepo];
 * everything shown is derived from what the owner logged — no forward
 * prediction, no fertility-window claims, no grading of the spread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleDashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { PeriodEntryRepo(context) }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var phaseByDay by remember { mutableStateOf<Map<LocalDate, CyclePhase>>(emptyMap()) }
    var onsetTimestamps by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(month) {
        val start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        phaseByDay = repo.fetchPhases(start, end).mapNotNull { reading ->
            val phase = CyclePhase.entries.getOrNull(reading.value.toInt()) ?: return@mapNotNull null
            utcDate(reading.timestamp) to phase
        }.toMap()
        onsetTimestamps = repo.fetchOnsetsSince(0L).map { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cycle") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CycleCalendar(
                month = month,
                phaseByDay = phaseByDay,
                onsetDays = onsetTimestamps.map { utcDate(it) }.toSet(),
                selectedDay = selectedDay,
                onSelectDay = { selectedDay = it },
                onMonthChange = { month = it; selectedDay = null },
            )
            selectedDay?.let { day ->
                SelectedDayCard(
                    day = day,
                    phase = phaseByDay[day],
                    cycleDay = CycleStats.cycleDayFor(day.toEpochDay(), onsetTimestamps),
                )
            }
            CycleLengthsCard(lengths = CycleStats.cycleLengthsDays(onsetTimestamps))
        }
    }
}

@Composable
private fun SelectedDayCard(day: LocalDate, phase: CyclePhase?, cycleDay: Int?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                phase?.let { "Phase: ${phaseLabel(it)}" } ?: "No phase derived for this day",
                style = MaterialTheme.typography.bodySmall,
            )
            cycleDay?.let {
                Text("Cycle day $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Lengths as logged, oldest to newest, with median and range. The spread
 * is presented as data — the owner evaluates it.
 */
@Composable
private fun CycleLengthsCard(lengths: List<Int>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cycle lengths", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (lengths.isEmpty()) {
                Text(
                    "Two or more logged onsets are needed before lengths can be measured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    lengths.joinToString(" · ") { "${it}d" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val median = CycleStats.median(lengths)
                Text(
                    "Median ${formatDays(median)} · range ${lengths.min()}–${lengths.max()}d · ${lengths.size} cycles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDays(median: Double?): String =
    median?.let { if (it % 1.0 == 0.0) "${it.toInt()}d" else "${it}d" } ?: "—"

private fun utcDate(timestampMs: Long): LocalDate =
    LocalDate.ofEpochDay(timestampMs / MS_PER_DAY)

private const val MS_PER_DAY = 86_400_000L
