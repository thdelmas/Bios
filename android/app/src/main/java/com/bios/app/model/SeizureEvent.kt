package com.bios.app.model

// Composite SEIZURE_EVENT reading bundled with its event-payload fields.
// Mirrors the [ExerciseSession] shape: the parent MetricReading carries
// timestamp and durationSec; the payload carries the per-event structured
// fields that downstream readers join on event_payloads.reading_id.
//
// Owner-logged seizure rows do not need this composite — they are bare
// MetricReading rows with no payload (or with the
// [SeizureEventFields.DETECTION_SOURCE] field set to "owner_logged" when
// the journal entry surface starts populating it). The composite is the
// shape wearable-inferred detections take so the per-event substrate
// (median HR, baseline HR) stays attached to the parent row.
data class SeizureEvent(
    val reading: MetricReading,
    val payload: List<EventPayloadField>,
)

// Field keys for the SEIZURE_EVENT payload. Centralized so adapters,
// detectors, and pull-side readers can't drift on spelling. New keys
// belong in docs/DATA_MODEL.md alongside the SEIZURE_EVENT metric.
object SeizureEventFields {
    // Distinguishes wearable-inferred detections from owner-logged ones
    // so the pull-side timeline can render them differently and the
    // URGENT escalation patterns can require owner confirmation before
    // firing on automation alone. Allowed values:
    // [DETECTION_SOURCE_WEARABLE_INFERRED], [DETECTION_SOURCE_OWNER_LOGGED].
    const val DETECTION_SOURCE = "detection_source"

    // Peri-event HR substrate from the ictal-tachycardia corroborator
    // (see SeizureDetector). Stored on the parent row so clinician-review
    // can see the actual ratio behind a wearable_inferred detection.
    const val MEDIAN_HR_BPM = "median_hr_bpm"
    const val BASELINE_HR_BPM = "baseline_hr_bpm"

    const val DETECTION_SOURCE_WEARABLE_INFERRED = "wearable_inferred"
    const val DETECTION_SOURCE_OWNER_LOGGED = "owner_logged"
}
