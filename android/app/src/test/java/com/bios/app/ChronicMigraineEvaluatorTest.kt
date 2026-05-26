package com.bios.app

import com.bios.app.alerts.ChronicMigraineEvaluator
import com.bios.app.alerts.HeadachePatterns
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [ChronicMigraineEvaluator] (#283 Cut 3,
 * IHS ICHD-3 §1.3). Pins:
 *  - The "any headache" + "migraine-only" day-counting semantics
 *    (distinct dates per month, cluster + tension contribute to
 *    headache-days but not to migraine-days)
 *  - The all-three-months-must-meet rule
 *  - The boundary cases the headache-medicine literature cares about
 *    (exact threshold = meets, one short = fails)
 */
class ChronicMigraineEvaluatorTest {

    private val zone = ZoneId.of("America/New_York")
    private val sourceId = "self-reported"

    /** Build "now" at a deterministic instant inside a known month so
     *  the "recent 3 calendar months" window is stable across test
     *  runs. February 15 sits comfortably inside a month so the day-15
     *  threshold can be hit without wrapping. */
    private val now: Long = ZonedDateTime.of(
        2026, 2, 15, 12, 0, 0, 0, zone,
    ).toInstant().toEpochMilli()

    private fun reading(
        metricType: MetricType,
        year: Int,
        month: Int,
        day: Int,
    ): MetricReading {
        val ts = ZonedDateTime.of(year, month, day, 10, 0, 0, 0, zone)
            .toInstant().toEpochMilli()
        return MetricReading(
            metricType = metricType.key,
            value = 5.0,
            timestamp = ts,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
    }

    @Test
    fun no_readings_yields_empty_per_month_counts_at_threshold_false() {
        val verdict = ChronicMigraineEvaluator.evaluate(emptyList(), now, zone)
        assertFalse(verdict.meetsScreeningThreshold)
        // Three months reported with zero counts so the renderer has
        // substrate to show.
        assertEquals(3, verdict.perMonthCounts.size)
        assertTrue(verdict.perMonthCounts.all { it.headacheDays == 0 && it.migraineDays == 0 })
    }

    @Test
    fun multiple_events_on_the_same_day_count_as_one_day() {
        // Two migraine entries + one tension on Feb 10 → one headache
        // day + one migraine day for February.
        val rows = listOf(
            reading(MetricType.MIGRAINE_ATTACK_EVENT, 2026, 2, 10),
            reading(MetricType.MIGRAINE_ATTACK_EVENT, 2026, 2, 10),
            reading(MetricType.HEADACHE_ATTACK_EVENT, 2026, 2, 10),
        )
        val v = ChronicMigraineEvaluator.evaluate(rows, now, zone)
        val feb = v.perMonthCounts.first { it.year == 2026 && it.month == 2 }
        assertEquals(1, feb.headacheDays)
        assertEquals(1, feb.migraineDays)
    }

    @Test
    fun cluster_and_tension_count_as_headache_but_not_as_migraine() {
        val rows = listOf(
            reading(MetricType.HEADACHE_ATTACK_EVENT, 2026, 2, 1),
            reading(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT, 2026, 2, 2),
        )
        val v = ChronicMigraineEvaluator.evaluate(rows, now, zone)
        val feb = v.perMonthCounts.first { it.year == 2026 && it.month == 2 }
        assertEquals(2, feb.headacheDays)
        assertEquals(0, feb.migraineDays)
    }

    @Test
    fun unrelated_metric_types_are_ignored() {
        // A noise row (SLEEP_DURATION etc.) inside the window must not
        // poison either count.
        val rows = listOf(
            reading(MetricType.MIGRAINE_ATTACK_EVENT, 2026, 2, 5),
            reading(MetricType.SLEEP_DURATION, 2026, 2, 5),
            reading(MetricType.HEART_RATE, 2026, 2, 6),
        )
        val v = ChronicMigraineEvaluator.evaluate(rows, now, zone)
        val feb = v.perMonthCounts.first { it.year == 2026 && it.month == 2 }
        assertEquals(1, feb.headacheDays)
        assertEquals(1, feb.migraineDays)
    }

    @Test
    fun verdict_is_true_when_all_three_months_clear_both_thresholds() {
        // Build a synthetic 3-month diary that's exactly at threshold:
        // 15 headache days/month, 8 of which are migraine. Pattern must
        // fire.
        val rows = mutableListOf<MetricReading>()
        for (monthsBack in 0..2) {
            val zdtTarget = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
                .minusMonths(monthsBack.toLong())
            val y = zdtTarget.year
            val m = zdtTarget.monthValue
            // 8 migraine days on days 1-8, 7 headache days on days 9-15.
            for (d in 1..8) rows += reading(MetricType.MIGRAINE_ATTACK_EVENT, y, m, d)
            for (d in 9..15) rows += reading(MetricType.HEADACHE_ATTACK_EVENT, y, m, d)
        }
        val v = ChronicMigraineEvaluator.evaluate(rows, now, zone)
        assertTrue("expected threshold met, got $v", v.meetsScreeningThreshold)
        assertTrue(v.perMonthCounts.all { it.headacheDays == 15 && it.migraineDays == 8 })
    }

    @Test
    fun verdict_is_false_when_one_month_falls_short_on_headache_days() {
        // Two months at threshold, one month with only 14 headache days
        // → fail. The chronic-migraine pattern requires ALL three.
        val rows = mutableListOf<MetricReading>()
        for (monthsBack in 0..2) {
            val zdtTarget = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
                .minusMonths(monthsBack.toLong())
            val y = zdtTarget.year
            val m = zdtTarget.monthValue
            val headacheTarget = if (monthsBack == 1) 14 else 15
            for (d in 1..8) rows += reading(MetricType.MIGRAINE_ATTACK_EVENT, y, m, d)
            for (d in 9..(headacheTarget)) {
                rows += reading(MetricType.HEADACHE_ATTACK_EVENT, y, m, d)
            }
        }
        val v = ChronicMigraineEvaluator.evaluate(rows, now, zone)
        assertFalse("expected threshold not met, got $v", v.meetsScreeningThreshold)
    }

    @Test
    fun verdict_is_false_when_one_month_falls_short_on_migraine_days() {
        // Each month has 15 headache days but only 7 migraine days in
        // one of them → fail (need 8 migraine days/month).
        val rows = mutableListOf<MetricReading>()
        for (monthsBack in 0..2) {
            val zdtTarget = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
                .minusMonths(monthsBack.toLong())
            val y = zdtTarget.year
            val m = zdtTarget.monthValue
            val migraineTarget = if (monthsBack == 2) 7 else 8
            for (d in 1..migraineTarget) {
                rows += reading(MetricType.MIGRAINE_ATTACK_EVENT, y, m, d)
            }
            for (d in (migraineTarget + 1)..15) {
                rows += reading(MetricType.HEADACHE_ATTACK_EVENT, y, m, d)
            }
        }
        val v = ChronicMigraineEvaluator.evaluate(rows, now, zone)
        assertFalse(v.meetsScreeningThreshold)
    }

    @Test
    fun describeVerdict_surfaces_each_month_with_both_counts() {
        val verdict = ChronicMigraineEvaluator.Verdict(
            meetsScreeningThreshold = true,
            perMonthCounts = listOf(
                ChronicMigraineEvaluator.MonthCount(2026, 2, 17, 9),
                ChronicMigraineEvaluator.MonthCount(2026, 1, 18, 10),
                ChronicMigraineEvaluator.MonthCount(2025, 12, 20, 12),
            ),
        )
        val text = ChronicMigraineEvaluator.describeVerdict(verdict)
        assertTrue("Feb count missing: $text", text.contains("17 headache days (9 migraine)"))
        assertTrue(text.contains("Jan"))
        assertTrue(text.contains("Dec"))
        assertTrue(
            "threshold reference missing: $text",
            text.contains(
                "${HeadachePatterns.CHRONIC_MIGRAINE_HEADACHE_DAYS_PER_MONTH_THRESHOLD} headache days/month"
            ),
        )
    }
}
