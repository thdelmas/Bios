package com.bios.app

import com.bios.app.model.EventPayloadField
import com.bios.app.model.SeizureEventFields
import com.bios.app.ui.seizure.DetectionSource
import com.bios.app.ui.seizure.SeizureSourceClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [SeizureSourceClassifier] (#269 Cut 3c).
 * Pins how SEIZURE_EVENT provenance + the peri-event HR substrate
 * are extracted from the `event_payloads` sidecar so the timeline
 * renderer can show wearable-detected vs owner-logged rows
 * differently without re-querying.
 */
class SeizureSourceClassifierTest {

    private val readingId = "r1"

    @Test
    fun empty_payload_is_classified_as_implicit_owner_logged() {
        val source = SeizureSourceClassifier.classify(emptyList())
        assertEquals(DetectionSource.OwnerLoggedImplicit, source)
    }

    @Test
    fun payload_without_detection_source_field_is_implicit_owner_logged() {
        // The HR substrate is present (a future owner-log surface might
        // attach context fields too), but no detection_source — keep the
        // implicit classification.
        val payload = listOf(
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.MEDIAN_HR_BPM,
                doubleValue = 95.0,
            )
        )
        val source = SeizureSourceClassifier.classify(payload)
        assertEquals(DetectionSource.OwnerLoggedImplicit, source)
    }

    @Test
    fun explicit_owner_logged_payload_is_classified_as_OwnerLogged() {
        val payload = listOf(
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.DETECTION_SOURCE,
                stringValue = SeizureEventFields.DETECTION_SOURCE_OWNER_LOGGED,
            )
        )
        val source = SeizureSourceClassifier.classify(payload)
        assertEquals(DetectionSource.OwnerLogged, source)
    }

    @Test
    fun wearable_inferred_payload_carries_the_hr_substrate() {
        val payload = listOf(
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.DETECTION_SOURCE,
                stringValue = SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
            ),
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.MEDIAN_HR_BPM,
                doubleValue = 120.0,
            ),
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.BASELINE_HR_BPM,
                doubleValue = 80.0,
            ),
        )
        val source = SeizureSourceClassifier.classify(payload)
        assertTrue(source is DetectionSource.WearableInferred)
        val sub = (source as DetectionSource.WearableInferred).substrate
        assertEquals(120.0, sub.medianHrBpm!!, 1e-9)
        assertEquals(80.0, sub.baselineHrBpm!!, 1e-9)
        // (120 - 80) / 80 = 0.5 → 50%
        assertEquals(50, sub.risePercent)
    }

    @Test
    fun wearable_inferred_without_hr_substrate_still_classifies_but_rise_is_null() {
        // Defensive case: an older wearable-inferred row that pre-dates
        // the substrate fields should still classify; risePercent is
        // null when either field is missing.
        val payload = listOf(
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.DETECTION_SOURCE,
                stringValue = SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
            ),
        )
        val source = SeizureSourceClassifier.classify(payload)
        assertTrue(source is DetectionSource.WearableInferred)
        val sub = (source as DetectionSource.WearableInferred).substrate
        assertNull(sub.medianHrBpm)
        assertNull(sub.baselineHrBpm)
        assertNull(sub.risePercent)
    }

    @Test
    fun risePercent_is_null_when_baseline_is_zero_or_negative() {
        // Math guard — division by zero must not crash the renderer.
        val payload = listOf(
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.DETECTION_SOURCE,
                stringValue = SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
            ),
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.MEDIAN_HR_BPM,
                doubleValue = 120.0,
            ),
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.BASELINE_HR_BPM,
                doubleValue = 0.0,
            ),
        )
        val source = SeizureSourceClassifier.classify(payload)
        val sub = (source as DetectionSource.WearableInferred).substrate
        assertNull(sub.risePercent)
    }

    @Test
    fun unknown_detection_source_value_falls_through_to_implicit() {
        // Defensive: a value we don't recognise (mis-spelt, future-key
        // we don't know about) should not crash and should not falsely
        // claim wearable-inferred. Implicit owner-logged is the safe
        // default — the safety gate (#287) only filters on the exact
        // wearable_inferred string anyway.
        val payload = listOf(
            EventPayloadField(
                readingId = readingId,
                fieldKey = SeizureEventFields.DETECTION_SOURCE,
                stringValue = "unrecognised_future_value",
            )
        )
        val source = SeizureSourceClassifier.classify(payload)
        assertEquals(DetectionSource.OwnerLoggedImplicit, source)
    }
}
