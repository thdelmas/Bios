package com.bios.app.data

import com.bios.app.engine.CycleInference
import com.bios.contracts.MetricType

/**
 * Shared re-derivation surface for cycle phases + day-of-cycle numbering.
 *
 * Both [BbtEntryRepo] (on new BBT) and [PeriodEntryRepo] (on new
 * menstruation onset) call [rederive] so the CYCLE_PHASE and CYCLE_DAY
 * series stay in sync regardless of which writer touched the database.
 *
 * Re-derivation walks the last 90 days of BBT + onset data, re-runs
 * [CycleInference], and upserts the derived rows with deterministic UTC-
 * day IDs so `OnConflictStrategy.REPLACE` keeps the series idempotent
 * across repeated runs.
 */
internal object CycleDerivation {

    private const val MS_PER_DAY = 86_400_000L
    private const val WINDOW_MS = 90L * MS_PER_DAY

    suspend fun rederive(db: ReproductiveDatabase, sourceId: String) {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_MS

        val bbts = db.readingDao().fetch(
            MetricType.BASAL_BODY_TEMPERATURE.key,
            windowStart,
            Long.MAX_VALUE
        )
        val onsets = db.readingDao().fetch(
            MetricType.MENSTRUATION_ONSET.key,
            windowStart,
            Long.MAX_VALUE
        )

        val phases = CycleInference.deriveCyclePhases(bbts, sourceId, onsets)
        if (phases.isNotEmpty()) {
            db.readingDao().insertAll(
                phases.map { it.copy(id = stableCyclePhaseId(it.timestamp)) }
            )
        }

        val days = CycleInference.deriveCycleDays(onsets, now, sourceId)
        if (days.isNotEmpty()) {
            db.readingDao().insertAll(
                days.map { it.copy(id = stableCycleDayId(it.timestamp)) }
            )
        }
    }

    /**
     * Deterministic primary key for a derived CYCLE_PHASE row, keyed by
     * the UTC day bucket so re-running [CycleInference] over a growing
     * BBT + onset series produces stable row ids.
     */
    fun stableCyclePhaseId(timestamp: Long): String {
        val day = timestamp / MS_PER_DAY
        return "cycle_phase_self_reported_$day"
    }

    /**
     * Deterministic primary key for a derived CYCLE_DAY row, keyed by the
     * UTC day bucket. Same idempotence rationale as [stableCyclePhaseId].
     */
    fun stableCycleDayId(timestamp: Long): String {
        val day = timestamp / MS_PER_DAY
        return "cycle_day_self_reported_$day"
    }

    /**
     * Deterministic primary key for a manually logged MENSTRUATION_ONSET
     * row. Two onsets on the same UTC day collapse into one — the owner
     * meant to log day-1, not duplicate it.
     */
    fun stableMenstruationOnsetId(timestamp: Long): String {
        val day = timestamp / MS_PER_DAY
        return "menstruation_onset_self_reported_$day"
    }
}
