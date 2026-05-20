package com.bios.app.data

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.util.UUID

/**
 * General manual-reading persistence path for clinical vital signs and
 * other owner-as-source metrics. Sibling of [BiomarkerEntryRepo] sharing
 * the same SELF_REPORTED write path
 * ([resolveSelfReportedSource]) — the split keeps the lab-specific
 * provenance vocabulary ([BiomarkerContext]) separate from the
 * clinical-visit vocabulary ([ManualReadingContext]) without forking the
 * data source.
 *
 * Engine isolation still holds: rows insert through SELF_REPORTED so
 * `BaselineEngine` and `AnomalyDetector` skip them
 * (docs/SELF_REPORTED_DATA_HOME.md decision 3). The owner sees these in
 * trends, FHIR export, and pull-side comparisons; nothing pushes a
 * judgement on a self-reported value.
 *
 * Reproductive metrics (BBT, period onset) are NOT accepted here — their
 * rows live in the isolated [ReproductiveDatabase] via
 * [BbtEntryRepo] / [PeriodEntryRepo] for the encryption-isolation reason
 * documented on those repos. The gate is [MetricType.allowsManualEntry];
 * reproductive entries that pass it (e.g. MENSTRUATION_ONSET) still get
 * routed to their domain repos by the UI layer.
 */
class ManualReadingRepo(private val db: BiosDatabase) {

    suspend fun add(
        metricType: MetricType,
        value: Double,
        timestamp: Long,
        context: ManualReadingContext = ManualReadingContext(),
    ): String {
        require(metricType.allowsManualEntry) {
            "ManualReadingRepo only accepts metrics with allowsManualEntry=true; " +
                "got ${metricType.key}"
        }
        val sourceId = resolveSelfReportedSource(db)
        val reading = MetricReading(
            metricType = metricType.key,
            value = value,
            timestamp = timestamp,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
            note = context.note?.takeIf { it.isNotBlank() },
        )
        db.metricReadingDao().insert(reading)
        persistPayload(reading.id, context)
        return reading.id
    }

    /**
     * Clinical-visit bundle entry. One shared timestamp + clinic + visit
     * note span N readings; each reading carries the same auto-generated
     * [ManualReadingContext.bundleId] so the UI can later regroup them.
     * Mirrors how a triage row gets recorded — BP + SpO2 + FC + Temp + FR +
     * EVA + GCS + Flujo O2 captured as one event.
     */
    suspend fun addBundle(
        timestamp: Long,
        clinicName: String?,
        visitNote: String?,
        sourceUri: String?,
        readings: List<BundleReading>,
    ): String {
        require(readings.isNotEmpty()) { "Clinical-visit bundle must contain at least one reading" }
        val bundleId = UUID.randomUUID().toString()
        val sharedContext = ManualReadingContext(
            clinicName = clinicName?.takeIf { it.isNotBlank() },
            visitNote = visitNote?.takeIf { it.isNotBlank() },
            sourceUri = sourceUri?.takeIf { it.isNotBlank() },
            bundleId = bundleId,
        )
        for (r in readings) {
            val rowContext = sharedContext.copy(
                originalScale = r.originalScale,
                originalValue = r.originalValue,
                note = r.note,
            )
            add(r.metricType, r.value, timestamp, rowContext)
        }
        return bundleId
    }

    suspend fun fetchContext(readingId: String): ManualReadingContext {
        val rows = db.eventPayloadFieldDao().fetchForReading(readingId)
        return rowsToContext(rows, note = null)
    }

    private suspend fun persistPayload(readingId: String, context: ManualReadingContext) {
        val fields = buildList {
            context.clinicName?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, ManualReadingPayloadKeys.CLINIC_NAME, stringValue = it))
            }
            context.visitNote?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, ManualReadingPayloadKeys.VISIT_NOTE, stringValue = it))
            }
            context.sourceUri?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, ManualReadingPayloadKeys.SOURCE_URI, stringValue = it))
            }
            context.bundleId?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, ManualReadingPayloadKeys.BUNDLE_ID, stringValue = it))
            }
            context.originalScale?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, ManualReadingPayloadKeys.ORIGINAL_SCALE, stringValue = it))
            }
            context.originalValue?.takeIf { it.isNotBlank() }?.let {
                add(EventPayloadField(readingId, ManualReadingPayloadKeys.ORIGINAL_VALUE, stringValue = it))
            }
        }
        if (fields.isNotEmpty()) {
            db.eventPayloadFieldDao().insertAll(fields)
        }
    }

    companion object {
        internal fun rowsToContext(rows: List<EventPayloadField>, note: String?): ManualReadingContext {
            val byKey = rows.associateBy { it.fieldKey }
            return ManualReadingContext(
                clinicName = byKey[ManualReadingPayloadKeys.CLINIC_NAME]?.stringValue,
                visitNote = byKey[ManualReadingPayloadKeys.VISIT_NOTE]?.stringValue,
                sourceUri = byKey[ManualReadingPayloadKeys.SOURCE_URI]?.stringValue,
                bundleId = byKey[ManualReadingPayloadKeys.BUNDLE_ID]?.stringValue,
                originalScale = byKey[ManualReadingPayloadKeys.ORIGINAL_SCALE]?.stringValue,
                originalValue = byKey[ManualReadingPayloadKeys.ORIGINAL_VALUE]?.stringValue,
                note = note,
            )
        }
    }
}

/**
 * One reading inside a clinical-visit bundle. [originalScale] /
 * [originalValue] preserve scales like AVPU when the canonical metric is
 * GCS — see [MetricType.CONSCIOUSNESS_LEVEL] for the lossless mapping.
 */
data class BundleReading(
    val metricType: MetricType,
    val value: Double,
    val originalScale: String? = null,
    val originalValue: String? = null,
    val note: String? = null,
)
