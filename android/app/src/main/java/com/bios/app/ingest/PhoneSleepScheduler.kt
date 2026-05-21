package com.bios.app.ingest

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure decision layer for [PhoneSleepWorker] (#134).
 *
 * The worker fires every 15 minutes via WorkManager. Each firing does
 * two things: append one new phone-state sample, and ask this scheduler
 * whether the current moment crosses the "morning trigger" for last
 * night's sleep window. Doing both inside one worker keeps the
 * sample-and-infer lifecycle in a single place and avoids a second
 * WorkManager registration just for the morning sweep.
 *
 * The trigger fires when:
 *  1. We are past [wakeHour] today in local time.
 *  2. We have not already inferred sleep whose midpoint sits in
 *     yesterday's overnight window (cutoff = noon-to-noon to handle
 *     odd-hour sleepers).
 *
 * The "have we already inferred" check is the caller's responsibility —
 * the scheduler just returns a [TriggerDecision.Fire] with the window
 * to feed [com.bios.app.engine.PhoneSleepInference]. The caller then
 * dedupes against existing rows in `metric_readings`.
 *
 * Pure so unit tests exercise every clock + state combination without
 * touching WorkManager or Room.
 */
object PhoneSleepScheduler {

    sealed interface TriggerDecision {
        /** Not yet past the wake hour, or already inferred. Caller appends
         *  a sample and exits. */
        object Skip : TriggerDecision

        /** Time to run inference. [windowStartMs] / [windowEndMs] bound
         *  the overnight sample window. Caller fetches the buffer,
         *  invokes the inference, writes outputs, and prunes the buffer. */
        data class Fire(val windowStartMs: Long, val windowEndMs: Long) : TriggerDecision
    }

    /**
     * @param nowMs current epoch ms.
     * @param zoneId local time zone for the wake-hour comparison.
     * @param wakeHour hour-of-day at or after which we consider the
     *   previous night "complete".
     * @param lastInferredMidpointMs midpoint timestamp of the most
     *   recently produced PHONE_SENSOR_DERIVED sleep reading, or null
     *   if we've never run inference. Used to avoid re-firing during the
     *   same morning window.
     */
    fun decide(
        nowMs: Long,
        zoneId: ZoneId,
        wakeHour: Int = DEFAULT_WAKE_HOUR,
        lastInferredMidpointMs: Long? = null,
    ): TriggerDecision {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zoneId)
        val today = now.toLocalDate()

        // Wake threshold for today (e.g. today at 09:00 local).
        val wakeThreshold = today.atTime(LocalTime.of(wakeHour, 0)).atZone(zoneId)
        if (now.isBefore(wakeThreshold)) return TriggerDecision.Skip

        // Window for "last night" — noon yesterday → wakeHour today.
        // Noon-to-noon split is robust to graveyard-shift / siesta
        // sleepers whose sleep midpoint might land in the morning, not
        // the middle of the night.
        val windowStart = (today.minusDays(1).atTime(LocalTime.NOON)).atZone(zoneId)
        val windowEnd = wakeThreshold
        val windowStartMs = windowStart.toInstant().toEpochMilli()
        val windowEndMs = windowEnd.toInstant().toEpochMilli()

        // Dedupe: if we already produced a sleep reading whose midpoint
        // sits inside the same overnight window, don't re-fire.
        if (lastInferredMidpointMs != null &&
            lastInferredMidpointMs in windowStartMs..windowEndMs
        ) {
            return TriggerDecision.Skip
        }

        return TriggerDecision.Fire(
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
        )
    }

    /** Helper used by the worker after a successful inference to compute
     *  the cutoff for sample pruning — drop everything older than two
     *  windows back so a missed morning has a chance to recover. */
    fun pruneCutoffMs(nowMs: Long, retentionDays: Int = DEFAULT_RETENTION_DAYS): Long =
        nowMs - retentionDays.toLong() * MILLIS_PER_DAY

    /** Reasonable default wake-hour for the morning trigger. Owner-
     *  configurable surface is a follow-up; the constant is exposed for
     *  tests + future settings wiring. */
    const val DEFAULT_WAKE_HOUR: Int = 9

    /** Two-day retention: a missed morning trigger can still recover if
     *  the next morning's run sees yesterday's samples. */
    const val DEFAULT_RETENTION_DAYS: Int = 2

    private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

    /** Convenience date conversion for callers that want to log the
     *  inferred night as a calendar date. */
    fun nightLabelFor(midpointMs: Long, zoneId: ZoneId): LocalDate =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(midpointMs), zoneId).toLocalDate()
}
