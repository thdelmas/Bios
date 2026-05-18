package com.bios.app.data

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.EventPayloadField
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
 *
 * Optional [BiomarkerContext] carries lab provenance — engine-relevant
 * fields (lab, fasting, specimen, source URI) ride the existing
 * `event_payloads` sidecar; the free-text owner-recall note rides the
 * `metric_readings.note` column directly. Engines never read either.
 */
class BiomarkerEntryRepo(private val db: BiosDatabase) {

    suspend fun add(
        metricType: MetricType,
        value: Double,
        timestamp: Long,
        context: BiomarkerContext = BiomarkerContext()
    ): String {
        require(metricType.domain == MetricDomain.BIOMARKER) {
            "BiomarkerEntryRepo only accepts BIOMARKER metrics; got ${metricType.key}"
        }
        val sourceId = getOrCreateSelfReportedSource()
        val reading = MetricReading(
            metricType = metricType.key,
            value = value,
            timestamp = timestamp,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
            note = context.note?.takeIf { it.isNotBlank() }
        )
        db.metricReadingDao().insert(reading)
        persistPayload(reading.id, context)
        return reading.id
    }

    suspend fun fetchRecent(limit: Int = 20): List<MetricReading> {
        val dao = db.metricReadingDao()
        return MetricType.entries
            .filter { it.domain == MetricDomain.BIOMARKER }
            .flatMap { dao.fetchLatest(it.key, limit) }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    suspend fun fetchContext(readingId: String): BiomarkerContext {
        val rows = db.eventPayloadFieldDao().fetchForReading(readingId)
        return rowsToContext(rows, note = null)
    }

    private suspend fun persistPayload(readingId: String, context: BiomarkerContext) {
        val fields = buildList {
            context.labName?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, BiomarkerPayloadKeys.LAB_NAME, stringValue = it))
            }
            context.fasting?.let {
                add(EventPayloadField(readingId, BiomarkerPayloadKeys.FASTING, longValue = if (it) 1L else 0L))
            }
            context.specimen?.let {
                add(EventPayloadField(readingId, BiomarkerPayloadKeys.SPECIMEN, stringValue = it.name))
            }
            context.sourceUri?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, BiomarkerPayloadKeys.SOURCE_URI, stringValue = it))
            }
        }
        if (fields.isNotEmpty()) {
            db.eventPayloadFieldDao().insertAll(fields)
        }
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

    companion object {
        internal fun rowsToContext(rows: List<EventPayloadField>, note: String?): BiomarkerContext {
            val byKey = rows.associateBy { it.fieldKey }
            return BiomarkerContext(
                labName = byKey[BiomarkerPayloadKeys.LAB_NAME]?.stringValue,
                fasting = byKey[BiomarkerPayloadKeys.FASTING]?.longValue?.let { it == 1L },
                specimen = byKey[BiomarkerPayloadKeys.SPECIMEN]?.stringValue
                    ?.let { com.bios.app.model.Specimen.fromName(it) },
                sourceUri = byKey[BiomarkerPayloadKeys.SOURCE_URI]?.stringValue,
                note = note
            )
        }
    }
}
