package com.bios.app.data

import android.content.Context
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Persistence path for manually logged menstruation onsets — owner taps
 * "Log period start" on the date their period began — plus the cycle-day
 * and cycle-phase derivations that re-run on every save.
 *
 * Lives alongside [BbtEntryRepo] on the same isolated [ReproductiveDatabase]
 * (separate SQLCipher file, independent encryption key, priority
 * destruction on LETHE duress PIN / dead-man's-switch). The owner can
 * wipe reproductive data — including their period log — without touching
 * the main DB.
 *
 * Saves are idempotent per UTC day via [CycleDerivation.stableMenstruationOnsetId],
 * so re-logging the same day collapses into one row. Cycle-day numbering
 * (cycle day 1 = first day of menstruation) anchors on the most recent
 * onset on or before each day; the MENSTRUAL phase overlays the five
 * days starting at each onset (FIGO/Munro 2018 median).
 */
class PeriodEntryRepo(context: Context) {

    private val appContext = context.applicationContext

    init {
        // Idempotent — no-op if a key already exists.
        ReproductiveDatabase.initialize(appContext)
    }

    private val db: ReproductiveDatabase
        get() = ReproductiveDatabase.getInstance(appContext)

    suspend fun addOnset(timestamp: Long) {
        val sourceId = SelfReportedSource.getOrCreate(db)
        db.readingDao().insert(
            MetricReading(
                id = CycleDerivation.stableMenstruationOnsetId(timestamp),
                metricType = MetricType.MENSTRUATION_ONSET.key,
                value = 1.0,
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        )
        CycleDerivation.rederive(db, sourceId)
    }

    suspend fun fetchRecentOnsets(limit: Int = 12): List<MetricReading> =
        db.readingDao().fetchLatest(MetricType.MENSTRUATION_ONSET.key, limit)

    suspend fun fetchLatestCycleDay(): MetricReading? =
        db.readingDao().fetchLatest(MetricType.CYCLE_DAY.key, limit = 1).firstOrNull()

    /** Derived CYCLE_PHASE rows in a window — the calendar's paint data. */
    suspend fun fetchPhases(startMillis: Long, endMillis: Long): List<MetricReading> =
        db.readingDao().fetch(MetricType.CYCLE_PHASE.key, startMillis, endMillis)

    /** All onsets since [sinceMillis] — feeds cycle-length statistics. */
    suspend fun fetchOnsetsSince(sinceMillis: Long): List<MetricReading> =
        db.readingDao().fetch(MetricType.MENSTRUATION_ONSET.key, sinceMillis, Long.MAX_VALUE)
}
