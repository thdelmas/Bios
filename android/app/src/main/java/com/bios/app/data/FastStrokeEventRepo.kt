package com.bios.app.data

import com.bios.app.model.FastStrokeEvent

/**
 * Thin repository wrapper over [com.bios.app.data.dao.FastStrokeEventDao]
 * for the FAST stroke-recognition screen (issue #207, audit gap §2.15).
 *
 * Owner-input only. The screen records every check the owner runs — both
 * positive and negative — so a clinician export carries a complete record
 * of FAST screens, not just suspicious ones.
 */
class FastStrokeEventRepo(private val db: BiosDatabase) {

    private val dao get() = db.fastStrokeEventDao()

    /**
     * Records a FAST screen result.
     *
     * @param timestamp epoch millis when the screen was performed (defaults to now)
     * @param facialDrooping AHA/ASA FAST item 1
     * @param armWeakness AHA/ASA FAST item 2
     * @param speechDifficulty AHA/ASA FAST item 3
     * @param notes optional owner free-text
     * @return the new event's id
     */
    suspend fun add(
        timestamp: Long = System.currentTimeMillis(),
        facialDrooping: Boolean,
        armWeakness: Boolean,
        speechDifficulty: Boolean,
        notes: String? = null,
    ): String {
        val event = FastStrokeEvent(
            timestamp = timestamp,
            facialDrooping = facialDrooping,
            armWeakness = armWeakness,
            speechDifficulty = speechDifficulty,
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
        )
        dao.insert(event)
        return event.id
    }

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun fetchAll(): List<FastStrokeEvent> = dao.fetchAll()

    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<FastStrokeEvent> =
        dao.fetchInRange(fromMillis, toMillis)

    /** Positive FAST screens only — what a clinician reader most cares about. */
    suspend fun fetchPositive(): List<FastStrokeEvent> =
        dao.fetchAll().filter { it.isPositive }
}
