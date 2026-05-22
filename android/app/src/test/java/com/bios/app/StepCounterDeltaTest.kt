package com.bios.app

import com.bios.app.ingest.StepCounterDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the pure step-delta computation that fixes the
 * "+5000 steps every app open" cumulative-counter bug.
 */
class StepCounterDeltaTest {

    private val todayStart = 1_000_000_000_000L
    private val bootToday = todayStart + 60_000L
    private val bootYesterday = todayStart - 86_400_000L

    @Test
    fun `first reading after install with boot today emits cumulative`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 3_200L,
            bootMs = bootToday,
            lastCumulative = -1L,
            lastBootMs = -1L,
            todayStartMs = todayStart
        )
        assertEquals(3_200L, delta)
    }

    @Test
    fun `first reading after install with boot before today suppresses`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 25_000L,
            bootMs = bootYesterday,
            lastCumulative = -1L,
            lastBootMs = -1L,
            todayStartMs = todayStart
        )
        assertNull(delta)
    }

    @Test
    fun `subsequent reading emits only the new steps`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 8_700L,
            bootMs = bootYesterday,
            lastCumulative = 8_500L,
            lastBootMs = bootYesterday,
            todayStartMs = todayStart
        )
        assertEquals(200L, delta)
    }

    @Test
    fun `repeat read with no movement emits nothing`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 8_500L,
            bootMs = bootYesterday,
            lastCumulative = 8_500L,
            lastBootMs = bootYesterday,
            todayStartMs = todayStart
        )
        assertNull(delta)
    }

    @Test
    fun `reboot detected by counter decrease emits cumulative when boot today`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 1_200L,
            bootMs = bootToday,
            lastCumulative = 40_000L,
            lastBootMs = bootYesterday,
            todayStartMs = todayStart
        )
        assertEquals(1_200L, delta)
    }

    @Test
    fun `reboot detected with boot before today suppresses`() {
        val rebootedBeforeToday = todayStart - 3_600_000L
        val delta = StepCounterDelta.computePure(
            cumulative = 4_000L,
            bootMs = rebootedBeforeToday,
            lastCumulative = 40_000L,
            lastBootMs = bootYesterday,
            todayStartMs = todayStart
        )
        assertNull(delta)
    }

    @Test
    fun `reboot detected by boot-time shift even if counter rises`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 500L,
            bootMs = bootToday,
            lastCumulative = 400L,
            lastBootMs = bootYesterday,
            todayStartMs = todayStart
        )
        // Detected as reboot because boot-time jumped a full day,
        // so the 500 is treated as steps-since-boot (today), not 100 delta.
        assertEquals(500L, delta)
    }

    @Test
    fun `tiny boot-time jitter is not treated as reboot`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 8_700L,
            bootMs = bootYesterday + 1_000L,
            lastCumulative = 8_500L,
            lastBootMs = bootYesterday,
            todayStartMs = todayStart
        )
        assertEquals(200L, delta)
    }

    @Test
    fun `zero cumulative emits nothing`() {
        val delta = StepCounterDelta.computePure(
            cumulative = 0L,
            bootMs = bootToday,
            lastCumulative = -1L,
            lastBootMs = -1L,
            todayStartMs = todayStart
        )
        assertNull(delta)
    }

    @Test
    fun `regression - five opens in a row do not balloon the total`() {
        // Simulate: device booted yesterday, user has ~30k steps cumulative,
        // walks ~200 steps between opens. The old code would have added 30k
        // on every open. The new code emits 0 on first open (boot before
        // today, no baseline) then 200 per subsequent open.
        val sequence = listOf(30_000L, 30_200L, 30_400L, 30_600L, 30_800L)
        var last = -1L
        val emitted = mutableListOf<Long>()
        for (cum in sequence) {
            val d = StepCounterDelta.computePure(
                cumulative = cum,
                bootMs = bootYesterday,
                lastCumulative = last,
                lastBootMs = if (last < 0L) -1L else bootYesterday,
                todayStartMs = todayStart
            )
            if (d != null) emitted.add(d)
            last = cum
        }
        assertEquals(listOf(200L, 200L, 200L, 200L), emitted)
        assertEquals(800L, emitted.sum())
    }
}
