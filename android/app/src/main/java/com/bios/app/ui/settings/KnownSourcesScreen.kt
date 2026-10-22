package com.bios.app.ui.settings

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

/**
 * Known sources — every device or source Bios has stored a reading from,
 * newest first, with what it reports and when it last delivered. Read-only
 * (connect/disconnect lives on the Data & sources screen). See
 * docs/specs/known-sources.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownSourcesScreen(
    viewModel: KnownSourcesViewModel,
    onBack: () -> Unit = {},
) {
    val sources by viewModel.sources.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Known sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Every device and source Bios has stored data from, and when it last delivered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                isLoading && sources.isEmpty() -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() -> Text(
                    "No sources have delivered data yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> sources.forEach { KnownSourceCard(it) }
            }
        }
    }
}

@Composable
private fun KnownSourceCard(source: KnownSource) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(source.displayName, style = MaterialTheme.typography.titleSmall)
                FreshnessLabel(source)
            }
            Text(
                source.typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (source.metrics.isEmpty()) "No metrics yet" else "Reports: " + metricSummary(source.metrics),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Last reading: " + relativeTime(source.lastReadingAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FreshnessLabel(source: KnownSource) {
    val (text, color) = when (source.state) {
        KnownSource.Freshness.CURRENT -> "Current" to MaterialTheme.colorScheme.primary
        KnownSource.Freshness.STALE -> "Stale" to MaterialTheme.colorScheme.error
        KnownSource.Freshness.ATTENTION -> "Needs attention" to MaterialTheme.colorScheme.error
        KnownSource.Freshness.UNKNOWN -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.End)
}

/** First few metrics, then "+N more", to keep a row scannable (criterion 3). */
private fun metricSummary(metrics: List<String>, cap: Int = 4): String {
    if (metrics.size <= cap) return metrics.joinToString(", ")
    val shown = metrics.take(cap).joinToString(", ")
    return "$shown, +${metrics.size - cap} more"
}

private fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "never"
    val diff = System.currentTimeMillis() - epochMillis
    if (diff < 0) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        days < 30 -> "$days d ago"
        else -> "${days / 30} mo ago"
    }
}
