package com.bios.app.engine

import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading

/**
 * Pure helper for [com.bios.app.alerts.SignalRule.excludePayloadFieldValue]
 * (#269 Cut 2 safety gate). Drops any row whose [payloadsByReading] entry
 * contains a field matching `(fieldKey, stringValue) == excludePayload`.
 * Rows missing from [payloadsByReading] pass through unchanged — that
 * matches the historical "bare owner-logged row, no payload" shape.
 *
 * Lifted out so unit tests can exercise the filtering rule without
 * touching Room, and so [AnomalyDetector] stays under the 500-line cap.
 */
internal fun applyPayloadExclusion(
    rows: List<MetricReading>,
    payloadsByReading: Map<String, List<EventPayloadField>>,
    excludePayload: Pair<String, String>,
): List<MetricReading> {
    val (fieldKey, excludedValue) = excludePayload
    return rows.filterNot { row ->
        payloadsByReading[row.id]?.any {
            it.fieldKey == fieldKey && it.stringValue == excludedValue
        } == true
    }
}
