package com.bios.app.ui.trends

import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Calendar buckets for cumulative-daily metrics (steps, active calories,
 * active minutes). Pure functions: the DAO groups readings with SQLite
 * `strftime(fmt, ts/1000, 'unixepoch', 'localtime')`, and everything here
 * works on the string keys that query returns, so a DST day (23 h or 25 h)
 * is still one bucket and nothing divides epoch milliseconds by 86 400 000.
 * Spec: docs/specs/day-totals.md.
 */
enum class BucketUnit(val strftime: String) {
    /** Key `2026-10-13T14`. */
    HOUR("%Y-%m-%dT%H"),
    /** Key `2026-10-13`, same text as [LocalDate.toString]. */
    DAY("%Y-%m-%d"),
    /** Key `2026-10`, same text as [YearMonth.toString]. */
    MONTH("%Y-%m"),
}

/** What the parent fetched for the chart; the composable only draws it. */
internal data class DayTotalsChartState(
    val totals: List<DayTotal>,
    val unit: BucketUnit,
    val loaded: Boolean,
    val selectedWindow: MetricChartTimeWindow,
    val selectedIndex: Int,
)

/** One bar. [hasReadings] is false for calendar slots the query returned nothing for. */
data class DayTotal(val key: String, val total: Double, val hasReadings: Boolean)

/** The window chips offered for cumulative metrics; Hour is not one of them (spec criterion 2). */
val dayTotalWindows: List<MetricChartTimeWindow> = listOf(
    MetricChartTimeWindow.DAY,
    MetricChartTimeWindow.WEEK,
    MetricChartTimeWindow.MONTH,
    MetricChartTimeWindow.YEAR,
    MetricChartTimeWindow.ALL,
)

fun bucketUnitFor(window: MetricChartTimeWindow): BucketUnit = when (window) {
    MetricChartTimeWindow.HOUR, MetricChartTimeWindow.DAY -> BucketUnit.HOUR
    MetricChartTimeWindow.WEEK, MetricChartTimeWindow.MONTH -> BucketUnit.DAY
    MetricChartTimeWindow.YEAR, MetricChartTimeWindow.ALL -> BucketUnit.MONTH
}

private const val MONTHS_IN_ALL = 24

private fun localToday(now: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

/**
 * Every calendar slot the window covers, oldest first, ending at the
 * current hour's day / today / this month. Slots with no readings still
 * get a key so the x axis stays a calendar (spec criterion 3).
 */
fun bucketKeys(window: MetricChartTimeWindow, now: Long, zone: ZoneId): List<String> {
    val today = localToday(now, zone)
    return when (window) {
        MetricChartTimeWindow.HOUR, MetricChartTimeWindow.DAY ->
            (0..23).map { h -> "${today}T${"%02d".format(h)}" }
        MetricChartTimeWindow.WEEK ->
            (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
        MetricChartTimeWindow.MONTH ->
            (29 downTo 0).map { today.minusDays(it.toLong()).toString() }
        MetricChartTimeWindow.YEAR ->
            (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()).toString() }
        MetricChartTimeWindow.ALL ->
            (MONTHS_IN_ALL - 1 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()).toString() }
    }
}

/** Epoch millis of the first slot's start in [zone]; the DAO's lower bound. */
fun calendarStartMillis(window: MetricChartTimeWindow, now: Long, zone: ZoneId): Long {
    val today = localToday(now, zone)
    val start: LocalDate = when (window) {
        MetricChartTimeWindow.HOUR, MetricChartTimeWindow.DAY -> today
        MetricChartTimeWindow.WEEK -> today.minusDays(6)
        MetricChartTimeWindow.MONTH -> today.minusDays(29)
        MetricChartTimeWindow.YEAR -> YearMonth.from(today).minusMonths(11).atDay(1)
        MetricChartTimeWindow.ALL -> YearMonth.from(today).minusMonths((MONTHS_IN_ALL - 1).toLong()).atDay(1)
    }
    return start.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Zero-fills [keys] from the DAO's grouped totals; keys the query did not return are empty bars. */
fun fillBuckets(keys: List<String>, totals: Map<String, Double>): List<DayTotal> =
    keys.map { key ->
        val t = totals[key]
        DayTotal(key = key, total = t ?: 0.0, hasReadings = t != null)
    }

/**
 * Owner-facing name of a bucket: "Today", "Yesterday", "Sat 10 Oct";
 * hours "14:00"; months "Oct 2026" (spec criterion 4).
 */
fun bucketLabel(
    key: String,
    unit: BucketUnit,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): String = when (unit) {
    BucketUnit.HOUR -> key.substringAfter('T') + ":00"
    BucketUnit.DAY -> {
        val day = LocalDate.parse(key)
        when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        }
    }
    BucketUnit.MONTH -> YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
}

/** Short tick under a bar: weekday initial for days, hour for hours, month initial for months. */
fun bucketTick(key: String, unit: BucketUnit, locale: Locale = Locale.getDefault()): String = when (unit) {
    BucketUnit.HOUR -> key.substringAfter('T').trimStart('0').ifEmpty { "0" }
    BucketUnit.DAY -> LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEEEE", locale))
    BucketUnit.MONTH -> YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMMMM", locale))
}

/**
 * "2,844 steps", "610 kcal", "1h 12m": thousands separated in [locale];
 * duration units keep the Trends "Xh Ym" rendering.
 */
fun formatTotal(value: Double, metric: MetricType, locale: Locale = Locale.getDefault()): String {
    if (metric.unit == MetricUnit.SECONDS) return formatMetricValue(value, metric.unit)
    val number = NumberFormat.getIntegerInstance(locale).format(value.roundToLong())
    val suffix = metric.unit.symbol.ifEmpty { metric.readableName.lowercase(locale) }
    return "$number $suffix"
}
