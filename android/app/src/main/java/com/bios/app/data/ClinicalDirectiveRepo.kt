package com.bios.app.data

import com.bios.app.model.ClinicalDirective

/**
 * Repository for the owner's single-row clinical-directive metadata
 * (#184). Companion to [GoalsOfCareRepo].
 *
 * Pull-side only — this surface is read by the Settings UI and never
 * by alert / detection code.
 */
class ClinicalDirectiveRepo(private val db: BiosDatabase) {

    private val dao get() = db.clinicalDirectiveDao()

    suspend fun save(directive: ClinicalDirective) {
        dao.save(
            directive.copy(
                id = ClinicalDirective.SINGLETON_ID,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun fetch(): ClinicalDirective? = dao.fetch()

    suspend fun fetchOrEmpty(): ClinicalDirective = dao.fetch() ?: ClinicalDirective()

    suspend fun clear() = dao.clear()
}
