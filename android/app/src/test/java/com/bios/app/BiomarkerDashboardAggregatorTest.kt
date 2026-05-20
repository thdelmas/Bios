package com.bios.app

import com.bios.app.data.BiomarkerPayloadKeys
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.app.model.Specimen
import com.bios.app.ui.biomarkers.BiomarkerDashboardAggregator
import com.bios.app.ui.biomarkers.BiomarkerPanels
import com.bios.app.ui.biomarkers.BiomarkerReferenceRanges
import com.bios.app.ui.biomarkers.ReferenceRange
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
 * readings, classification passthrough against the reference table,
 * empty-state shape, payload-context decoding, staleness rollup.
 * Reference-range numeric correctness is checked by separate cases on
 * each ReferenceRange.classify call.
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
                assertEquals(ReferenceRange.Classification.UNKNOWN, t.classification)
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
        // 150 mg/dL LDL exceeds the 100 mg/dL primary-prevention cutoff
        // — classification must surface as ABOVE for the colour swap.
        assertEquals(ReferenceRange.Classification.ABOVE, tile.classification)
    }

    @Test
    fun `value within the range classifies as WITHIN`() {
        // LDL 80 < 100 cutoff → WITHIN.
        val r = biomarker(MetricType.LDL_CHOLESTEROL, value = 80.0, timestampMs = now, sourceId = "lab")
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(r),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        assertEquals(
            ReferenceRange.Classification.WITHIN,
            panels.findTile(MetricType.LDL_CHOLESTEROL).classification,
        )
    }

    @Test
    fun `value below low cutoff classifies as BELOW`() {
        // HDL 30 < 40 low cutoff → BELOW (HDL has a low-only range).
        val r = biomarker(MetricType.HDL_CHOLESTEROL, value = 30.0, timestampMs = now, sourceId = "lab")
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(r),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        assertEquals(
            ReferenceRange.Classification.BELOW,
            panels.findTile(MetricType.HDL_CHOLESTEROL).classification,
        )
    }

    @Test
    fun `payload context decodes lab name fasting and specimen`() {
        val r = biomarker(
            MetricType.HBA1C, value = 5.4, timestampMs = now - 10L * day, sourceId = "lab",
        )
        val payloads = listOf(
            EventPayloadField(r.id, BiomarkerPayloadKeys.LAB_NAME, stringValue = "Quest"),
            EventPayloadField(r.id, BiomarkerPayloadKeys.FASTING, longValue = 1L),
            EventPayloadField(r.id, BiomarkerPayloadKeys.SPECIMEN, stringValue = Specimen.SERUM.name),
        )
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(r),
            payloadByReadingId = mapOf(r.id to payloads),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val tile = panels.findTile(MetricType.HBA1C)
        assertEquals("Quest", tile.context.labName)
        assertEquals(true, tile.context.fasting)
        assertEquals(Specimen.SERUM, tile.context.specimen)
    }

    @Test
    fun `note rides the MetricReading column not the payload sidecar`() {
        // Documented split: free-text owner-recall note lives in the
        // reading row, not in event_payloads. The aggregator must surface
        // it on the tile context.
        val r = biomarker(
            MetricType.HSCRP, value = 0.4, timestampMs = now, sourceId = "lab",
            note = "post-flu draw, doctor flagged for recheck",
        )
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(r),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val tile = panels.findTile(MetricType.HSCRP)
        assertEquals("post-flu draw, doctor flagged for recheck", tile.context.note)
    }

    @Test
    fun `oldest age across a panel rolls up correctly`() {
        // LDL 7 days old, HDL 90 days old, others empty. The panel's
        // staleness chip keys off the oldest populated tile.
        val readings = listOf(
            biomarker(MetricType.LDL_CHOLESTEROL, 90.0, now - 7L * day, "lab"),
            biomarker(MetricType.HDL_CHOLESTEROL, 55.0, now - 90L * day, "lab"),
        )
        val panels = BiomarkerDashboardAggregator.compute(
            readings = readings,
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val cardiometabolic = panels.first { it.panel.id == BiomarkerPanels.cardiometabolic.id }
        assertEquals(90L, cardiometabolic.oldestAgeDays)
        assertEquals(true, cardiometabolic.hasAnyReading)
    }

    @Test
    fun `future readings are ignored`() {
        // Defensive against bad ingest: a reading timestamped after now
        // must not surface (could happen if Health Connect imports a
        // future-tagged row from a misconfigured device).
        val future = biomarker(
            MetricType.HBA1C, value = 5.0, timestampMs = now + day, sourceId = "lab",
        )
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(future),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        assertNull(panels.findTile(MetricType.HBA1C).value)
    }

    @Test
    fun `source label falls back to null when unknown`() {
        val r = biomarker(MetricType.HEMOGLOBIN, 14.0, now, "ghost")
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(r),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        assertNull(panels.findTile(MetricType.HEMOGLOBIN).sourceLabel)
    }

    @Test
    fun `sex-dependent endocrine assays surface without a range`() {
        // Testosterone has no shipped range (sex/age-dependent), so the
        // tile should render the value but classification stays UNKNOWN.
        val r = biomarker(
            MetricType.TESTOSTERONE_TOTAL, value = 500.0, timestampMs = now, sourceId = "lab",
        )
        val panels = BiomarkerDashboardAggregator.compute(
            readings = listOf(r),
            payloadByReadingId = emptyMap(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val tile = panels.findTile(MetricType.TESTOSTERONE_TOTAL)
        assertEquals(500.0, tile.value!!, 1e-9)
        assertNull(tile.range)
        assertEquals(ReferenceRange.Classification.UNKNOWN, tile.classification)
    }

    @Test
    fun `every shipped reference range has at least one bound`() {
        // Sanity check on BiomarkerReferenceRanges: a range with neither
        // low nor high is a UX bug — it can never trigger a colour
        // change, so it's worse than omitting the entry.
        for ((metric, range) in BiomarkerReferenceRanges.ranges) {
            assertTrue(
                "${metric.key} reference range must have low or high",
                range.low != null || range.high != null,
            )
        }
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
