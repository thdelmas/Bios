package com.bios.app.data

import android.content.Context
import com.bios.app.model.ContraceptionEntry
import com.bios.app.model.ContraceptionMethod

/**
 * Persistence path for owner-recorded contraception methods (#209,
 * OBGYN_POV §2.11).
 *
 * **Storage isolation.** Persists in [ReproductiveDatabase] — a separate
 * SQLCipher file with an independent encryption key, independent retention,
 * and priority destruction on LETHE duress PIN / dead-man's-switch. The
 * owner can wipe contraception history without touching the main DB. This
 * isolation is the audit-recommended threat model: contraception history is
 * itself sensitive in jurisdictions that police it.
 *
 * The repo lazily initialises the reproductive DB on first construction so
 * the owner doesn't have to opt in separately — first contraception entry
 * just works. Owners who want an explicit passphrase can call
 * [ReproductiveDatabase.initialize] before first use; the lazy init is
 * idempotent.
 */
class ContraceptionEntryRepo(context: Context) {

    private val appContext = context.applicationContext

    init {
        // Idempotent — no-op if a key already exists.
        ReproductiveDatabase.initialize(appContext)
    }

    private val db: ReproductiveDatabase
        get() = ReproductiveDatabase.getInstance(appContext)

    suspend fun add(
        method: ContraceptionMethod,
        startDate: Long,
        endDate: Long? = null,
        notes: String? = null,
    ): ContraceptionEntry {
        val entry = ContraceptionEntry(
            method = method.name,
            startDate = startDate,
            endDate = endDate,
            notes = notes?.takeIf { it.isNotBlank() },
        )
        db.contraceptionEntryDao().insert(entry)
        return entry
    }

    suspend fun fetchAll(): List<ContraceptionEntry> =
        db.contraceptionEntryDao().fetchAll()

    suspend fun fetchLatest(): ContraceptionEntry? =
        db.contraceptionEntryDao().fetchLatest()

    suspend fun delete(entry: ContraceptionEntry) {
        db.contraceptionEntryDao().delete(entry)
    }

    /** Returns the parsed [ContraceptionMethod] of the latest entry, or null. */
    suspend fun fetchLatestMethod(): ContraceptionMethod? =
        fetchLatest()?.method?.let { name ->
            runCatching { ContraceptionMethod.valueOf(name) }.getOrNull()
        }
}
