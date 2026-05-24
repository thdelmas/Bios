package com.bios.app.data

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.HeadacheEventFields
import com.bios.app.model.HeadacheLog
import com.bios.app.model.HeadacheType
import com.bios.app.model.MetricReading
import com.bios.app.model.MigraineAttack
import com.bios.contracts.MetricType
import java.util.UUID

/**
 * Pure helper that turns a [MigraineAttack] or [HeadacheLog] entity
 * into the cross-cutting `metric_readings` + `event_payloads` writeset
 * the chronic-migraine and MOH evaluators read (#283 Cut 2).
 *
 * The entity tables stay the primary store — the diary screen reads
 * them, and they carry richer structured fields than fit in a generic
 * payload sidecar. This helper produces a denormalised view of the
 * same event so engines can scan a single `metric_readings` window
 * for headache-days / migraine-days / treatment-days, without
 * cross-table joins.
 *
 * The parent reading uses the entity's own UUID as its `id` so an
 * entity-table delete can clear the matching `MetricReading` by id
 * without an indirect lookup (see
 * [com.bios.app.data.dao.MetricReadingDao.deleteById]).
 *
 * `value` carries the 0–10 intensity. `durationSec` carries the event
 * duration when known. Confidence is [ConfidenceTier.HIGH] — owner
 * assertion is the highest-grade signal we get for headache events.
 *
 * When the entity records an abortive medication, the writeset also
 * includes a child [MetricType.MEDICATION_INTAKE] [MetricReading] +
 * a reciprocal payload link on the parent
 * ([HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID]). The
 * `value` of the child intake is `1.0` (presence marker) — the
 * free-text dose lives in a `medication_name` payload field on the
 * child, and Cut 3 / future work can parse the mg out when the
 * pharmacokinetic-engine path needs it.
 */
internal object HeadacheEventWriter {

    /** Field key on the child MEDICATION_INTAKE row that carries the
     *  owner's free-text medication name (e.g. "sumatriptan 50 mg").
     *  Lives here, not in [HeadacheEventFields], because it's a key
     *  on the **child** row, not on the parent headache event. */
    const val MEDICATION_NAME_FIELD = "medication_name"

    /** Field key on the child MEDICATION_INTAKE row that links back to
     *  the parent headache event reading id. Reciprocal of
     *  [HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID]; lets a
     *  scan-from-the-medication-side path (e.g. future MS DMT
     *  adherence) walk back to the triggering attack. */
    const val PARENT_HEADACHE_EVENT_FIELD = "parent_headache_event_id"

    data class WriteSet(
        val parent: MetricReading,
        val payload: List<EventPayloadField>,
        /** Child MEDICATION_INTAKE row when the entity recorded an
         *  abortive medication; null otherwise. */
        val abortiveIntake: MetricReading?,
        /** Payload rows for the child intake (medication name +
         *  back-link to the parent). Empty when [abortiveIntake] is
         *  null. */
        val abortivePayload: List<EventPayloadField>,
    )

    fun fromMigraineAttack(
        attack: MigraineAttack,
        sourceId: String,
        now: Long = System.currentTimeMillis(),
    ): WriteSet {
        val parent = MetricReading(
            id = attack.id,
            metricType = MetricType.MIGRAINE_ATTACK_EVENT.key,
            value = attack.peakIntensity.toDouble(),
            timestamp = attack.onsetTimestamp,
            durationSec = attack.endTimestamp?.let {
                ((it - attack.onsetTimestamp) / 1_000L).toInt().coerceAtLeast(0)
            },
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
            createdAt = now,
        )
        val payload = buildList {
            if (attack.aura) add(boolField(parent.id, HeadacheEventFields.AURA, true))
            attack.triggers.takeIf { it.isNotEmpty() }?.let { triggers ->
                add(
                    EventPayloadField(
                        readingId = parent.id,
                        fieldKey = HeadacheEventFields.TRIGGERS,
                        stringValue = triggers.joinToString(",") { it.name },
                    )
                )
            }
            attack.medicationTaken?.let { med ->
                add(
                    EventPayloadField(
                        readingId = parent.id,
                        fieldKey = HeadacheEventFields.ABORTIVE_MEDICATION,
                        stringValue = med,
                    )
                )
            }
            attack.notes?.let { add(stringField(parent.id, HeadacheEventFields.NOTES, it)) }
        }
        return wrapWithAbortiveIntake(parent, payload, attack.medicationTaken, sourceId, now)
    }

    fun fromHeadacheLog(
        log: HeadacheLog,
        sourceId: String,
        now: Long = System.currentTimeMillis(),
    ): WriteSet {
        val parent = MetricReading(
            id = log.id,
            metricType = metricTypeForHeadache(log.type).key,
            value = log.intensity.toDouble(),
            timestamp = log.timestamp,
            durationSec = log.durationMinutes?.let { (it.toLong() * 60L).toInt().coerceAtLeast(0) },
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
            createdAt = now,
        )
        val payload = buildList {
            log.medicationTaken?.let { med ->
                add(
                    EventPayloadField(
                        readingId = parent.id,
                        fieldKey = HeadacheEventFields.ABORTIVE_MEDICATION,
                        stringValue = med,
                    )
                )
            }
            log.notes?.let { add(stringField(parent.id, HeadacheEventFields.NOTES, it)) }
        }
        return wrapWithAbortiveIntake(parent, payload, log.medicationTaken, sourceId, now)
    }

    /** [HeadacheType.MIGRAINE] gets routed to [MetricType.MIGRAINE_ATTACK_EVENT]
     *  so the chronic-migraine evaluator's migraine-day count picks it up
     *  alongside [MigraineAttack] rows. CLUSTER routes to the dedicated
     *  cluster event so #284's periodicity histogram has a clean stream.
     *  Everything else (TENSION / OTHER) is generic HEADACHE_ATTACK_EVENT. */
    private fun metricTypeForHeadache(type: HeadacheType): MetricType = when (type) {
        HeadacheType.MIGRAINE -> MetricType.MIGRAINE_ATTACK_EVENT
        HeadacheType.CLUSTER -> MetricType.CLUSTER_HEADACHE_ATTACK_EVENT
        HeadacheType.TENSION, HeadacheType.OTHER -> MetricType.HEADACHE_ATTACK_EVENT
    }

    private fun wrapWithAbortiveIntake(
        parent: MetricReading,
        parentPayload: List<EventPayloadField>,
        medicationTaken: String?,
        sourceId: String,
        now: Long,
    ): WriteSet {
        if (medicationTaken.isNullOrBlank()) {
            return WriteSet(parent, parentPayload, abortiveIntake = null, abortivePayload = emptyList())
        }
        val intakeId = UUID.randomUUID().toString()
        val intake = MetricReading(
            id = intakeId,
            metricType = MetricType.MEDICATION_INTAKE.key,
            value = 1.0, // presence marker; dose parsing is future work
            timestamp = parent.timestamp,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
            createdAt = now,
        )
        val intakePayload = listOf(
            EventPayloadField(
                readingId = intakeId,
                fieldKey = MEDICATION_NAME_FIELD,
                stringValue = medicationTaken,
            ),
            EventPayloadField(
                readingId = intakeId,
                fieldKey = PARENT_HEADACHE_EVENT_FIELD,
                stringValue = parent.id,
            ),
        )
        // Add the parent-side back-link payload field.
        val parentWithLink = parentPayload + EventPayloadField(
            readingId = parent.id,
            fieldKey = HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID,
            stringValue = intakeId,
        )
        return WriteSet(parent, parentWithLink, intake, intakePayload)
    }

    private fun boolField(readingId: String, key: String, value: Boolean): EventPayloadField =
        EventPayloadField(
            readingId = readingId,
            fieldKey = key,
            longValue = if (value) 1L else 0L,
        )

    private fun stringField(readingId: String, key: String, value: String): EventPayloadField =
        EventPayloadField(
            readingId = readingId,
            fieldKey = key,
            stringValue = value,
        )
}
