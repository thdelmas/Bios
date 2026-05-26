package com.bios.app

import com.bios.app.engine.applyPayloadExclusion
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.app.model.SeizureEventFields
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for the #269 Cut 2 safety gate: wearable-inferred
 * SEIZURE_EVENT rows must not feed the URGENT / ADVISORY seizure patterns
 * in [com.bios.app.alerts.NeurologyUrgentPatterns]. The full evaluator
 * path (`AnomalyDetector.fetchAbsoluteWindowValues`) builds the
 * `payloadsByReading` map from the DAO; this file pins the filtering
 * decision over fake inputs so the safety semantics are nailed down
 * independently of Room.
 */
class PayloadExclusionTest {

    private val sourceId = "test-source"
    private fun row(id: String): MetricReading = MetricReading(
        id = id,
        metricType = MetricType.SEIZURE_EVENT.key,
        value = 1.0,
        timestamp = 0L,
        durationSec = 360,
        sourceId = sourceId,
        confidence = 50,
    )

    private val detectionSourceGate =
        SeizureEventFields.DETECTION_SOURCE to
            SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED

    @Test
    fun bare_owner_logged_rows_pass_through() {
        // Historical owner-logged seizure entries have no event_payloads
        // sidecar at all. The safety gate must not silently drop them.
        val rows = listOf(row("a"), row("b"))
        val filtered = applyPayloadExclusion(rows, emptyMap(), detectionSourceGate)
        assertEquals(rows, filtered)
    }

    @Test
    fun explicit_owner_logged_rows_pass_through() {
        val rows = listOf(row("a"))
        val payloads = mapOf(
            "a" to listOf(
                EventPayloadField(
                    readingId = "a",
                    fieldKey = SeizureEventFields.DETECTION_SOURCE,
                    stringValue = SeizureEventFields.DETECTION_SOURCE_OWNER_LOGGED,
                )
            )
        )
        val filtered = applyPayloadExclusion(rows, payloads, detectionSourceGate)
        assertEquals(rows, filtered)
    }

    @Test
    fun wearable_inferred_rows_are_dropped() {
        val rows = listOf(row("a"))
        val payloads = mapOf(
            "a" to listOf(
                EventPayloadField(
                    readingId = "a",
                    fieldKey = SeizureEventFields.DETECTION_SOURCE,
                    stringValue = SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
                )
            )
        )
        val filtered = applyPayloadExclusion(rows, payloads, detectionSourceGate)
        assertTrue("wearable-inferred row must not escalate", filtered.isEmpty())
    }

    @Test
    fun mixed_population_drops_only_wearable_inferred() {
        // A realistic 24h cluster window: one bare owner-logged event,
        // one explicit owner_logged, one wearable_inferred. The cluster
        // pattern's absoluteMinReadings is 3 — after the safety gate the
        // surviving count must be 2 so the cluster fails to fire from
        // detector noise alone.
        val rows = listOf(row("bare"), row("owner"), row("wear"))
        val payloads = mapOf(
            "owner" to listOf(
                EventPayloadField(
                    readingId = "owner",
                    fieldKey = SeizureEventFields.DETECTION_SOURCE,
                    stringValue = SeizureEventFields.DETECTION_SOURCE_OWNER_LOGGED,
                )
            ),
            "wear" to listOf(
                EventPayloadField(
                    readingId = "wear",
                    fieldKey = SeizureEventFields.DETECTION_SOURCE,
                    stringValue = SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
                ),
                EventPayloadField(
                    readingId = "wear",
                    fieldKey = SeizureEventFields.MEDIAN_HR_BPM,
                    doubleValue = 112.0,
                ),
            ),
        )
        val filtered = applyPayloadExclusion(rows, payloads, detectionSourceGate)
        assertEquals(2, filtered.size)
        assertEquals(setOf("bare", "owner"), filtered.map { it.id }.toSet())
    }

    @Test
    fun unrelated_payload_fields_do_not_drop_the_row() {
        // A wearable-inferred row carries median_hr_bpm and baseline_hr_bpm
        // alongside the detection_source field. The filter must key on
        // (fieldKey, stringValue) — a row that only carries the HR
        // substrate (no detection_source row) is treated as bare and
        // passes through.
        val rows = listOf(row("a"))
        val payloads = mapOf(
            "a" to listOf(
                EventPayloadField(
                    readingId = "a",
                    fieldKey = SeizureEventFields.MEDIAN_HR_BPM,
                    doubleValue = 95.0,
                )
            )
        )
        val filtered = applyPayloadExclusion(rows, payloads, detectionSourceGate)
        assertEquals(rows, filtered)
    }
}
