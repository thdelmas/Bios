package com.bios.app.data

import com.bios.app.model.GoalsOfCare
import com.bios.app.model.InterventionLevel

/**
 * Repository for the owner's single-row goals-of-care declaration
 * (#184).
 *
 * The [com.bios.app.alerts.AlertManager] reads this surface to decide
 * whether to short-circuit URGENT-tier escalation. When the owner's
 * intervention level is [InterventionLevel.COMFORT_ONLY], the alert
 * manager silences automatic escalation in the same way hospice mode
 * does.
 *
 * The data is stored in the main encrypted DB and is wiped alongside
 * the rest of the owner's health data via the standard LETHE
 * "Delete all data" pathway.
 */
class GoalsOfCareRepo(private val db: BiosDatabase) {

    private val dao get() = db.goalsOfCareDao()

    suspend fun save(goals: GoalsOfCare) {
        dao.save(
            goals.copy(
                id = GoalsOfCare.SINGLETON_ID,
                lastReviewedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun fetch(): GoalsOfCare? = dao.fetch()

    suspend fun fetchOrEmpty(): GoalsOfCare = dao.fetch() ?: GoalsOfCare()

    suspend fun clear() = dao.clear()

    /**
     * True when the owner's declared intervention level is
     * comfort-only and URGENT-tier escalation should be silenced.
     */
    suspend fun isComfortOnly(): Boolean =
        dao.fetch()?.interventionLevel == InterventionLevel.COMFORT_ONLY
}
