package com.bios.app.ingest

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Quiet-hours gate for the charge-triggered phone-sleep collection
 * service (#241 Cut 1).
 *
 * Plugging the phone in to charge during the day (commute, desk, café
 * power bank) is not "going to sleep" and must not trigger a
 * sleep-collection foreground service. The receiver consults this gate
 * before starting the service: only `ACTION_POWER_CONNECTED` events that
 * land inside the quiet-hours window are honoured.
 *
 * Defaults are 21:00–08:00 local — the canonical adult sleep band used
 * by [PhoneSleepInference] internally. The window wraps midnight by
 * construction.
 *
 * Pure helper; lives in `ingest/` only because that's where its callers
 * sit. No Android dependencies — testable in straight JVM JUnit.
 */
object PhoneSleepQuietHours {

    /** Default start of the quiet-hours band (local time). */
    val DEFAULT_START: LocalTime = LocalTime.of(21, 0)

    /** Default end of the quiet-hours band (local time). */
    val DEFAULT_END: LocalTime = LocalTime.of(8, 0)

    /**
     * True when [now] falls inside `[start, end)` interpreted as a
     * local-time band on `zoneId`. The band wraps midnight: when
     * `start > end` (the default 21:00 → 08:00 case), `now` is inside
     * the band iff it is at or after `start` *or* strictly before `end`.
     */
    fun isInQuietHours(
        now: ZonedDateTime,
        start: LocalTime = DEFAULT_START,
        end: LocalTime = DEFAULT_END,
    ): Boolean {
        val t = now.toLocalTime()
        return if (start < end) {
            // Non-wrapping band — e.g. 13:00..15:00 — `now` must sit inside.
            t >= start && t < end
        } else {
            // Wrapping band — e.g. 21:00..08:00 — `now` is inside iff it
            // is after start OR strictly before end. The two halves never
            // overlap so OR is safe.
            t >= start || t < end
        }
    }

    /**
     * Convenience overload that reads system time on the given zone.
     * Production callers pass `ZoneId.systemDefault()`; tests inject a
     * fixed [ZonedDateTime] via [isInQuietHours] instead.
     */
    fun isInQuietHoursNow(
        zoneId: ZoneId = ZoneId.systemDefault(),
        start: LocalTime = DEFAULT_START,
        end: LocalTime = DEFAULT_END,
    ): Boolean = isInQuietHours(ZonedDateTime.now(zoneId), start, end)
}
