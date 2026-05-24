package com.bios.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Owner-facing tiered list of wearables that the existing
 * [com.bios.app.ingest.GadgetbridgeAdapter] (and / or the native API
 * adapters) can ingest from (#245).
 *
 * Manifesto framing — same as the banner that brings the owner here:
 *   - This is a **suggestion**, not a recommendation engine.
 *   - We surface what's available + the trade-offs; the owner decides.
 *   - No affiliate links, no telemetry on view, no nudges.
 *
 * Each tier lists rough price, the FOSS pipeline the device speaks,
 * the data quality to expect, and the pairing caveat if any. Honest
 * about Gadgetbridge's own conclusion — even wrist-worn deep-sleep
 * detection is "not reliable" on most consumer watches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearableRecommendationScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Recommended wearables") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { IntroCard() }
            items(TIERS, key = { it.title }) { tier -> TierCard(tier) }
            item { SkipForNowCard() }
            item { HonestNoteCard() }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What a wearable adds over phone-only",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Total sleep time and onset / offset become reliable. " +
                    "Stage classification stays approximate even on premium " +
                    "hardware — Gadgetbridge's own decade of work concluded " +
                    "wrist-worn deep-sleep detection is not reliable for " +
                    "most consumer watches.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierCard(tier: WearableTier) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                tier.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                tier.priceRange,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(tier.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pipeline: ${tier.pipeline}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Expected quality: ${tier.qualityNote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tier.caveat?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Caveat: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SkipForNowCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Skip for now",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "PineTime — sleep-tracking PR not yet merged.\n" +
                    "AsteroidOS 2.0 — no sleep tracking.\n" +
                    "Pebble (revived by Core Devices) — check ship date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HonestNoteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "No affiliate links, no telemetry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Bios records nothing about whether you opened this screen " +
                    "or what you tapped on. No vendor sponsors any tier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class WearableTier(
    val title: String,
    val priceRange: String,
    val description: String,
    val pipeline: String,
    val qualityNote: String,
    val caveat: String?,
)

private val TIERS: List<WearableTier> = listOf(
    WearableTier(
        title = "Tier 1 — Best $/signal: Xiaomi Mi Band 7 / 8",
        priceRange = "~$40-$50",
        description = "Solid heart rate + motion + decent battery. Mi Band 8 even " +
            "pushes raw light / deep / REM stages through the xiaomi-protobuf " +
            "path that Gadgetbridge supports.",
        pipeline = "Gadgetbridge (xiaomi-protobuf)",
        qualityNote = "Reliable total sleep time + onset / offset; approximate stages.",
        caveat = "Token-pairing on newer Huami firmware sometimes needs root or " +
            "a one-time vendor-app handshake.",
    ),
    WearableTier(
        title = "Tier 2 — Best FOSS philosophy: Bangle.js 2",
        priceRange = "~$89",
        description = "Fully open hardware + firmware. MIT-licensed app ecosystem. " +
            "The on-watch `sleeplog` app runs accel + HR sleep tracking locally.",
        pipeline = "Gadgetbridge (Bangle.js)",
        qualityNote = "Honest mid-tier — total sleep time + onset / offset are " +
            "reliable; staging stays approximate.",
        caveat = null,
    ),
    WearableTier(
        title = "Tier 3 — Premium: Garmin Instinct 3 / Fenix",
        priceRange = "~$300-$1000+",
        description = "Native on-watch sleep staging. Syncs to Gadgetbridge without " +
            "the proprietary Garmin app. Bios also has a direct GarminApiAdapter.",
        pipeline = "Gadgetbridge or direct GarminApiAdapter",
        qualityNote = "Premium total sleep + onset / offset; the closest consumer-" +
            "tier device to research-grade staging (still approximate).",
        caveat = null,
    ),
)
