package com.bios.app

import com.bios.app.ingest.PhoneSleepQuietHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Quiet-hours gate covers the daytime / nighttime split that
 * [PowerConnectionReceiver] keys off — a "phone plugged in" event at
 * 14:00 must not start the collection service, while one at 23:30 (or
 * 06:00 the next morning) must. Wrap-around behaviour is the only
 * non-obvious bit.
 */
class PhoneSleepQuietHoursTest {

    private val zone = ZoneId.of("UTC")

    private fun at(hour: Int, minute: Int = 0) =
        ZonedDateTime.of(LocalDate.of(2026, 5, 23), LocalTime.of(hour, minute), zone)

    @Test
    fun midnight_is_in_default_quiet_hours() {
        assertTrue(PhoneSleepQuietHours.isInQuietHours(at(0, 0)))
    }

    @Test
    fun three_am_is_in_default_quiet_hours() {
        assertTrue(PhoneSleepQuietHours.isInQuietHours(at(3, 0)))
    }

    @Test
    fun seven_am_is_in_default_quiet_hours() {
        assertTrue(PhoneSleepQuietHours.isInQuietHours(at(7, 59)))
    }

    @Test
    fun eight_am_sharp_is_outside_quiet_hours() {
        // End is exclusive — 08:00 itself is daytime.
        assertFalse(PhoneSleepQuietHours.isInQuietHours(at(8, 0)))
    }

    @Test
    fun afternoon_charging_is_outside_quiet_hours() {
        assertFalse(PhoneSleepQuietHours.isInQuietHours(at(14, 0)))
        assertFalse(PhoneSleepQuietHours.isInQuietHours(at(17, 30)))
    }

    @Test
    fun nine_pm_is_in_quiet_hours() {
        assertTrue(PhoneSleepQuietHours.isInQuietHours(at(21, 0)))
        assertTrue(PhoneSleepQuietHours.isInQuietHours(at(23, 30)))
    }

    @Test
    fun just_before_nine_pm_is_outside_quiet_hours() {
        assertFalse(PhoneSleepQuietHours.isInQuietHours(at(20, 59)))
    }

    @Test
    fun non_wrapping_band_treats_inside_as_in_band() {
        val start = LocalTime.of(13, 0)
        val end = LocalTime.of(15, 0)
        assertTrue(PhoneSleepQuietHours.isInQuietHours(at(14, 0), start, end))
        assertFalse(PhoneSleepQuietHours.isInQuietHours(at(12, 59), start, end))
        assertFalse(PhoneSleepQuietHours.isInQuietHours(at(15, 0), start, end))
    }
}
