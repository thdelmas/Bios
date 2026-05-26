package com.bios.app.data

import com.bios.app.model.InterventionCategory
import com.bios.app.model.InterventionEvent
import com.bios.app.model.MedicalTradition

/**
 * Thin repository wrapper over [com.bios.app.data.dao.InterventionEventDao]
 * for the intervention-events Settings screen (#192).
 *
 * Same shape as [MedicationAnnotationRepo] / [ImmunizationRepo]: an
 * ergonomic [add] helper plus pass-throughs to the DAO. Owner-declared
 * only — no auto-classification, no inference.
 */
class InterventionEventRepo(private val db: BiosDatabase) {

    private val dao get() = db.interventionEventDao()

    /**
     * Records a new intervention event.
     *
     * @param category coarse-grained category; required
     * @param timestamp epoch millis when the intervention occurred; defaults to now
     * @param subType owner free-text refinement (e.g. "Tuina", "OMM lumbar HVLA")
     * @param practitionerTradition optional tradition the practitioner works in
     * @param notes optional owner free-text
     * @param bodyRegion optional anatomical region
     * @param treatmentCourseId optional FK into a parent course
     */
    suspend fun add(
        category: InterventionCategory,
        timestamp: Long = System.currentTimeMillis(),
        subType: String? = null,
        practitionerTradition: MedicalTradition? = null,
        notes: String? = null,
        bodyRegion: String? = null,
        treatmentCourseId: String? = null,
    ): String {
        val event = InterventionEvent(
            timestamp = timestamp,
            category = category,
            subType = subType?.takeIf { it.isNotBlank() }?.trim(),
            practitionerTradition = practitionerTradition,
            notes = notes?.takeIf { it.isNotBlank() }?.trim(),
            bodyRegion = bodyRegion?.takeIf { it.isNotBlank() }?.trim(),
            treatmentCourseId = treatmentCourseId,
        )
        dao.insert(event)
        return event.id
    }

    suspend fun update(event: InterventionEvent) = dao.update(event)

    suspend fun remove(id: String) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun fetchAll(): List<InterventionEvent> = dao.fetchAll()
    suspend fun fetchById(id: String): InterventionEvent? = dao.fetchById(id)
    suspend fun fetchByCourse(courseId: String): List<InterventionEvent> = dao.fetchByCourse(courseId)
    suspend fun fetchSince(since: Long): List<InterventionEvent> = dao.fetchSince(since)
}
