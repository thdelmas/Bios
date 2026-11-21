package com.bios.app.data

import com.bios.app.model.EcgStrip

/** Thin repository wrapper over [com.bios.app.data.dao.EcgStripDao] for the ECG strips screen and file import. */
class EcgStripRepo(private val db: BiosDatabase) {

    private val dao get() = db.ecgStripDao()

    suspend fun insert(strip: EcgStrip) = dao.insert(strip)
    suspend fun fetchById(id: String): EcgStrip? = dao.fetchById(id)
    suspend fun fetchAll(): List<EcgStrip> = dao.fetchAll()
    suspend fun fetchInWindow(startMs: Long, endMs: Long): List<EcgStrip> =
        dao.fetchInWindow(startMs, endMs)
    suspend fun remove(id: String) = dao.deleteById(id)
    suspend fun count(): Int = dao.count()
}
