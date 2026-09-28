package com.bios.app.ingest

import com.bios.app.model.SourceType
import java.util.concurrent.TimeUnit

/**
 * How often each source type is expected to deliver, so that "stopped
 * delivering" is measured in the source's own units rather than in rows.
 * A nightly-summary API that writes two rows a day and a phone sensor that
 * writes hundreds share one rule: a source is stale after it has missed
 * [STALE_INTERVALS] of its own intervals. See docs/specs/source-liveness.md.
 *
 * `null` means the source has no cadence Bios can hold it to (one-shot
 * captures, self-reports, Bios's own inference) and is never stale.
 */
object SourceCadence {

    /** Missed intervals before the source reads "stale" in the app. */
    const val STALE_INTERVALS = 2

    /** Missed intervals before a stale source earns a push. */
    const val PUSH_STALE_INTERVALS = 5

    /** Delivery span (first to last reading) that counts as "previously active". */
    const val ACTIVE_SPAN_INTERVALS = 2

    private val DAY = TimeUnit.DAYS.toMillis(1)
    private val HOUR = TimeUnit.HOURS.toMillis(1)

    fun intervalMillis(sourceType: SourceType): Long? = when (sourceType) {
        // Nightly summaries: one wake-day per delivery.
        SourceType.COROS_API, SourceType.OURA_API, SourceType.WHOOP_API,
        SourceType.GARMIN_API, SourceType.WITHINGS_API, SourceType.POLAR_API,
        SourceType.DEXCOM_API -> DAY
        // Passive bridges: the watch syncs when its app is opened, at least daily.
        SourceType.HEALTH_CONNECT, SourceType.GADGETBRIDGE -> DAY
        // In-process sensors run with the 15-minute sync; an hour of silence is two misses.
        SourceType.PHONE_SENSOR, SourceType.DIRECT_SENSOR -> HOUR
        SourceType.BLE_PERIPHERAL -> HOUR
        // Derived, one-shot, or owner-paced: no cadence to hold them to.
        SourceType.PHONE_SENSOR_DERIVED, SourceType.BIOS_INFERRED,
        SourceType.CAMERA_PPG, SourceType.SELF_REPORTED -> null
    }
}
