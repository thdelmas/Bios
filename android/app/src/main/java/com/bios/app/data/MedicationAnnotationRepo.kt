package com.bios.app.data

import com.bios.app.model.MedicationAnnotation

/**
 * Thin repository wrapper over [com.bios.app.data.dao.MedicationAnnotationDao]
 * for the medications screen and the alert-explanation enhancer. The DAO
 * already exposes the queries; this layer adds two ergonomic helpers
 * ([add] and [markDiscontinued]) that other surfaces use without thinking
 * about timestamps.
 *
 * Audit gap §2.5 — owner records current medications so the alert surface
 * can contextualise patterns that depend on the medication backdrop
 * (beta-blockers + bradycardia patterns, levothyroxine + tachycardia
 * patterns, steroid course + glucose variability, etc.).
 */
class MedicationAnnotationRepo(private val db: BiosDatabase) {

    private val dao get() = db.medicationAnnotationDao()

    /**
     * Records a new medication the owner is currently taking.
     * Returns the auto-generated row id.
     *
     * @param startDate epoch millis the owner started this medication;
     *   defaults to "right now" so the typical add-from-the-screen path
     *   doesn't have to pick a date.
     */
    suspend fun add(name: String, startDate: Long = System.currentTimeMillis(), note: String? = null): Long {
        require(name.isNotBlank()) { "Medication name cannot be blank" }
        return dao.insert(
            MedicationAnnotation(
                name = name.trim(),
                startDate = startDate,
                endDate = null,
                note = note?.takeIf { it.isNotBlank() }?.trim(),
            )
        )
    }

    /**
     * Marks an existing medication as discontinued. The row stays in the
     * DB — past medications are part of the owner's own context (RHR
     * normalising three weeks after stopping a beta-blocker is less
     * mysterious when the stop date is on record).
     */
    suspend fun markDiscontinued(id: Long, endDate: Long = System.currentTimeMillis()) {
        val existing = dao.fetchById(id) ?: return
        dao.update(existing.copy(endDate = endDate))
    }

    /** Hard-delete a row. Use for typo correction; mark-discontinued is the right path for end-of-course. */
    suspend fun remove(id: Long) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun update(annotation: MedicationAnnotation) = dao.update(annotation)
    suspend fun fetchAll(): List<MedicationAnnotation> = dao.fetchAll()
    suspend fun fetchActive(): List<MedicationAnnotation> = dao.fetchActive()
    suspend fun fetchActiveOn(at: Long): List<MedicationAnnotation> = dao.fetchActiveOn(at)

    /**
     * Formats the list of active medications for inclusion in an alert
     * explanation. Returns `null` when no active meds are recorded so
     * callers can skip the line cleanly. Otherwise returns a single
     * sentence: "Annotated current medications: metoprolol, atorvastatin."
     * Names are joined with commas in DAO order (newest startDate first).
     */
    suspend fun formatActiveContext(): String? = formatMedicationContext(fetchActive())

    /**
     * Same as [formatActiveContext] but scoped to a specific point in time —
     * useful when surfacing context next to a historical anomaly. Returns
     * `null` when nothing was on record at that timestamp.
     */
    suspend fun formatActiveContextOn(at: Long): String? = formatMedicationContext(fetchActiveOn(at))

    companion object {
        /**
         * Pure formatter for the alert-context line. Extracted from the
         * suspend wrappers so the formatting can be unit-tested without
         * a DB. The phrasing is deliberately neutral ("annotated", not
         * "your medications") so the line stays a data statement, not a
         * judgment — compliant with [com.bios.app.alerts.AlertContentPolicy].
         */
        fun formatMedicationContext(meds: List<MedicationAnnotation>): String? {
            if (meds.isEmpty()) return null
            val names = meds.joinToString(", ") { it.name }
            return "Annotated current medications: $names."
        }
    }
}
