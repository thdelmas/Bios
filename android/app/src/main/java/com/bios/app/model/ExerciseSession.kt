package com.bios.app.model

// Composite EXERCISE_SESSION reading bundled with its event-payload fields.
// Adapters return these from a session-specific fetch path; IngestManager
// writes the parent row to metric_readings and the payload rows to
// event_payloads in one logical insert.
//
// The reading itself is the canonical parent — the payload follows it by
// foreign key (CASCADE on delete). Pattern-engine code reads the parent
// for time/duration and joins on event_payloads.reading_id when it needs
// modality, avg-HR, or RPE.
data class ExerciseSession(
    val reading: MetricReading,
    val payload: List<EventPayloadField>,
)

// Modality enum carried in event_payloads.field_key="modality".string_value.
// Stable string constants — adapters and the pattern engine compile against
// these; renames break field-key contracts (docs/DATA_MODEL.md).
//
// Coarse on purpose. A single CARDIO bucket lumps running, biking, swimming
// because the cross-system patterns Bios cares about (overtraining,
// deconditioning) hinge on aerobic-vs-anaerobic-vs-mixed load, not on
// modality flavor. Fine-grained classification (which sport, indoor vs
// outdoor) stays in the source's own record and is not promoted to a
// canonical field.
object ExerciseModality {
    const val CARDIO = "CARDIO"
    const val STRENGTH = "STRENGTH"
    const val INTERVAL = "INTERVAL"
    const val MOBILITY = "MOBILITY"
    const val OTHER = "OTHER"
}

// Field keys for the EXERCISE_SESSION payload. Centralized so adapters and
// readers can't drift on spelling. New keys belong in docs/DATA_MODEL.md
// alongside the metric they describe.
object ExerciseSessionFields {
    const val MODALITY = "modality"
    const val START_UTC = "start_utc"
    const val END_UTC = "end_utc"
    const val AVG_HR_BPM = "avg_hr_bpm"
    const val RPE = "rpe"
}
