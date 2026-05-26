package com.bios.app.data

import android.content.Context
import com.bios.app.model.GenderAffirmingCareEntry
import com.bios.app.model.GenderAffirmingHormoneType

/**
 * Persistence path for owner-recorded gender-affirming hormone episodes
 * (#209). The owner records what they're on; Bios uses that to interpret
 * baseline signals correctly. No gender-identity field by design — the
 * Indigenous Americas audit (§2.4) named the binary gender model as
 * itself a design defect; Bios stores hormones, not labels.
 *
 * **Storage isolation.** Persists in [ReproductiveDatabase] (separate
 * SQLCipher file, independent key, priority destruction on duress).
 * Despite the legacy "reproductive" name, the DB is the right encryption
 * envelope for any sensitive reproductive / hormone data.
 */
class GenderAffirmingCareEntryRepo(context: Context) {

    private val appContext = context.applicationContext

    init {
        ReproductiveDatabase.initialize(appContext)
    }

    private val db: ReproductiveDatabase
        get() = ReproductiveDatabase.getInstance(appContext)

    suspend fun add(
        hormoneType: GenderAffirmingHormoneType,
        startDate: Long,
        endDate: Long? = null,
        dose: String? = null,
        notes: String? = null,
    ): GenderAffirmingCareEntry {
        val entry = GenderAffirmingCareEntry(
            hormoneType = hormoneType.name,
            startDate = startDate,
            endDate = endDate,
            dose = dose?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
        )
        db.genderAffirmingCareEntryDao().insert(entry)
        return entry
    }

    suspend fun fetchAll(): List<GenderAffirmingCareEntry> =
        db.genderAffirmingCareEntryDao().fetchAll()

    suspend fun fetchLatest(): GenderAffirmingCareEntry? =
        db.genderAffirmingCareEntryDao().fetchLatest()

    suspend fun delete(entry: GenderAffirmingCareEntry) {
        db.genderAffirmingCareEntryDao().delete(entry)
    }
}
