package com.bios.app

import com.bios.app.ui.seizure.SeizureEntryValidator
import com.bios.app.ui.seizure.parseDurationSec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of the owner-logging entry path (#269 follow-up).
 *
 * Two pieces under test:
 *  - [SeizureEntryValidator]: bounds checks (duration, future, lookback)
 *  - [parseDurationSec]: minutes-as-text parser used by the entry dialog
 *
 * Together they guarantee the URGENT pattern's `durationAtLeastSec = 300`
 * filter can finally match owner-logged input (a 5-min entry becomes
 * `durationSec = 300` end-to-end).
 */
class SeizureEntryValidatorTest {

    private val now = 1_700_000_000_000L

    // -- parseDurationSec --

    @Test
    fun parseDurationSec_accepts_integer_minutes() {
        assertEquals(300, parseDurationSec("5"))
        assertEquals(600, parseDurationSec("10"))
    }

    @Test
    fun parseDurationSec_accepts_decimal_minutes() {
        assertEquals(150, parseDurationSec("2.5"))
    }

    @Test
    fun parseDurationSec_rejects_empty_garbage_and_negatives() {
        assertNull(parseDurationSec(""))
        assertNull(parseDurationSec("abc"))
        assertNull(parseDurationSec("-5"))
        assertNull(parseDurationSec("0"))
    }

    // -- SeizureEntryValidator --

    @Test
    fun validate_accepts_a_just_ended_five_minute_seizure() {
        // The canonical case the URGENT pattern needs to match.
        val result = SeizureEntryValidator.validate(
            timestampMs = now - 5 * 60 * 1000,
            durationSec = 300,
            nowMs = now,
        )
        assertTrue(result is SeizureEntryValidator.Result.Valid)
        result as SeizureEntryValidator.Result.Valid
        assertEquals(300, result.durationSec)
    }

    @Test
    fun validate_rejects_zero_or_negative_duration() {
        val zero = SeizureEntryValidator.validate(now, 0, now)
        val neg = SeizureEntryValidator.validate(now, -10, now)
        assertTrue(zero is SeizureEntryValidator.Result.Invalid)
        assertTrue(neg is SeizureEntryValidator.Result.Invalid)
    }

    @Test
    fun validate_rejects_duration_above_one_hour() {
        val result = SeizureEntryValidator.validate(
            timestampMs = now,
            durationSec = SeizureEntryValidator.MAX_DURATION_SEC + 1,
            nowMs = now,
        )
        assertTrue(result is SeizureEntryValidator.Result.Invalid)
    }

    @Test
    fun validate_rejects_far_future_timestamps() {
        val result = SeizureEntryValidator.validate(
            // 10 minutes in the future — beyond the 5-minute clock-skew slop.
            timestampMs = now + 10 * 60 * 1000,
            durationSec = 60,
            nowMs = now,
        )
        assertTrue(result is SeizureEntryValidator.Result.Invalid)
    }

    @Test
    fun validate_accepts_timestamps_within_the_future_slop() {
        // 2 minutes in the future — under the 5-minute slop, accept.
        val result = SeizureEntryValidator.validate(
            timestampMs = now + 2 * 60 * 1000,
            durationSec = 60,
            nowMs = now,
        )
        assertTrue(result is SeizureEntryValidator.Result.Valid)
    }

    @Test
    fun validate_rejects_timestamps_older_than_the_lookback_window() {
        val result = SeizureEntryValidator.validate(
            // 31 days ago — outside the 30-day live-entry window.
            timestampMs = now - 31L * 24 * 60 * 60 * 1000,
            durationSec = 60,
            nowMs = now,
        )
        assertTrue(result is SeizureEntryValidator.Result.Invalid)
    }

    @Test
    fun validate_accepts_timestamps_inside_the_lookback_window() {
        val result = SeizureEntryValidator.validate(
            // 5 days ago — inside the 30-day window, accept.
            timestampMs = now - 5L * 24 * 60 * 60 * 1000,
            durationSec = 60,
            nowMs = now,
        )
        assertTrue(result is SeizureEntryValidator.Result.Valid)
    }

    @Test
    fun validate_min_and_max_duration_boundaries_are_inclusive() {
        val atMin = SeizureEntryValidator.validate(
            timestampMs = now,
            durationSec = SeizureEntryValidator.MIN_DURATION_SEC,
            nowMs = now,
        )
        val atMax = SeizureEntryValidator.validate(
            timestampMs = now,
            durationSec = SeizureEntryValidator.MAX_DURATION_SEC,
            nowMs = now,
        )
        assertTrue("min duration boundary should be valid", atMin is SeizureEntryValidator.Result.Valid)
        assertTrue("max duration boundary should be valid", atMax is SeizureEntryValidator.Result.Valid)
    }
}
