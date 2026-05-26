package com.bios.app

import com.bios.app.ui.sleep.ManualSleepEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state tests for [ManualSleepEntry.validate]. Covers the four
 * outcomes the tile and any future Compose surface need to render:
 * NotStarted, Valid, Invalid (wake-before-bedtime), Invalid (too short
 * — accidental double-tap), Invalid (over 24 h).
 */
class ManualSleepEntryTest {

    private val midnight = 1_716_500_000_000L

    @Test
    fun zero_bedtime_signals_not_started() {
        val result = ManualSleepEntry.validate(bedtimeMs = 0L, wakeMs = midnight)
        assertEquals(ManualSleepEntry.Result.NotStarted, result)
    }

    @Test
    fun negative_bedtime_signals_not_started() {
        val result = ManualSleepEntry.validate(bedtimeMs = -1L, wakeMs = midnight)
        assertEquals(ManualSleepEntry.Result.NotStarted, result)
    }

    @Test
    fun seven_hour_window_is_valid() {
        val bedtime = midnight
        val wake = bedtime + 7L * 3600L * 1000L
        val result = ManualSleepEntry.validate(bedtime, wake)
        assertTrue(result is ManualSleepEntry.Result.Valid)
        val range = (result as ManualSleepEntry.Result.Valid).range
        assertEquals(bedtime, range.bedtimeMs)
        assertEquals(wake, range.wakeMs)
        assertEquals(7L * 3600L, range.durationSeconds)
    }

    @Test
    fun fifteen_minute_window_is_at_the_minimum_floor() {
        val bedtime = midnight
        val wake = bedtime + 15L * 60L * 1000L
        assertTrue(ManualSleepEntry.validate(bedtime, wake) is ManualSleepEntry.Result.Valid)
    }

    @Test
    fun fourteen_minute_window_is_rejected_as_too_short() {
        val bedtime = midnight
        val wake = bedtime + 14L * 60L * 1000L
        val result = ManualSleepEntry.validate(bedtime, wake)
        assertTrue(result is ManualSleepEntry.Result.Invalid)
        assertTrue((result as ManualSleepEntry.Result.Invalid).reason.contains("15 minutes"))
    }

    @Test
    fun wake_before_bedtime_is_rejected() {
        val bedtime = midnight
        val wake = bedtime - 3600L * 1000L
        val result = ManualSleepEntry.validate(bedtime, wake)
        assertTrue(result is ManualSleepEntry.Result.Invalid)
        assertTrue((result as ManualSleepEntry.Result.Invalid).reason.contains("after bedtime"))
    }

    @Test
    fun wake_equal_to_bedtime_is_rejected() {
        val result = ManualSleepEntry.validate(midnight, midnight)
        assertTrue(result is ManualSleepEntry.Result.Invalid)
    }

    @Test
    fun over_24_hour_window_is_rejected_as_too_long() {
        val bedtime = midnight
        val wake = bedtime + 25L * 3600L * 1000L
        val result = ManualSleepEntry.validate(bedtime, wake)
        assertTrue(result is ManualSleepEntry.Result.Invalid)
        assertTrue((result as ManualSleepEntry.Result.Invalid).reason.contains("24 hours"))
    }
}
