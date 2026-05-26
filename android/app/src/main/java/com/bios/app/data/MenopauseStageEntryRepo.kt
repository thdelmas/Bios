package com.bios.app.data

import com.bios.app.model.MenopauseStage
import com.bios.app.model.MenopauseStageEntry
import android.content.Context

/**
 * Persistence path for owner-recorded menopause-stage transitions (#209,
 * OBGYN_POV §2.12, Modern Non-Allopathic perimenopause audit).
 *
 * STRAW+10 staging — Harlow SD et al. (2012) J Clin Endocrinol Metab 97:
 * 1159–1168. The owner stages themselves; Bios never infers from cycle
 * gaps or BBT pattern loss. History-preserving so earlier readings can
 * be interpreted under the stage the owner was in at the time.
 *
 * **Storage isolation.** Persists in [ReproductiveDatabase] (separate
 * SQLCipher file, independent key, priority destruction on duress).
 */
class MenopauseStageEntryRepo(context: Context) {

    private val appContext = context.applicationContext

    init {
        ReproductiveDatabase.initialize(appContext)
    }

    private val db: ReproductiveDatabase
        get() = ReproductiveDatabase.getInstance(appContext)

    suspend fun add(
        stage: MenopauseStage,
        recordedDate: Long,
        notes: String? = null,
    ): MenopauseStageEntry {
        val entry = MenopauseStageEntry(
            stage = stage.name,
            recordedDate = recordedDate,
            notes = notes?.takeIf { it.isNotBlank() },
        )
        db.menopauseStageEntryDao().insert(entry)
        return entry
    }

    suspend fun fetchAll(): List<MenopauseStageEntry> =
        db.menopauseStageEntryDao().fetchAll()

    suspend fun fetchLatest(): MenopauseStageEntry? =
        db.menopauseStageEntryDao().fetchLatest()

    /** Returns the parsed [MenopauseStage] of the latest entry, or null. */
    suspend fun fetchLatestStage(): MenopauseStage? =
        fetchLatest()?.stage?.let { name ->
            runCatching { MenopauseStage.valueOf(name) }.getOrNull()
        }

    suspend fun delete(entry: MenopauseStageEntry) {
        db.menopauseStageEntryDao().delete(entry)
    }
}
