package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.CountSourceOrder
import com.bios.app.data.CountTotals
import com.bios.app.model.DataSource
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * The owner's order over the sources that may count a day
 * (docs/specs/source-preference.md, criterion 8): one list, move up or
 * down, and the source that won today's steps named under it so the rule
 * is visible without a chart.
 */
@Composable
internal fun CountSourceOrderSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<DataSource>>(emptyList()) }
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var todayWinner by remember { mutableStateOf<String?>(null) }

    suspend fun reloadWinner() {
        val zone = ZoneId.systemDefault()
        todayWinner = CountTotals(viewModel.db, context)
            .dayTotal(MetricType.STEPS, LocalDate.now(zone), zone)?.winnerKey
    }

    LaunchedEffect(Unit) {
        sources = viewModel.db.dataSourceDao().getAll()
        // Only sources that have ever counted a day, plus the owner's own entry.
        val readingDao = viewModel.db.metricReadingDao()
        val counting = listOf(MetricType.STEPS, MetricType.ACTIVE_CALORIES, MetricType.ACTIVE_MINUTES)
            .flatMap { readingDao.sourceCountsForMetric(it.key) }.map { it.sourceId }.toSet()
        val keys = sources.filter { it.id in counting }.map { CountSourceOrder.keyOf(it) }.toSet() +
            com.bios.app.model.SourceType.SELF_REPORTED.key
        order = CountSourceOrder.effectiveOrder(CountSourceOrder.load(context), keys)
        reloadWinner()
    }

    fun move(index: Int, delta: Int) {
        val next = CountSourceOrder.moved(order, index, delta)
        if (next == order) return
        order = next
        CountSourceOrder.save(context, next)
        scope.launch { reloadWinner() }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Which source counts a day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Steps, active calories and active minutes take one source per day: " +
                    "the first in this list with readings that day. The others are ignored that day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            order.forEachIndexed { index, key ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. ${CountSourceOrder.labelOf(key, sources)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { move(index, 1) }, enabled = index < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                    }
                }
            }
            Text(
                text = "Today's steps: " + (todayWinner?.let { CountSourceOrder.labelOf(it, sources) } ?: "no readings yet"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
