package com.bios.app.data

import com.bios.app.model.ImmunizationRecord

/**
 * Thin repository wrapper over [com.bios.app.data.dao.ImmunizationRecordDao]
 * for the immunisations entry screen and the future screening-cadence
 * engine (#155). Closes audit gap §2.3.
 */
class ImmunizationRepo(private val db: BiosDatabase) {

    private val dao get() = db.immunizationRecordDao()

    /**
     * Records a new immunisation.
     *
     * @param vaccineName display name; required and non-blank
     * @param occurrenceDate epoch millis when administered (defaults to now)
     * @param cvxCode optional CDC CVX code — present when populated by
     *   FHIR import, null for free-text owner entries
     * @param doseNumber optional dose number for multi-dose series
     * @param lotNumber optional lot number for traceability
     * @param note optional owner free-text
     */
    suspend fun add(
        vaccineName: String,
        occurrenceDate: Long = System.currentTimeMillis(),
        cvxCode: String? = null,
        doseNumber: Int? = null,
        lotNumber: String? = null,
        note: String? = null,
    ): Long {
        require(vaccineName.isNotBlank()) { "Vaccine name cannot be blank" }
        return dao.insert(
            ImmunizationRecord(
                vaccineName = vaccineName.trim(),
                cvxCode = cvxCode?.takeIf { it.isNotBlank() }?.trim(),
                occurrenceDate = occurrenceDate,
                doseNumber = doseNumber,
                lotNumber = lotNumber?.takeIf { it.isNotBlank() }?.trim(),
                note = note?.takeIf { it.isNotBlank() }?.trim(),
            )
        )
    }

    suspend fun update(record: ImmunizationRecord) = dao.update(record)
    suspend fun remove(id: Long) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun fetchAll(): List<ImmunizationRecord> = dao.fetchAll()
    suspend fun fetchLatestByCvxCode(cvxCode: String): ImmunizationRecord? =
        dao.fetchLatestByCvxCode(cvxCode)
}
