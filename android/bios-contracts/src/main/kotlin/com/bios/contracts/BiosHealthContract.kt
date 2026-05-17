package com.bios.contracts

/**
 * URI surface of [BiosHealthProvider]. Companions construct URIs from these
 * constants rather than hardcoding strings. Renames here are breaking changes
 * across every consumer.
 *
 * Build full URIs with `Uri.parse("content://$AUTHORITY/...")` on the
 * companion side — this module is framework-free so it stays usable from
 * non-Android contexts (tests, JVM tooling).
 *
 * ### Reads
 *   content://com.bios.app.health/readings/{metricType}?start={epochMs}&end={epochMs}
 *   content://com.bios.app.health/baselines
 *   content://com.bios.app.health/baselines/{metricType}
 *   content://com.bios.app.health/status
 *   content://com.bios.app.health/status/{metricType}
 *   content://com.bios.app.health/payload/{readingId}
 *
 * ### Writes (companion signals only)
 *   content://com.bios.app.health/companion/{metricType}
 *   ContentValues: "value" (Double, required), "timestamp" (Long, optional — defaults to now)
 *   Caller must be in Bios's per-package allowlist (Settings → Companion Apps)
 *   AND hold [BiosPermissions.WRITE_COMPANION].
 */
object BiosHealthContract {
    const val AUTHORITY = "com.bios.app.health"

    const val PATH_READINGS = "readings"
    const val PATH_BASELINES = "baselines"
    const val PATH_STATUS = "status"
    const val PATH_COMPANION = "companion"
    const val PATH_PAYLOAD = "payload"

    /** Reading-row column names returned by `/readings/{metricType}` queries. */
    val READING_COLUMNS = arrayOf(
        "id", "metric_type", "value", "timestamp", "duration_sec",
        "source_id", "confidence", "is_primary"
    )

    /** Baseline-row column names returned by `/baselines[/...]` queries. */
    val BASELINE_COLUMNS = arrayOf(
        "metric_type", "context", "window_days", "computed_at",
        "mean", "std_dev", "p5", "p95", "trend", "trend_slope"
    )

    /** Status-row column names returned by `/status[/...]` queries. */
    val STATUS_COLUMNS = arrayOf(
        "metric_type", "last_ingested_at", "reading_count_24h", "reading_count_total"
    )

    /**
     * Payload-row column names returned by `/payload/{readingId}` queries.
     * Exactly one of `string_value`, `double_value`, `long_value` is non-null
     * per row. Field-key vocabulary lives in `docs/DATA_MODEL.md`.
     */
    val PAYLOAD_COLUMNS = arrayOf(
        "reading_id", "field_key", "string_value", "double_value", "long_value"
    )

    /** ContentValues keys accepted on `/companion/{metricType}` inserts. */
    object CompanionInsert {
        const val VALUE = "value"
        const val TIMESTAMP = "timestamp"
    }
}
