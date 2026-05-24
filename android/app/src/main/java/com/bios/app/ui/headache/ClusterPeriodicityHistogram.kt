package com.bios.app.ui.headache

import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure-Kotlin binning helper for the cluster-headache periodicity
 * histogram (#284, NEUROLOGY_POV §2.5 / IHS ICHD-3 §3.1).
 *
 * Cluster headache is one of the few neurological conditions whose
 * **time-of-day distribution is itself diagnostic**: nocturnal-onset
 * clusters firing within a 1–2 h window of the same clock time night
 * after night are the canonical "clockwork" presentation. The
 * pull-side histogram surfaces the substrate directly — no judgment,
 * no alert, no pattern. The owner reads the periodicity themselves
 * (or shares it with a neurologist).
 *
 * Counts onset-hour of every
 * [MetricType.CLUSTER_HEADACHE_ATTACK_EVENT] row in the supplied
 * list, mapping each row's `timestamp` to the local hour-of-day
 * (0–23) in the supplied timezone. The result is a stable 24-bin
 * array — bins with no events still appear (with count 0) so the
 * renderer can show the canonical "clockwork" gaps that distinguish
 * cluster from non-cluster headache.
 *
 * Other MetricType rows passed in are silently ignored so the
 * caller can hand over a generic windowed fetch without
 * pre-classifying.
 */
internal object ClusterPeriodicityHistogram {

    /** One bin of the histogram. [hourOfDay] is 0..23, [count] is the
     *  number of cluster-event onsets that fell in that hour. */
    data class HourBin(val hourOfDay: Int, val count: Int)

    /**
     * Bin the cluster-event onsets by hour-of-day in the supplied
     * timezone. Returns a 24-element list ordered 0..23 (midnight
     * first), suitable for direct rendering as a horizontal bar
     * chart.
     */
    fun bin(
        readings: List<MetricReading>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<HourBin> {
        val counts = IntArray(HOURS_IN_DAY)
        val clusterKey = MetricType.CLUSTER_HEADACHE_ATTACK_EVENT.key
        for (reading in readings) {
            if (reading.metricType != clusterKey) continue
            val hour = ZonedDateTime
                .ofInstant(Instant.ofEpochMilli(reading.timestamp), zoneId)
                .hour
            counts[hour] += 1
        }
        return (0 until HOURS_IN_DAY).map { HourBin(hourOfDay = it, count = counts[it]) }
    }

    /** Total cluster-event count across the binned rows (i.e. the
     *  histogram's sum). Convenient for the screen header. */
    fun totalEvents(bins: List<HourBin>): Int = bins.sumOf { it.count }

    /** The peak hour-of-day and its count, or null when the histogram
     *  is empty. Surfaced separately so the renderer can highlight
     *  the clockwork-onset hour the diagnostic literature describes. */
    fun peakHour(bins: List<HourBin>): HourBin? =
        bins.maxByOrNull { it.count }?.takeIf { it.count > 0 }

    private const val HOURS_IN_DAY: Int = 24
}
