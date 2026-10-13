package com.bios.app

import com.bios.app.ui.trends.BucketUnit
import com.bios.app.ui.trends.DayTotal
import com.bios.app.ui.trends.MetricChartTimeWindow
import com.bios.app.ui.trends.bucketKeys
import com.bios.app.ui.trends.bucketLabel
import com.bios.app.ui.trends.bucketTick
import com.bios.app.ui.trends.bucketUnitFor
import com.bios.app.ui.trends.calendarStartMillis
import com.bios.app.ui.trends.dayTotalWindows
import com.bios.app.ui.trends.fillBuckets
import com.bios.app.ui.trends.formatTotal
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Pure-JVM checks for the day-totals bucketing (docs/specs/day-totals.md):
 * calendar keys, zero-filling, DST-safe window starts, labels, formatting.
 */
class DayTotalsTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun at(date: String, time: String = "14:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(paris).toInstant().toEpochMilli()

    @Test
    fun week_keys_are_seven_calendar_days_ending_today() {
        val keys = bucketKeys(MetricChartTimeWindow.WEEK, at("2026-10-13"), paris)
        assertEquals(7, keys.size)
        assertEquals("2026-10-07", keys.first())
        assertEquals("2026-10-13", keys.last())
    }

    @Test
    fun month_keys_are_thirty_days() {
        val keys = bucketKeys(MetricChartTimeWindow.MONTH, at("2026-10-13"), paris)
        assertEquals(30, keys.size)
        assertEquals("2026-09-14", keys.first())
    }

    @Test
    fun day_keys_are_24_hours_of_today() {
        val keys = bucketKeys(MetricChartTimeWindow.DAY, at("2026-10-13"), paris)
        assertEquals(24, keys.size)
        assertEquals("2026-10-13T00", keys.first())
        assertEquals("2026-10-13T23", keys.last())
    }

    @Test
    fun year_and_all_keys_are_months() {
        val year = bucketKeys(MetricChartTimeWindow.YEAR, at("2026-10-13"), paris)
        assertEquals(12, year.size)
        assertEquals("2025-11", year.first())
        assertEquals("2026-10", year.last())
        val all = bucketKeys(MetricChartTimeWindow.ALL, at("2026-10-13"), paris)
        assertEquals(24, all.size)
        assertEquals("2024-11", all.first())
    }

    @Test
    fun week_spanning_dst_end_still_has_seven_keys_and_a_midnight_start() {
        // Europe/Paris leaves DST on 2026-10-25 (a 25-hour day).
        val now = at("2026-10-26", "09:00")
        val keys = bucketKeys(MetricChartTimeWindow.WEEK, now, paris)
        assertEquals(7, keys.size)
        assertTrue(keys.contains("2026-10-25"))
        val start = calendarStartMillis(MetricChartTimeWindow.WEEK, now, paris)
        assertEquals(at("2026-10-20", "00:00"), start)
    }

    @Test
    fun day_window_starts_at_local_midnight_not_now_minus_24h() {
        val now = at("2026-10-13", "14:00")
        assertEquals(at("2026-10-13", "00:00"), calendarStartMillis(MetricChartTimeWindow.DAY, now, paris))
    }

    @Test
    fun year_window_starts_on_the_first_of_the_month_eleven_months_back() {
        val now = at("2026-10-13")
        assertEquals(at("2025-11-01", "00:00"), calendarStartMillis(MetricChartTimeWindow.YEAR, now, paris))
    }

    @Test
    fun fill_buckets_zero_fills_missing_days_and_keeps_order() {
        val keys = listOf("2026-10-11", "2026-10-12", "2026-10-13")
        val filled = fillBuckets(keys, mapOf("2026-10-11" to 8432.0, "2026-10-13" to 2844.0))
        assertEquals(
            listOf(
                DayTotal("2026-10-11", 8432.0, true),
                DayTotal("2026-10-12", 0.0, false),
                DayTotal("2026-10-13", 2844.0, true),
            ),
            filled,
        )
    }

    @Test
    fun labels_name_today_yesterday_and_weekday_dates() {
        val today = LocalDate.parse("2026-10-13")
        assertEquals("Today", bucketLabel("2026-10-13", BucketUnit.DAY, today, Locale.ENGLISH))
        assertEquals("Yesterday", bucketLabel("2026-10-12", BucketUnit.DAY, today, Locale.ENGLISH))
        assertEquals("Sat 10 Oct", bucketLabel("2026-10-10", BucketUnit.DAY, today, Locale.ENGLISH))
        assertEquals("14:00", bucketLabel("2026-10-13T14", BucketUnit.HOUR, today, Locale.ENGLISH))
        assertEquals("Oct 2026", bucketLabel("2026-10", BucketUnit.MONTH, today, Locale.ENGLISH))
    }

    @Test
    fun ticks_are_one_letter_days_and_bare_hours() {
        assertEquals("S", bucketTick("2026-10-10", BucketUnit.DAY, Locale.ENGLISH))
        assertEquals("0", bucketTick("2026-10-13T00", BucketUnit.HOUR, Locale.ENGLISH))
        assertEquals("14", bucketTick("2026-10-13T14", BucketUnit.HOUR, Locale.ENGLISH))
        assertEquals("O", bucketTick("2026-10", BucketUnit.MONTH, Locale.ENGLISH))
    }

    @Test
    fun totals_are_thousands_separated_with_the_metric_name_as_unit() {
        assertEquals("2,844 steps", formatTotal(2844.0, MetricType.STEPS, Locale.US))
        assertEquals("610 kcal", formatTotal(610.4, MetricType.ACTIVE_CALORIES, Locale.US))
        assertEquals("1h 12m", formatTotal(4320.0, MetricType.ACTIVE_MINUTES, Locale.US))
    }

    @Test
    fun hour_window_is_not_offered_and_maps_to_hour_buckets_anyway() {
        assertFalse(dayTotalWindows.contains(MetricChartTimeWindow.HOUR))
        assertEquals(BucketUnit.HOUR, bucketUnitFor(MetricChartTimeWindow.HOUR))
        assertEquals(BucketUnit.HOUR, bucketUnitFor(MetricChartTimeWindow.DAY))
        assertEquals(BucketUnit.DAY, bucketUnitFor(MetricChartTimeWindow.WEEK))
        assertEquals(BucketUnit.DAY, bucketUnitFor(MetricChartTimeWindow.MONTH))
        assertEquals(BucketUnit.MONTH, bucketUnitFor(MetricChartTimeWindow.YEAR))
        assertEquals(BucketUnit.MONTH, bucketUnitFor(MetricChartTimeWindow.ALL))
    }
}
