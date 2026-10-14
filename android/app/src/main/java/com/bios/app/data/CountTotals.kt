package com.bios.app.data

import android.content.Context
import com.bios.app.ui.trends.MetricChartTimeWindow
import com.bios.app.ui.trends.BucketUnit
import com.bios.app.ui.trends.calendarStartMillis
import com.bios.contracts.MetricType
import java.time.LocalDate
import java.time.ZoneId

/** One grouped row from the DAO, with its source already turned into an order key. */
data class SourceBucketRow(val bucket: String, val sourceKey: String, val total: Double)

/**
 * A bucket's total as one witness counted it. [winnerKey] is the source key
 * that owned the day; null for a month, which is a sum of day winners
 * (docs/specs/source-preference.md, criterion 5).
 */
data class ResolvedBucket(val key: String, val total: Double, val winnerKey: String?)

/** Pure winner selection: one source per calendar day, by the owner's order. */
object CountWinners {

    /** `2026-10-14T11` and `2026-10-14` both belong to `2026-10-14`. */
    fun dayOf(bucket: String): String = bucket.substringBefore('T')

    /**
     * For every day present in [rows], the first key in [order] that has a
     * row that day wins; buckets of other keys that day are dropped. Hour
     * buckets are kept per hour, so a Day window still sums to the day.
     */
    fun resolve(rows: List<SourceBucketRow>, order: List<String>): Map<String, ResolvedBucket> {
        val winnerByDay = rows.groupBy { dayOf(it.bucket) }.mapValues { (_, dayRows) ->
            dayRows.map { it.sourceKey }.distinct().minByOrNull { CountSourceOrder.rank(order, it) }!!
        }
        return rows
            .filter { winnerByDay[dayOf(it.bucket)] == it.sourceKey }
            .groupBy { it.bucket }
            .mapValues { (bucket, same) ->
                ResolvedBucket(bucket, same.sumOf { it.total }, same.first().sourceKey)
            }
    }

    /** Month buckets (`2026-10`) as sums of per-day winners, never a winner per month. */
    fun rollUpToMonths(days: Map<String, ResolvedBucket>): Map<String, ResolvedBucket> =
        days.values.groupBy { it.key.substring(0, MONTH_KEY_LENGTH) }
            .mapValues { (month, list) -> ResolvedBucket(month, list.sumOf { it.total }, winnerKey = null) }

    private const val MONTH_KEY_LENGTH = 7
}

/**
 * The one function every day total goes through: Trends bars, the Home
 * tile, the daily digest and the provider's day summary (criterion 6).
 * Reads every source's grouped sums, maps each source to its order key,
 * and lets [CountWinners] pick one witness per day.
 */
class CountTotals(private val db: BiosDatabase, private val context: Context) {

    private suspend fun orderAndKeys(): Pair<List<String>, Map<String, String>> {
        val sources = db.dataSourceDao().getAll()
        val keyBySourceId = sources.associate { it.id to CountSourceOrder.keyOf(it) }
        val order = CountSourceOrder.effectiveOrder(CountSourceOrder.load(context), keyBySourceId.values.toSet())
        return order to keyBySourceId
    }

    private suspend fun rows(
        metric: MetricType, start: Long, end: Long, unit: BucketUnit,
    ): Pair<List<SourceBucketRow>, List<String>> {
        val (order, keyBySourceId) = orderAndKeys()
        val grouped = db.metricReadingDao().fetchBucketTotalsBySource(metric.key, start, end, unit.strftime)
        val rows = grouped.map { SourceBucketRow(it.bucket, keyBySourceId[it.sourceId] ?: it.sourceId, it.total) }
        return rows to order
    }

    /** Buckets for a Trends window, keyed like [com.bios.app.ui.trends.bucketKeys]. */
    suspend fun buckets(metric: MetricType, window: MetricChartTimeWindow, now: Long, zone: ZoneId): Map<String, ResolvedBucket> {
        val start = calendarStartMillis(window, now, zone)
        val hourly = window == MetricChartTimeWindow.DAY || window == MetricChartTimeWindow.HOUR
        val unit = if (hourly) BucketUnit.HOUR else BucketUnit.DAY
        val (rows, order) = rows(metric, start, now, unit)
        val resolved = CountWinners.resolve(rows, order)
        val monthly = window == MetricChartTimeWindow.YEAR || window == MetricChartTimeWindow.ALL
        return if (monthly) CountWinners.rollUpToMonths(resolved) else resolved
    }

    /** One calendar day's total, or null when no source has readings that day. */
    suspend fun dayTotal(metric: MetricType, day: LocalDate, zone: ZoneId): ResolvedBucket? {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val (rows, order) = rows(metric, start, end, BucketUnit.DAY)
        return CountWinners.resolve(rows, order)[day.toString()]
    }
}
