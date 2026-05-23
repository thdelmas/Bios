package com.bios.app.engine

import com.bios.app.model.ReadingKind
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit

// EVENT-unit metrics (tobacco_use, fall_event, …) are companion-written
// SELF_REPORTED / DERIVED — CompanionConditionPatterns rules want those
// rows, so the SENSOR-only baseline filter is branched off for them.
// WOMENS_HEALTH metrics live in ReproductiveDatabase and are SELF_REPORTED
// by design — same carve-out.
internal fun readingKindFilterFor(metricType: MetricType): String? = when {
    metricType.unit == MetricUnit.EVENT -> null
    isReproductiveMetric(metricType) -> null
    else -> ReadingKind.SENSOR.name
}

/** Reproductive raw readings live in [com.bios.app.data.ReproductiveDatabase]; baselines stay in the main DB as summary-only rows. */
internal fun isReproductiveMetric(metricType: MetricType): Boolean = metricType.domain == MetricDomain.WOMENS_HEALTH
