package com.bios.app.ui.screening

import android.content.Context
import com.bios.app.data.BiosDatabase
import com.bios.app.data.RiskProfileRepo
import com.bios.app.data.ScreeningEntryRepo
import com.bios.app.model.ScreeningEntry
import com.bios.app.screening.CheckupHealthDataLink
import com.bios.app.screening.OwnerDemographicsStore
import com.bios.app.screening.ScreeningCadenceEngine
import com.bios.app.screening.ScreeningCatalog
import com.bios.app.screening.ScreeningStatus
import java.util.Calendar

/**
 * Shared pull-side data assembly for Preventive Care. Both the screen and
 * the passive Settings badge read through here, so "what's due" is computed
 * exactly one way.
 *
 * Nothing here pushes: every caller is owner-initiated (the screen the owner
 * opened, or the Settings card the owner is already looking at). No
 * notifications, no background work — the manifesto's pull-side rule holds.
 */
object PreventiveCareData {

    /**
     * Most-recent "performed" date per screening key, merging the owner's
     * manual screening ledger with dates Bios can *derive* from existing
     * health data (a blood-pressure reading satisfies `blood_pressure_check`,
     * etc. — see [CheckupHealthDataLink]). Most-recent wins, so either a
     * manual entry or a fresh reading keeps the checkup current and the
     * owner never re-logs data Bios already holds. Closes #400.
     */
    suspend fun latestPerformedByKey(context: Context): Map<String, Long?> {
        val db = BiosDatabase.getInstance(context)
        val merged = ScreeningEntryRepo(db).fetchAll()
            .groupBy { it.screeningKey }
            .mapValues { (_, list) -> list.maxOfOrNull { it.performedDate } }
            .toMutableMap()

        val readingDao = db.metricReadingDao()
        for ((screeningKey, metricKeys) in CheckupHealthDataLink.metricKeysByScreeningKey) {
            val derived = metricKeys
                .mapNotNull { key -> readingDao.fetchLatest(key, 1).firstOrNull()?.timestamp }
                .maxOrNull() ?: continue
            merged[screeningKey] = maxOf(merged[screeningKey] ?: 0L, derived)
        }
        return merged
    }

    /**
     * Count of entries that read as [ScreeningStatus.DueNow] (recurring and
     * overdue) for the active owner — the number behind the passive badge on
     * the Settings "Preventive care" row (#401). Deliberately conservative:
     *
     *  - minimum-interval checkups never contribute (they're never "overdue"
     *    — the no-shame rule), and
     *  - `NoRecord` doesn't count (not-yet-started ≠ due), so a fresh install
     *    shows no badge rather than a wall of nags.
     *
     * Returns 0 when birth year isn't set (no demographics → nothing to say).
     */
    suspend fun dueCount(context: Context, now: Long = System.currentTimeMillis()): Int {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val demographics = OwnerDemographicsStore(context).load(currentYear) ?: return 0
        val latest = latestPerformedByKey(context)
        val riskProfile = RiskProfileRepo(BiosDatabase.getInstance(context)).fetch()
        return ScreeningCadenceEngine.evaluateAll(
            catalog = ScreeningCatalog.combined,
            demographics = demographics,
            latestByKey = { key ->
                latest[key]?.let { ScreeningEntry(screeningKey = key, performedDate = it) }
            },
            riskProfile = riskProfile,
            now = now,
        ).count { (_, status) -> status is ScreeningStatus.DueNow }
    }
}
