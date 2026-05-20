package com.bios.app.ui.biomarkers

import com.bios.app.data.BiomarkerContext
import com.bios.app.data.BiomarkerEntryRepo
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Pure aggregator behind the biomarker / body-levels dashboard (#137).
 *
 * Takes a flat list of biomarker [MetricReading]s + their `event_payloads`
 * sidecar fields + the source-label map, picks the most recent value per
 * [MetricType], classifies it descriptively against the population
 * reference range from [BiomarkerReferenceRanges], and groups the tiles
 * into [BiomarkerPanels].
 *
 * Pure so the screen layer is a thin renderer — staleness math, range
 * classification, panel staleness rollup, and the empty-state contract
 * are all exercised under JUnit. The screen reads the output and lays
 * it out, that's it.
 *
 * Manifesto: descriptive only. Classification is a passthrough of what
 * the lab itself flags. No composite "score" across panels.
 */
object BiomarkerDashboardAggregator {

    /** A single biomarker tile on the dashboard. Null fields render as
     *  "—" rather than fabricating defaults. */
    data class Tile(
        val metricType: MetricType,
        val value: Double?,
        val lastReadingTimestampMs: Long?,
        val ageDays: Long?,
        val range: ReferenceRange?,
        val classification: ReferenceRange.Classification,
        val sourceLabel: String?,
        val context: BiomarkerContext,
    ) {
        val hasReading: Boolean get() = value != null
    }

    data class PanelDisplay(
        val panel: BiomarkerPanel,
        val tiles: List<Tile>,
    ) {
        /** Oldest age across populated tiles. Null when the panel has no
         *  readings. The dashboard uses this for the staleness chip. */
        val oldestAgeDays: Long?
            get() = tiles.mapNotNull { it.ageDays }.maxOrNull()

        val hasAnyReading: Boolean get() = tiles.any { it.hasReading }
    }

    /**
     * @param readings every biomarker reading available — the aggregator
     *   filters down to BIOMARKER-domain entries internally so callers
     *   can pass a wider stream without pre-filtering.
     * @param payloadByReadingId reading-id → its event_payloads rows.
     *   Empty map is fine; tiles render without provenance context.
     * @param sourceLabels sourceId → human-readable label.
     * @param nowMs anchor for staleness math.
     */
    fun compute(
        readings: List<MetricReading>,
        payloadByReadingId: Map<String, List<EventPayloadField>>,
        sourceLabels: Map<String, String>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<PanelDisplay> {
        val biomarkerKeys = MetricType.entries
            .filter { it.domain == com.bios.contracts.MetricDomain.BIOMARKER }
            .map { it.key }
            .toSet()
        val latestByMetric = readings
            .asSequence()
            .filter { it.metricType in biomarkerKeys }
            .filter { it.timestamp <= nowMs }
            .groupBy { it.metricType }
            .mapValues { (_, list) -> list.maxByOrNull { it.timestamp }!! }

        return BiomarkerPanels.all.map { panel ->
            val tiles = panel.metrics.map { metric ->
                buildTile(
                    metric = metric,
                    latest = latestByMetric[metric.key],
                    payloadByReadingId = payloadByReadingId,
                    sourceLabels = sourceLabels,
                    nowMs = nowMs,
                )
            }
            PanelDisplay(panel = panel, tiles = tiles)
        }
    }

    private fun buildTile(
        metric: MetricType,
        latest: MetricReading?,
        payloadByReadingId: Map<String, List<EventPayloadField>>,
        sourceLabels: Map<String, String>,
        nowMs: Long,
    ): Tile {
        val range = BiomarkerReferenceRanges.forMetric(metric)
        val value = latest?.value
        val timestamp = latest?.timestamp
        val ageDays = timestamp?.let { ((nowMs - it) / MILLIS_PER_DAY).coerceAtLeast(0L) }
        val context = latest?.let {
            BiomarkerEntryRepo.rowsToContext(
                rows = payloadByReadingId[it.id].orEmpty(),
                note = it.note,
            )
        } ?: BiomarkerContext()
        val classification = if (value != null && range != null) {
            range.classify(value)
        } else {
            ReferenceRange.Classification.UNKNOWN
        }
        return Tile(
            metricType = metric,
            value = value,
            lastReadingTimestampMs = timestamp,
            ageDays = ageDays,
            range = range,
            classification = classification,
            sourceLabel = latest?.sourceId?.let { sourceLabels[it] },
            context = context,
        )
    }

    internal const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
}
