package com.bios.app.engine

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.app.model.SeizureEvent
import com.bios.app.model.SeizureEventFields
import com.bios.contracts.MetricType
import java.util.UUID

/**
 * Wraps a [SeizureDetector.Detection] into a writable composite
 * SEIZURE_EVENT (parent MetricReading + event_payloads sidecar).
 *
 * Kept separate from [SeizureDetector] so the detector stays pure
 * signal-processing (testable without touching the room schema) and
 * separate from the model package so the data classes stay pure data
 * (no engine dependency).
 *
 * Wearable-inferred events are written with [ConfidenceTier.LOW] —
 * screening-surrogate confidence, matching the manifesto guard from
 * #269: the timeline records the detection but the URGENT escalation
 * path stays gated on owner confirmation.
 */
object SeizureEventFactory {

    /**
     * Build the composite SEIZURE_EVENT row for a wearable-inferred
     * detection. `now` is injected for tests; production callers can
     * omit it.
     */
    fun fromWearableDetection(
        detection: SeizureDetector.Detection,
        sourceId: String,
        now: Long = System.currentTimeMillis(),
    ): SeizureEvent {
        val reading = MetricReading(
            id = UUID.randomUUID().toString(),
            metricType = MetricType.SEIZURE_EVENT.key,
            value = 1.0,
            timestamp = detection.startTimestampMs,
            durationSec = detection.durationSec,
            sourceId = sourceId,
            confidence = ConfidenceTier.LOW.level,
            createdAt = now,
        )
        val payload = listOf(
            EventPayloadField(
                readingId = reading.id,
                fieldKey = SeizureEventFields.DETECTION_SOURCE,
                stringValue = SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
            ),
            EventPayloadField(
                readingId = reading.id,
                fieldKey = SeizureEventFields.MEDIAN_HR_BPM,
                doubleValue = detection.medianHrBpm,
            ),
            EventPayloadField(
                readingId = reading.id,
                fieldKey = SeizureEventFields.BASELINE_HR_BPM,
                doubleValue = detection.baselineHrBpm,
            ),
        )
        return SeizureEvent(reading = reading, payload = payload)
    }
}
