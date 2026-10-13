package com.bios.app.ui

import com.bios.contracts.MetricType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sums one calendar day's primary readings, [daysAgo] days before today in
 * the device zone (1 = yesterday). Null when the day has no readings.
 * Home shows it under today's total for cumulative-daily metrics
 * (docs/specs/day-totals.md, criterion 7). Lives beside [AppViewModel]
 * because that file sits at its 500-line cap.
 */
suspend fun AppViewModel.getDaySum(metricType: MetricType, daysAgo: Int): Double? {
    val zone = ZoneId.systemDefault()
    val day = LocalDate.now(zone).minusDays(daysAgo.toLong())
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    val values = db.metricReadingDao().fetchValues(metricType.key, start, end)
    return if (values.isEmpty()) null else values.sum()
}
