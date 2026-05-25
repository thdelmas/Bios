package com.bios.app.ui

import com.bios.app.data.ManualReadingContext
import com.bios.app.data.ManualReadingRepo
import com.bios.app.engine.BaselineEngine
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Recent-entries / per-reading-delete / per-metric-coverage slice of
 * [AppViewModel]. Kept here so the main view-model file stays under the
 * 500-line cap. Same pattern as [AppViewModelBiomarkers].
 *
 * Engine isolation still holds — `deleteReading` cascades to
 * `event_payloads` but does NOT cascade to structured-entity children
 * (headache / migraine / cluster repos own their own delete paths; see
 * [com.bios.app.data.dao.MetricReadingDao.deleteById] kdoc).
 */
data class RecentEntry(
    val reading: MetricReading,
    val context: ManualReadingContext,
)

suspend fun AppViewModel.getRecentReadings(
    metricType: MetricType,
    limit: Int = 20,
): List<RecentEntry> {
    val readings = db.metricReadingDao().fetchLatest(metricType.key, limit)
    if (readings.isEmpty()) return emptyList()
    val payloads = db.eventPayloadFieldDao().fetchForReadings(readings.map { it.id })
    val byReadingId = payloads.groupBy { it.readingId }
    return readings.map { r ->
        RecentEntry(
            reading = r,
            context = ManualReadingRepo.rowsToContext(
                byReadingId[r.id].orEmpty(), note = r.note,
            ),
        )
    }
}

suspend fun AppViewModel.deleteReading(readingId: String) {
    db.eventPayloadFieldDao().deleteForReading(readingId)
    db.metricReadingDao().deleteById(readingId)
}

suspend fun AppViewModel.deleteReadings(readingIds: Collection<String>) {
    for (id in readingIds) deleteReading(id)
}

/**
 * Recompute the baseline for one metric and refresh the public flow. Used
 * by the entries-history surface after an owner-initiated delete so the
 * dashboard stops showing a baseline that no longer reflects what's on
 * file.
 */
suspend fun AppViewModel.recomputeBaseline(metricType: MetricType) {
    baselineEngine.computeBaseline(metricType)
    refreshBaselines()
}

/**
 * Per-metric coverage on-demand. The prebaked `metricCoverage` flow only
 * covers `TRACKED_METRICS` (HR / HRV / RHR / SpO2 / RR / Steps / Sleep);
 * any metric reached via the global search bar needs this fallback or
 * its dashboard claims "no data yet" even when manual entries exist.
 */
suspend fun AppViewModel.coverageFor(metricType: MetricType): BaselineEngine.Coverage =
    baselineEngine.coverageFor(metricType)
