package com.bios.app.alerts

import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure-Kotlin chronic migraine evaluator (#283 Cut 3, IHS ICHD-3 §1.3).
 *
 * Reads from the cross-cutting `metric_readings` view written by
 * [com.bios.app.data.HeadacheEventWriter] (#283 Cut 2). Each entry
 * the owner records via the migraine / headache diary lands as one
 * of three MetricType rows:
 *  - [MetricType.MIGRAINE_ATTACK_EVENT]
 *  - [MetricType.HEADACHE_ATTACK_EVENT]
 *  - [MetricType.CLUSTER_HEADACHE_ATTACK_EVENT]
 *
 * The evaluator counts, per calendar month in the supplied timezone:
 *  - **headache_days** — distinct calendar dates with **any** of the
 *    three event types. Cluster headache counts as a headache for the
 *    chronic-migraine threshold even though it's a distinct entity —
 *    the IHS criterion is "headache days," not "migraine-class
 *    headache days."
 *  - **migraine_days** — distinct calendar dates with at least one
 *    [MetricType.MIGRAINE_ATTACK_EVENT]. Tension and cluster rows do
 *    not contribute to this count even if they occurred on the same
 *    date.
 *
 * The verdict ([Verdict.meetsScreeningThreshold]) is `true` iff
 * **every** one of the most-recent
 * [HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED] calendar months
 * (3) has `headache_days ≥ 15` AND `migraine_days ≥ 8`.
 *
 * Same shape as [MedicationOveruseHeadacheEvaluator] so the
 * [ChronicMigraineWorker] dedup logic mirrors [MohScreeningWorker]
 * one-to-one.
 */
object ChronicMigraineEvaluator {

    data class Verdict(
        val meetsScreeningThreshold: Boolean,
        /** Per-calendar-month breakdown, newest first. */
        val perMonthCounts: List<MonthCount>,
    )

    /** Per-calendar-month day counts. [headacheDays] is the distinct-date
     *  count for any headache event type; [migraineDays] is the subset
     *  with at least one MIGRAINE_ATTACK_EVENT. */
    data class MonthCount(
        val year: Int,
        val month: Int,
        val headacheDays: Int,
        val migraineDays: Int,
    )

    /**
     * Evaluate the chronic-migraine screen across the supplied
     * headache-event readings.
     *
     * @param readings the headache-event rows from the rolling window;
     *   the evaluator filters to the three relevant MetricTypes
     *   internally so callers can pass an unfiltered window without
     *   pre-classifying
     * @param nowMillis "now" for picking the most-recent 3 calendar months
     * @param zoneId timezone for calendar-month boundaries
     */
    fun evaluate(
        readings: List<MetricReading>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Verdict {
        val migraineKey = MetricType.MIGRAINE_ATTACK_EVENT.key
        val headacheKey = MetricType.HEADACHE_ATTACK_EVENT.key
        val clusterKey = MetricType.CLUSTER_HEADACHE_ATTACK_EVENT.key

        val anyHeadacheDates = mutableSetOf<EventDate>()
        val migraineDates = mutableSetOf<EventDate>()

        for (reading in readings) {
            val key = reading.metricType
            if (key != migraineKey && key != headacheKey && key != clusterKey) continue
            val date = eventDate(reading.timestamp, zoneId)
            anyHeadacheDates += date
            if (key == migraineKey) migraineDates += date
        }

        val targetMonths = recentCalendarMonths(
            nowMillis = nowMillis,
            zoneId = zoneId,
            count = HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED,
        )

        val perMonthCounts = targetMonths.map { (year, month) ->
            MonthCount(
                year = year,
                month = month,
                headacheDays = anyHeadacheDates.count { it.year == year && it.month == month },
                migraineDays = migraineDates.count { it.year == year && it.month == month },
            )
        }

        val headacheThreshold = HeadachePatterns.CHRONIC_MIGRAINE_HEADACHE_DAYS_PER_MONTH_THRESHOLD
        val migraineThreshold = HeadachePatterns.CHRONIC_MIGRAINE_MIGRAINE_DAYS_PER_MONTH_THRESHOLD
        val meets = perMonthCounts.size == HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED &&
            perMonthCounts.all {
                it.headacheDays >= headacheThreshold && it.migraineDays >= migraineThreshold
            }

        return Verdict(meetsScreeningThreshold = meets, perMonthCounts = perMonthCounts)
    }

    /**
     * Build a manifesto-clean alert message body summarising the per-
     * month headache + migraine day counts. Returned text obeys
     * [AlertContentPolicy] — data statements only, no second-person
     * judgments.
     */
    fun describeVerdict(verdict: Verdict): String {
        if (verdict.perMonthCounts.isEmpty()) return ""
        val monthLine = verdict.perMonthCounts.joinToString("; ") { mc ->
            "${monthLabel(mc.year, mc.month)}: ${mc.headacheDays} headache days (${mc.migraineDays} migraine)"
        }
        return "Per-month headache + migraine day counts (most recent first): $monthLine. " +
            "IHS ICHD-3 §1.3 chronic-migraine threshold is " +
            "${HeadachePatterns.CHRONIC_MIGRAINE_HEADACHE_DAYS_PER_MONTH_THRESHOLD} headache days/month " +
            "with ${HeadachePatterns.CHRONIC_MIGRAINE_MIGRAINE_DAYS_PER_MONTH_THRESHOLD}+ migraine, " +
            "across ${HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED} consecutive months."
    }

    private data class EventDate(val year: Int, val month: Int, val day: Int)

    private fun eventDate(epochMillis: Long, zoneId: ZoneId): EventDate {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId)
        return EventDate(zdt.year, zdt.monthValue, zdt.dayOfMonth)
    }

    private fun recentCalendarMonths(
        nowMillis: Long,
        zoneId: ZoneId,
        count: Int,
    ): List<Pair<Int, Int>> {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return (0 until count).map { offset ->
            val month = now.minusMonths(offset.toLong())
            month.year to month.monthValue
        }
    }

    private fun monthLabel(year: Int, month: Int): String {
        val mm = java.time.Month.of(month).getDisplayName(
            java.time.format.TextStyle.SHORT,
            java.util.Locale.US,
        )
        return "$mm $year"
    }
}
