package com.bios.app.data

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.app.model.SeizureEventFields
import com.bios.contracts.MetricType
import java.util.UUID

/**
 * Persistence path for owner-logged seizure events (#269 follow-up).
 *
 * Writes a SEIZURE_EVENT [MetricReading] with the validated `durationSec`
 * so [com.bios.app.alerts.NeurologyUrgentPatterns.statusEpilepticusConvulsive]
 * (ILAE 2015 t1 = 5 min) can finally fire on owner-logged input.
 * Prior to this surface, `SEIZURE_EVENT` was `allowsManualEntry = true`
 * but the generic clinical-reading entry path didn't capture
 * `durationSec`, so the URGENT pattern's `durationAtLeastSec = 300`
 * filter could never match any owner-logged row.
 *
 * Each row is tagged with the shared SELF_REPORTED [com.bios.app.model.DataSource]
 * + [com.bios.app.model.ReadingKind.SELF_REPORTED] so the baseline
 * engine ignores it while it still surfaces in the seizure timeline,
 * the URGENT / cluster patterns, and the FHIR exporter.
 *
 * Each row also carries an explicit
 * `detection_source = "owner_logged"` field in the event_payloads
 * sidecar. This is defensive: it lets the safety gate from #287 see
 * unambiguously that the row is owner-asserted (the gate excludes
 * `"wearable_inferred"` rows; "owner_logged" passes), and it lets
 * future analytics distinguish rows written via this entry surface
 * from any historical bare-row seizure entries.
 */
class SeizureEntryRepo(private val db: BiosDatabase) {

    /**
     * Persist an owner-logged seizure event. Callers should validate
     * inputs via [com.bios.app.ui.seizure.SeizureEntryValidator] before
     * calling; this repo accepts already-validated values.
     */
    suspend fun add(timestampMs: Long, durationSec: Int, notes: String? = null) {
        val sourceId = resolveSelfReportedSource(db)
        val readingId = UUID.randomUUID().toString()
        val reading = MetricReading(
            id = readingId,
            metricType = MetricType.SEIZURE_EVENT.key,
            value = 1.0,
            timestamp = timestampMs,
            durationSec = durationSec,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
        val payload = buildList {
            add(
                EventPayloadField(
                    readingId = readingId,
                    fieldKey = SeizureEventFields.DETECTION_SOURCE,
                    stringValue = SeizureEventFields.DETECTION_SOURCE_OWNER_LOGGED,
                )
            )
            notes?.takeIf { it.isNotBlank() }?.let {
                add(
                    EventPayloadField(
                        readingId = readingId,
                        fieldKey = NOTES_FIELD_KEY,
                        stringValue = it,
                    )
                )
            }
        }
        db.metricReadingDao().insert(reading)
        db.eventPayloadFieldDao().insertAll(payload)
    }

    companion object {
        /** Field key for the optional free-text note. Not in
         *  [SeizureEventFields] because that file is the cross-module
         *  contract for fields the engine/detector both read; the
         *  notes field is owner-facing only and lives with the entry
         *  surface that writes it. */
        const val NOTES_FIELD_KEY = "owner_note"
    }
}
