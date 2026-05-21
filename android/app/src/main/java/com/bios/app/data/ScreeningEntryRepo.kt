package com.bios.app.data

import com.bios.app.model.ScreeningEntry

/**
 * Thin repository wrapper over the screening-history DAO. Closes audit
 * gap §2.2 — the pull-side cadence engine reads this surface alongside
 * the static catalog to compute "next due" per screening.
 */
class ScreeningEntryRepo(private val db: BiosDatabase) {

    private val dao get() = db.screeningEntryDao()

    suspend fun add(
        screeningKey: String,
        performedDate: Long = System.currentTimeMillis(),
        note: String? = null,
    ): Long {
        require(screeningKey.isNotBlank()) { "screeningKey cannot be blank" }
        return dao.insert(
            ScreeningEntry(
                screeningKey = screeningKey.trim(),
                performedDate = performedDate,
                note = note?.takeIf { it.isNotBlank() }?.trim(),
            )
        )
    }

    suspend fun remove(id: Long) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun fetchAll(): List<ScreeningEntry> = dao.fetchAll()
    suspend fun fetchLatestByKey(key: String): ScreeningEntry? = dao.fetchLatestByKey(key)
}
