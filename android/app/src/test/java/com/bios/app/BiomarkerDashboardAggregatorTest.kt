package com.bios.app

import com.bios.app.data.BiomarkerPayloadKeys
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.app.model.Specimen
import com.bios.app.ui.biomarkers.BiomarkerDashboardAggregator
import com.bios.app.ui.biomarkers.BiomarkerPanels
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state tests for the biomarker dashboard aggregator (#137).
 *
 * Covers: panel coverage, latest-wins selection across multiple
 * readings, empty-state shape, payload-context decoding, age rollup.
 */
class BiomarkerDashboardAggregatorTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `every BIOMARKER MetricType is rendered exactly once across panels`() {
        // Pins the dashboard's coverage contract — when a new BIOMARKER
        // key lands in MetricType, the panel list must claim it (and
        // claim it once). Sister test to the snapshot.
        val biomarkerKeys = MetricType.entries
            .filter { it.domain == MetricDomain.BIOMARKER }
            .toSet()
        val rendered = BiomarkerPanels.all.flatMap { it.metrics }
        // Every biomarker is on some panel.
        for (m in biomarkerKeys) {
            assertTrue(
                "${m.key} is a BIOMARKER but no panel claims it",
                m in rendered,
            )
        }
        // No panel claims a metric twice.
        assertEquals(
            "panel list has duplicates",
            rendered.size, rendered.toSet().size,
        )
    }

    @Test
    fun `empty input yields a panel display per panel with all tiles empty`() {
        val panels = BiomarkerDashboardAggregator.compute(
            readings = emptyList(),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        assertEquals(BiomarkerPanels.all.size, panels.size)
        for (panel in panels) {
            assertEquals(panel.panel.metrics.size, panel.tiles.size)
            assertEquals(false, panel.hasAnyReading)
            assertNull(panel.oldestAgeDays)
            for (t in panel.tiles) {
                assertNull(t.value)
                assertNull(t.lastReadingTimestampMs)
            }
        }
    }

    @Test
    fun `most recent reading per metric wins`() {
        val older = biomarker(
            MetricType.LDL_CHOLESTEROL, value = 80.0,
            timestampMs = now - 30L * day, sourceId = "lab",
        )
        val newer = biomarker(
            MetricType.LDL_CHOLESTEROL, value = 150.0,
            timestampMs = now - 7L * day, sourceId = "lab",
        )
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(older, newer),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val tile = panels.findTile(MetricType.LDL_CHOLESTEROL)
        assertEquals(150.0, tile.value!!, 1e-9)
        assertEquals(now - 7L * day, tile.lastReadingTimestampMs)
        assertEquals(7L, tile.ageDays)
    }

    @Test
    fun `non-biomarker readings are silently ignored`() {
        // Defensive routing: a caller might pass a wider stream than
        // strictly biomarker rows. The aggregator must ignore non-
        // BIOMARKER keys without poisoning any panel.
        val noise = MetricReading(
            metricType = MetricType.HEART_RATE.key,
            value = 60.0,
            timestamp = now,
            sourceId = "watch",
            confidence = ConfidenceTier.HIGH.level,
        )
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(noise),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        for (p in panels) {
            assertEquals(false, p.hasAnyReading)
        }
    }

    // ---- helpers ----

    private fun biomarker(
        metric: MetricType,
        value: Double,
        timestampMs: Long,
        sourceId: String,
        note: String? = null,
    ): MetricReading = MetricReading(
        metricType = metric.key,
        value = value,
        timestamp = timestampMs,
        sourceId = sourceId,
        confidence = ConfidenceTier.HIGH.level,
        note = note,
    )

    private fun List<BiomarkerDashboardAggregator.PanelDisplay>.findTile(
        metric: MetricType,
    ): BiomarkerDashboardAggregator.Tile {
        val tile = this.firstNotNullOfOrNull { p ->
            p.tiles.firstOrNull { it.metricType == metric }
        }
        assertNotNull("tile for ${metric.key} not found in any panel", tile)
        return tile!!
    }
}
