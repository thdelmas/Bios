package com.bios.app.data

import com.bios.app.model.MedicalTradition
import com.bios.app.model.TreatmentCourse

/**
 * Thin repository wrapper over [com.bios.app.data.dao.TreatmentCourseDao]
 * for the treatment-courses Settings screen (#192).
 *
 * Owner-declared metadata only — no adherence tracking, no reminders,
 * no automated closing. Closing a course is an explicit owner action
 * via [close].
 */
class TreatmentCourseRepo(private val db: BiosDatabase) {

    private val dao get() = db.treatmentCourseDao()

    /**
     * Records a new treatment course.
     *
     * @param label owner-supplied display label; required and non-blank
     * @param startedAt epoch millis the course began (defaults to now)
     * @param tradition optional tradition the course belongs to
     * @param expectedEndAt optional expected end date
     * @param goal optional owner free-text goal
     * @param notes optional owner free-text
     */
    suspend fun add(
        label: String,
        startedAt: Long = System.currentTimeMillis(),
        tradition: MedicalTradition? = null,
        expectedEndAt: Long? = null,
        goal: String? = null,
        notes: String? = null,
    ): String {
        require(label.isNotBlank()) { "Treatment course label cannot be blank" }
        val course = TreatmentCourse(
            label = label.trim(),
            tradition = tradition,
            startedAt = startedAt,
            expectedEndAt = expectedEndAt,
            actualEndAt = null,
            goal = goal?.takeIf { it.isNotBlank() }?.trim(),
            notes = notes?.takeIf { it.isNotBlank() }?.trim(),
        )
        dao.insert(course)
        return course.id
    }

    /**
     * Marks an existing course as closed. Owner-driven only — Bios never
     * closes a course automatically, even when `expectedEndAt` is in the
     * past.
     */
    suspend fun close(id: String, actualEndAt: Long = System.currentTimeMillis()) {
        val existing = dao.fetchById(id) ?: return
        dao.update(existing.copy(actualEndAt = actualEndAt))
    }

    /**
     * Reopens a previously closed course (`actualEndAt = null`). Owners
     * sometimes mark a course done and then change their mind — the
     * primitive supports that without forcing a new course creation.
     */
    suspend fun reopen(id: String) {
        val existing = dao.fetchById(id) ?: return
        dao.update(existing.copy(actualEndAt = null))
    }

    suspend fun update(course: TreatmentCourse) = dao.update(course)

    suspend fun remove(id: String) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun fetchAll(): List<TreatmentCourse> = dao.fetchAll()
    suspend fun fetchById(id: String): TreatmentCourse? = dao.fetchById(id)
    suspend fun fetchOpen(): List<TreatmentCourse> = dao.fetchOpen()
}
