package com.bios.app.data

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.ReadingKind
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType

/**
 * Persistence path for self-reported lab values entered through the
 * biomarker entry screen.
 *
 * Self-reported readings flow through a single SELF_REPORTED [DataSource]
 * tagged with [ReadingKind.SELF_REPORTED] so the baseline engine and anomaly
 * detector keep ignoring them (decision 3 in docs/SELF_REPORTED_DATA_HOME.md).
 * They still show up in trends, FHIR export, and condition-pattern displays.
 */
class BiomarkerEntryRepo(private val db: BiosDatabase) {

    suspend fun add(metricType: MetricType, value: Double, timestamp: Long) {
        require(metricType.domain == MetricDomain.BIOMARKER) {
            "BiomarkerEntryRepo only accepts BIOMARKER metrics; got ${metricType.key}"
        }
        val sourceId = getOrCreateSelfReportedSource()
        db.metricReadingDao().insert(
            MetricReading(
                metricType = metricType.key,
                value = value,
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        )
    }

    suspend fun fetchRecent(limit: Int = 20): List<MetricReading> {
        val dao = db.metricReadingDao()
        return MetricType.entries
            .filter { it.domain == MetricDomain.BIOMARKER }
            .flatMap { dao.fetchLatest(it.key, limit) }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

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
