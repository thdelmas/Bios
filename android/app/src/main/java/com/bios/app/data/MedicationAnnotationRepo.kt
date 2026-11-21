package com.bios.app.data

import com.bios.app.model.MedicationAnnotation

/**
 * Thin repository wrapper over [com.bios.app.data.dao.MedicationAnnotationDao]
 * for the medications screen and the alert-explanation enhancer. The DAO
 * already exposes the queries; this layer adds two ergonomic helpers
 * ([add] and [markDiscontinued]) that other surfaces use without thinking
 * about timestamps.
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
     * @param substanceSource optional vocabulary the [substanceCode] is
     *   drawn from (RxNorm, ATC, AYUSH_ICD11, TCM formula, Kampo NHI,
     *   WHO botanical monograph, etc.). Null defaults to the legacy
     *   free-text path. See [SubstanceSource].
     * @param substanceCode optional code drawn from [substanceSource];
     *   never validated against an external service.
     * @param traditionalName optional traditional / vernacular name in
     *   the owner's preferred script. Recorded verbatim.
     */
    suspend fun add(
        name: String,
        startDate: Long = System.currentTimeMillis(),
        note: String? = null,
        substanceSource: SubstanceSource? = null,
        substanceCode: String? = null,
        traditionalName: String? = null,
    ): Long {
        require(name.isNotBlank()) { "Medication name cannot be blank" }
        return dao.insert(
            MedicationAnnotation(
                name = name.trim(),
                startDate = startDate,
                endDate = null,
                note = note?.takeIf { it.isNotBlank() }?.trim(),
                substanceSource = substanceSource?.toPersistenceString(),
                substanceCode = substanceCode?.takeIf { it.isNotBlank() }?.trim(),
                traditionalName = traditionalName?.takeIf { it.isNotBlank() }?.trim(),
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
}
