package com.bios.app.data

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.ReadingKind
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType

/**
 * Persistence path for manually logged sleep duration when the owner has
 * no wearable / Health Connect feeding the metric (or wants to override a
 * gap on a given night).
 *
 * Like [BiomarkerEntryRepo], rows are tagged with the shared SELF_REPORTED
 * [DataSource] + [ReadingKind.SELF_REPORTED] so the baseline engine ignores
 * them while they still surface in trends, coverage, and FHIR export.
 */
class SleepEntryRepo(private val db: BiosDatabase) {

    /** Physiological cap (24h). Anything above is a data-entry error. */
    private val maxDurationSeconds = 24L * 3600L

    suspend fun add(durationSeconds: Long, timestamp: Long) {
        require(durationSeconds in 1..maxDurationSeconds) {
            "Sleep duration $durationSeconds s is outside the 1..$maxDurationSeconds range"
        }
        val sourceId = getOrCreateSelfReportedSource()
        db.metricReadingDao().insert(
            MetricReading(
                metricType = MetricType.SLEEP_DURATION.key,
                value = durationSeconds.toDouble(),
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        )
    }

    suspend fun fetchRecent(limit: Int = 20): List<MetricReading> =
        db.metricReadingDao().fetchLatest(MetricType.SLEEP_DURATION.key, limit)

    private suspend fun getOrCreateSelfReportedSource(): String {
        val dao = db.dataSourceDao()
        dao.findByType(SourceType.SELF_REPORTED.key)?.let { return it.id }
        val source = DataSource(
            sourceType = SourceType.SELF_REPORTED.key,
            deviceName = "Self-reported",
            sensorType = SensorType.DERIVED.name,
            readingKind = ReadingKind.SELF_REPORTED.name
        )
        dao.insert(source)
        return source.id
    }
}
