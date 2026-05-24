package com.bios.app

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.ui.headache.ClusterPeriodicityHistogram
import com.bios.contracts.MetricType
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [ClusterPeriodicityHistogram] (#284). Pins the
 * binning semantics that drive the cluster-periodicity diagnostic
 * view: 24 stable bins (one per hour-of-day in the supplied
 * timezone), non-cluster MetricTypes silently ignored, and the
 * peak-hour helper used by the screen header.
 */
class ClusterPeriodicityHistogramTest {

    private val ny = ZoneId.of("America/New_York")
    private val sourceId = "self-reported"

    private fun reading(
        metricType: MetricType,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        zone: ZoneId = ny,
    ): MetricReading {
        val ts = ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone)
            .toInstant().toEpochMilli()
        return MetricReading(
            metricType = metricType.key,
            value = 1.0,
            timestamp = ts,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
    }

    @Test
    fun empty_input_yields_24_zero_bins() {
        val bins = ClusterPeriodicityHistogram.bin(emptyList(), ny)
        assertEquals(24, bins.size)
        assertEquals((0..23).toList(), bins.map { it.hourOfDay })
        assertTrue(bins.all { it.count == 0 })
    }

    @Test
    fun cluster_events_bin_to_the_correct_local_hour() {
        // Two events at 02:00 local, one at 14:00 local.
        val rows = listOf(
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 1, 2),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 2, 2),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 1, 14),
        )
        val bins = ClusterPeriodicityHistogram.bin(rows, ny)
        assertEquals(2, bins[2].count)
        assertEquals(1, bins[14].count)
        // Every other bin stays at zero.
        bins.filter { it.hourOfDay !in setOf(2, 14) }.forEach { assertEquals(0, it.count) }
    }

    @Test
    fun non_cluster_metric_types_are_silently_ignored() {
        // The caller can hand over an unfiltered windowed fetch — the
        // helper only counts cluster events. A migraine row at 02:00
        // and a heart-rate noise row must not bump the 02:00 bin.
        val rows = listOf(
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 1, 2),
            reading(MetricType.MIGRAINE_ATTACK_EVENT, 2026, 5, 2, 2),
            reading(MetricType.HEART_RATE, 2026, 5, 3, 2),
        )
        val bins = ClusterPeriodicityHistogram.bin(rows, ny)
        assertEquals(1, bins[2].count)
    }

    @Test
    fun timezone_is_applied_at_binning_time() {
        // Same UTC instant lands in different hour-of-day bins depending
        // on the supplied zone. A 06:00 UTC event reads as 02:00 EDT
        // (UTC-4) — pin that semantic so the diagnostic timeline reads
        // the owner's wall clock, not server time.
        val utcRow = reading(
            MetricType.CLUSTER_HEADACHE_ATTACK_EVENT,
            2026, 5, 1, 6,
            zone = ZoneId.of("UTC"),
        )
        val binsNy = ClusterPeriodicityHistogram.bin(listOf(utcRow), ny)
        // EDT = UTC-4 in May → 06:00 UTC = 02:00 EDT.
        assertEquals(1, binsNy[2].count)
        assertEquals(0, binsNy[6].count)
    }

    @Test
    fun totalEvents_sums_every_bin() {
        val rows = listOf(
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 1, 2),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 2, 2),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 3, 14),
        )
        val bins = ClusterPeriodicityHistogram.bin(rows, ny)
        assertEquals(3, ClusterPeriodicityHistogram.totalEvents(bins))
    }

    @Test
    fun peakHour_returns_the_most_populated_bin() {
        val rows = listOf(
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 1, 2),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 2, 2),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 3, 2),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 5, 1, 14),
        )
        val bins = ClusterPeriodicityHistogram.bin(rows, ny)
        val peak = ClusterPeriodicityHistogram.peakHour(bins)
        assertEquals(ClusterPeriodicityHistogram.HourBin(2, 3), peak)
    }

    @Test
    fun peakHour_returns_null_on_empty_histogram() {
        val bins = ClusterPeriodicityHistogram.bin(emptyList(), ny)
        assertNull(ClusterPeriodicityHistogram.peakHour(bins))
    }
}
