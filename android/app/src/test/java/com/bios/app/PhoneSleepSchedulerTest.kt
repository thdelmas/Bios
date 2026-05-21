package com.bios.app

import com.bios.app.ingest.PhoneSleepScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure-clock tests for [PhoneSleepScheduler] (#134).
 *
 * Exercises the morning-trigger contract: skip before the wake hour,
 * fire once after it, then stay skipped for the rest of the day after
 * a successful inference. Uses a fixed UTC zone so the wake-hour math
 * is reproducible without depending on the host JVM's default zone.
 */
class PhoneSleepSchedulerTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `before wake hour returns Skip`() {
        val now = LocalDateTime.of(2026, 5, 21, 7, 30).atZone(zone).toInstant().toEpochMilli()
        val out = PhoneSleepScheduler.decide(
            nowMs = now,
            zoneId = zone,
            wakeHour = 9,
            lastInferredMidpointMs = null,
        )
        assertEquals(PhoneSleepScheduler.TriggerDecision.Skip, out)
    }

    @Test
    fun `exactly at wake hour returns Fire`() {
        val now = LocalDateTime.of(2026, 5, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val out = PhoneSleepScheduler.decide(
            nowMs = now,
            zoneId = zone,
            wakeHour = 9,
            lastInferredMidpointMs = null,
        )
        assertTrue("expected Fire, got $out", out is PhoneSleepScheduler.TriggerDecision.Fire)
    }

    @Test
    fun `fire window spans noon yesterday to wake hour today`() {
        val now = LocalDateTime.of(2026, 5, 21, 9, 15).atZone(zone).toInstant().toEpochMilli()
        val out = PhoneSleepScheduler.decide(
            nowMs = now,
            zoneId = zone,
            wakeHour = 9,
            lastInferredMidpointMs = null,
        ) as PhoneSleepScheduler.TriggerDecision.Fire

        val expectedStart = LocalDateTime.of(2026, 5, 20, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val expectedEnd = LocalDateTime.of(2026, 5, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedStart, out.windowStartMs)
        assertEquals(expectedEnd, out.windowEndMs)
    }

    @Test
    fun `dedupes when last inference midpoint falls inside this morning's window`() {
        // Already ran at 09:15 → midpoint was 04:00 local last night. The
        // 09:30 firing should skip, not re-fire.
        val now = LocalDateTime.of(2026, 5, 21, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val lastMidpoint = LocalDateTime.of(2026, 5, 21, 4, 0).atZone(zone).toInstant().toEpochMilli()
        val out = PhoneSleepScheduler.decide(
            nowMs = now,
            zoneId = zone,
            wakeHour = 9,
            lastInferredMidpointMs = lastMidpoint,
        )
        assertEquals(PhoneSleepScheduler.TriggerDecision.Skip, out)
    }

    @Test
    fun `fires again on a new morning after yesterday's inference`() {
        // Last midpoint from May 20's night-of (e.g., 03:00 local) — the
        // current window is May 21's overnight, which starts at noon
        // May 20 → 09:00 May 21. The May-20 midpoint sits in the prior
        // window, not this one, so dedupe doesn't trip.
        val now = LocalDateTime.of(2026, 5, 22, 9, 5).atZone(zone).toInstant().toEpochMilli()
        val lastMidpoint = LocalDateTime.of(2026, 5, 21, 3, 0).atZone(zone).toInstant().toEpochMilli()
        val out = PhoneSleepScheduler.decide(
            nowMs = now,
            zoneId = zone,
            wakeHour = 9,
            lastInferredMidpointMs = lastMidpoint,
        )
        assertTrue("expected Fire after a new midnight, got $out",
            out is PhoneSleepScheduler.TriggerDecision.Fire)
    }

    @Test
    fun `custom wake hour shifts both the threshold and the window end`() {
        // Configurable wake hour: an owner who wakes at 7 should get an
        // earlier trigger and an earlier window end.
        val now = LocalDateTime.of(2026, 5, 21, 7, 15).atZone(zone).toInstant().toEpochMilli()
        val out = PhoneSleepScheduler.decide(
            nowMs = now,
            zoneId = zone,
            wakeHour = 7,
            lastInferredMidpointMs = null,
        ) as PhoneSleepScheduler.TriggerDecision.Fire
        val expectedEnd = LocalDateTime.of(2026, 5, 21, 7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedEnd, out.windowEndMs)
    }

    @Test
    fun `pruneCutoffMs trails now by the retention window`() {
        val now = LocalDateTime.of(2026, 5, 21, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val cutoff = PhoneSleepScheduler.pruneCutoffMs(now, retentionDays = 2)
        val expected = LocalDateTime.of(2026, 5, 19, 12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, cutoff)
    }
}
