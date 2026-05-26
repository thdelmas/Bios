package com.bios.app

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.ui.sleep.WearableRecommendationGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for [WearableRecommendationGate.shouldShow]. Covers
 * the four firing cases the #245 banner UX cares about: too-few-nights,
 * all-low/missing, mixed (a single HIGH night blocks), and the post-
 * dismissal cooldown.
 */
class WearableRecommendationGateTest {

    private val night = WearableRecommendationGate.Night(emptyList())

    private fun lowNight(): WearableRecommendationGate.Night {
        return WearableRecommendationGate.Night(
            listOf(reading(confidence = ConfidenceTier.LOW))
        )
    }

    private fun missingNight(): WearableRecommendationGate.Night =
        WearableRecommendationGate.Night(emptyList())

    private fun mediumNight(): WearableRecommendationGate.Night {
        return WearableRecommendationGate.Night(
            listOf(reading(confidence = ConfidenceTier.MEDIUM))
        )
    }

    private fun reading(confidence: ConfidenceTier): MetricReading =
        MetricReading(
            metricType = "sleep_duration",
            value = 7.0 * 3600.0,
            timestamp = 0L,
            sourceId = "test",
            confidence = confidence.level,
        )

    @Test
    fun fewer_than_seven_nights_never_fires() {
        val recent = List(6) { lowNight() }
        assertFalse(
            WearableRecommendationGate.shouldShow(recent, dismissedAtMs = null)
        )
    }

    @Test
    fun seven_low_or_missing_nights_fires() {
        val recent = listOf(
            lowNight(), missingNight(), lowNight(), missingNight(),
            lowNight(), lowNight(), missingNight(),
        )
        assertTrue(
            WearableRecommendationGate.shouldShow(recent, dismissedAtMs = null)
        )
    }

    @Test
    fun a_single_medium_or_better_night_blocks_the_banner() {
        val recent = listOf(
            lowNight(), lowNight(), lowNight(), mediumNight(),
            lowNight(), lowNight(), lowNight(),
        )
        assertFalse(
            WearableRecommendationGate.shouldShow(recent, dismissedAtMs = null)
        )
    }

    @Test
    fun recent_dismissal_suppresses_re_fire() {
        val recent = List(7) { lowNight() }
        val now = 1_716_000_000_000L
        val dismissed = now - 10L * 24L * 60L * 60L * 1000L // 10 days ago
        assertFalse(
            WearableRecommendationGate.shouldShow(
                recentNights = recent,
                dismissedAtMs = dismissed,
                nowMs = now,
            )
        )
    }

    @Test
    fun dismissal_older_than_thirty_days_re_fires() {
        val recent = List(7) { lowNight() }
        val now = 1_716_000_000_000L
        val dismissed = now - 31L * 24L * 60L * 60L * 1000L // 31 days ago
        assertTrue(
            WearableRecommendationGate.shouldShow(
                recentNights = recent,
                dismissedAtMs = dismissed,
                nowMs = now,
            )
        )
    }

    @Test
    fun bin_by_night_preserves_seven_buckets_even_with_empty_history() {
        val binned = WearableRecommendationGate.binByNight(
            readingsNewestFirst = emptyList(),
            localDayKey = { ms -> ms / (24L * 60L * 60L * 1000L) },
            nightsBack = 7,
            nowMs = 7L * 24L * 60L * 60L * 1000L,
        )
        // 7 empty Night buckets — all isLowOrMissing == true.
        assertTrue(binned.size == 7)
        assertTrue(binned.all { it.isLowOrMissing })
    }

    @Test
    fun bin_by_night_routes_readings_to_their_day_bucket() {
        val day = 24L * 60L * 60L * 1000L
        val now = 7L * day + day / 2 // halfway through day 7
        val readings = listOf(
            // Today (day 7) — a single LOW reading
            reading(ConfidenceTier.LOW).copy(timestamp = now - 1_000L),
            // 5 days ago (day 2) — a MEDIUM reading
            reading(ConfidenceTier.MEDIUM).copy(timestamp = now - 5L * day),
        )
        val binned = WearableRecommendationGate.binByNight(
            readingsNewestFirst = readings,
            localDayKey = { ms -> ms / day },
            nightsBack = 7,
            nowMs = now,
        )
        val nonEmpty = binned.filter { it.readings.isNotEmpty() }
        assertTrue(nonEmpty.size == 2)
    }
}
