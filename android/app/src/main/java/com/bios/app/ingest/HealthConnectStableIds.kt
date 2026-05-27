package com.bios.app.ingest

/**
 * Stable [com.bios.app.model.MetricReading] IDs derived from Health Connect's
 * own record identifiers (#312).
 *
 * Default `MetricReading.id` is `UUID.randomUUID()` — a brand-new value on
 * every fetch. Re-syncing the same HC record then inserts a parallel row
 * instead of updating in place, because:
 *  - the primary-key conflict path needs the *id* to repeat, but UUIDs do not;
 *  - the unique index on `(sourceId, metricType, timestamp)` only fires when
 *    the timestamp is identical, and HC silently shifts a session's
 *    `endTime` when the upstream wearable finalises a session retroactively
 *    or HC re-derives the boundary.
 *
 * Anchoring the row's primary key on the HC record id + a fixed kind prefix
 * (and the Bios `sourceId` so the same upstream record under two ingest
 * sources keeps separate rows) gives `OnConflictStrategy.REPLACE` a stable
 * key to update against — the row's timestamp and value can correct
 * themselves without leaving phantom rows.
 *
 * Scope today: the sleep paths in [HealthConnectAdapter.fetchSleep]
 * (sleep_stage rows and the sleep_duration session summary). Other HC
 * record paths (HR, steps, SpO2, etc.) are vulnerable to the same drift
 * pattern but have not surfaced an owner-visible symptom yet; they get
 * the same treatment in a follow-up PR keeping the per-path blast radius
 * small.
 */
internal object HealthConnectStableIds {

    /**
     * Stable ID for a 1:1 mapping (one HC record → one [MetricReading]).
     * Form: `hc-{kind}-{sourceId}-{hcRecordId}`.
     */
    fun forRecord(kind: String, sourceId: String, hcRecordId: String): String =
        "hc-$kind-$sourceId-$hcRecordId"

    /**
     * Stable ID for a sub-record mapping (one HC record → many
     * [MetricReading]s, e.g. sleep stages within a session, deltas within
     * a skin-temperature record). [subKey] disambiguates the sub-record;
     * the natural choice is the sub-record's own timestamp in ms because
     * it survives reordering and is stable across HC re-emissions of the
     * same physical sub-record.
     * Form: `hc-{kind}-{sourceId}-{hcRecordId}-{subKey}`.
     */
    fun forSubRecord(kind: String, sourceId: String, hcRecordId: String, subKey: Long): String =
        "hc-$kind-$sourceId-$hcRecordId-$subKey"
}
