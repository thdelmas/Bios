package com.bios.app.ui

import com.bios.app.data.CountTotals
import com.bios.app.data.ResolvedBucket
import com.bios.contracts.MetricType
import java.time.LocalDate
import java.time.ZoneId

/**
 * One calendar day's total for a cumulative metric, [daysAgo] days before
 * today in the device zone (0 = today, 1 = yesterday), counted by one
 * source per the owner's order (docs/specs/source-preference.md). Null
 * when no source has readings that day. Lives beside [AppViewModel]
 * because that file sits at its 500-line cap.
 */
suspend fun AppViewModel.getDayTotal(metricType: MetricType, daysAgo: Int): ResolvedBucket? {
    val zone = ZoneId.systemDefault()
    val day = LocalDate.now(zone).minusDays(daysAgo.toLong())
    return CountTotals(db, getApplication()).dayTotal(metricType, day, zone)
}

suspend fun AppViewModel.getDaySum(metricType: MetricType, daysAgo: Int): Double? =
    getDayTotal(metricType, daysAgo)?.total
