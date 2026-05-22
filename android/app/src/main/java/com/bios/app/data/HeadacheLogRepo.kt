package com.bios.app.data

import com.bios.app.model.HeadacheLog
import com.bios.app.model.HeadacheType

/**
 * Thin repository wrapper over [com.bios.app.data.dao.HeadacheLogDao]
 * for the generic headache-diary screen and the MOH evaluator
 * (issue #207).
 *
 * Validates intensity at the insert boundary; the UI slider also pins
 * 0-10. Stored in the main encrypted DB ([BiosDatabase]).
 */
class HeadacheLogRepo(private val db: BiosDatabase) {

    private val dao get() = db.headacheLogDao()

    /**
     * Records a new headache log entry.
     *
     * @param timestamp epoch millis when the headache occurred (defaults to now)
     * @param intensity 0-10 NRS
     * @param type IHS ICHD-3 high-level grouping
     * @param durationMinutes optional duration in minutes
     * @param medicationTaken free-text acute-treatment name (or null)
     * @param notes optional owner free-text
     * @return the new log's id
     */
    suspend fun add(
        timestamp: Long = System.currentTimeMillis(),
        intensity: Int,
        type: HeadacheType,
        durationMinutes: Int? = null,
        medicationTaken: String? = null,
        notes: String? = null,
    ): String {
        HeadacheLog.validateIntensity(intensity)
        val log = HeadacheLog(
            timestamp = timestamp,
            intensity = intensity,
            type = type,
            durationMinutes = durationMinutes?.takeIf { it >= 0 },
            medicationTaken = medicationTaken?.trim()?.takeIf { it.isNotBlank() },
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
        )
        dao.insert(log)
        return log.id
    }

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun fetchAll(): List<HeadacheLog> = dao.fetchAll()

    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<HeadacheLog> =
        dao.fetchInRange(fromMillis, toMillis)
}
