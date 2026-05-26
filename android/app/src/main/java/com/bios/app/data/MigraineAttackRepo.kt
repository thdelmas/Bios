package com.bios.app.data

import com.bios.app.model.HeadacheEventFields
import com.bios.app.model.MigraineAttack
import com.bios.app.model.MigraineTrigger

/**
 * Thin repository wrapper over [com.bios.app.data.dao.MigraineAttackDao]
 * for the migraine-diary screen and the MOH evaluator (issue #207).
 *
 * Validates intensity at the insert boundary; the UI slider also pins
 * 0-10. The split-validation is intentional — a direct DAO call from a
 * test or a future engine path still gets the range check here.
 *
 * Stores in the main encrypted DB ([BiosDatabase]).
 */
class MigraineAttackRepo(private val db: BiosDatabase) {

    private val dao get() = db.migraineAttackDao()

    /**
     * Records a new migraine attack.
     *
     * @param onsetTimestamp epoch millis the owner records as attack start
     * @param peakIntensity 0-10 NRS
     * @param aura whether aura was present (IHS ICHD-3 §1.2)
     * @param triggers owner-identified triggers
     * @param medicationTaken free-text acute-treatment name (or null)
     * @param notes optional owner free-text
     * @return the new attack's id
     */
    suspend fun add(
        onsetTimestamp: Long = System.currentTimeMillis(),
        endTimestamp: Long? = null,
        peakIntensity: Int,
        aura: Boolean = false,
        triggers: Set<MigraineTrigger> = emptySet(),
        medicationTaken: String? = null,
        notes: String? = null,
    ): String {
        MigraineAttack.validateIntensity(peakIntensity)
        val attack = MigraineAttack(
            onsetTimestamp = onsetTimestamp,
            endTimestamp = endTimestamp,
            peakIntensity = peakIntensity,
            aura = aura,
            triggers = triggers,
            medicationTaken = medicationTaken?.trim()?.takeIf { it.isNotBlank() },
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
        )
        dao.insert(attack)
        writeMetricReadingMirror(attack)
        return attack.id
    }

    /** Mark an in-progress attack as resolved at the given [endTimestamp]. */
    suspend fun close(id: String, endTimestamp: Long = System.currentTimeMillis()) {
        val existing = dao.fetchById(id) ?: return
        val updated = existing.copy(endTimestamp = endTimestamp)
        dao.update(updated)
        // Refresh the metric_readings mirror so the parent row's
        // durationSec is up to date for the chronic-migraine evaluator
        // (Cut 3). Cheapest path: delete-then-rewrite.
        clearMetricReadingMirror(existing)
        writeMetricReadingMirror(updated)
    }

    suspend fun remove(id: String) {
        val existing = dao.fetchById(id)
        dao.deleteById(id)
        existing?.let { clearMetricReadingMirror(it) }
    }

    private suspend fun writeMetricReadingMirror(attack: MigraineAttack) {
        val sourceId = resolveSelfReportedSource(db)
        val writeSet = HeadacheEventWriter.fromMigraineAttack(attack, sourceId)
        db.metricReadingDao().insert(writeSet.parent)
        db.eventPayloadFieldDao().insertAll(writeSet.payload)
        writeSet.abortiveIntake?.let {
            db.metricReadingDao().insert(it)
            db.eventPayloadFieldDao().insertAll(writeSet.abortivePayload)
        }
    }

    private suspend fun clearMetricReadingMirror(attack: MigraineAttack) {
        // Walk the parent payload to find any linked MEDICATION_INTAKE
        // child id, then cascade. Two DAO calls — the payload table is
        // small per reading so the round-trips are cheap.
        val payloadDao = db.eventPayloadFieldDao()
        val readingDao = db.metricReadingDao()
        val parentPayload = payloadDao.fetchForReading(attack.id)
        val intakeId = parentPayload
            .firstOrNull { it.fieldKey == HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID }
            ?.stringValue
        payloadDao.deleteForReading(attack.id)
        readingDao.deleteById(attack.id)
        if (intakeId != null) {
            payloadDao.deleteForReading(intakeId)
            readingDao.deleteById(intakeId)
        }
    }

    suspend fun fetchAll(): List<MigraineAttack> = dao.fetchAll()

    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<MigraineAttack> =
        dao.fetchInRange(fromMillis, toMillis)
}
