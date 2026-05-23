package com.bios.app.engine

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.ExerciseSession
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.util.UUID

/**
 * Producer wiring for [HrRecoveryComputer] (#267). PR #261 shipped the
 * computer and the `advanced_cardiology_hr_recovery_impaired` pattern, but
 * left no producer writing HR_RECOVERY_1MIN / HR_RECOVERY_2MIN
 * [MetricReading]s — the readings table stayed empty for those keys and
 * the pattern could never fire.
 *
 * This persister is session-triggered: after [IngestManager] lands an
 * [ExerciseSession], it queries the HR samples bracketing the session end
 * and writes the HRR readings keyed to the session-end timestamp.
 *
 * ## Idempotency
 *
 * Each HRR reading is keyed by a deterministic UUID derived from
 * (metricType, sourceId, sessionEndMs). Re-running the persister on the
 * same session produces the same id; Room's `REPLACE` strategy keeps the
 * row count flat. This matters because [IngestManager.persistSessions]
 * does not yet dedupe exercise-session rows — a re-ingested session would
 * otherwise double-write HRR.
 */
object HrRecoveryPersister {

    /**
     * Extra window after session end included in the HR query so
     * [HrRecoveryComputer]'s ±5 s tolerance has post-exercise samples
     * available at both the HRR1 (60 s) and HRR2 (120 s) anchors.
     */
    private const val POST_EXERCISE_QUERY_PAD_MS: Long =
        HrRecoveryComputer.HRR2_TAU_MS + HrRecoveryComputer.POST_EXERCISE_SAMPLE_TOLERANCE_MS

    /**
     * Compute and persist HRR readings for the given [sessions]. Returns
     * the rows written so callers can fold them into existing pipelines
     * (logging, derivation counters). Empty when no session yields a
     * computable result.
     */
    suspend fun persistFor(
        sessions: List<ExerciseSession>,
        readingDao: MetricReadingDao,
    ): List<MetricReading> {
        if (sessions.isEmpty()) return emptyList()
        val rows = mutableListOf<MetricReading>()
        for (session in sessions) {
            val parent = session.reading
            val durationSec = parent.durationSec ?: continue
            val startMs = parent.timestamp
            val endMs = startMs + durationSec * 1000L
            val hrReadings = readingDao.fetch(
                metricType = MetricType.HEART_RATE.key,
                startMillis = startMs,
                endMillis = endMs + POST_EXERCISE_QUERY_PAD_MS,
            )
            val result = HrRecoveryComputer.compute(startMs, endMs, hrReadings) ?: continue
            result.hrr1BpmDelta?.let {
                rows += hrrReading(MetricType.HR_RECOVERY_1MIN, it, endMs, parent)
            }
            result.hrr2BpmDelta?.let {
                rows += hrrReading(MetricType.HR_RECOVERY_2MIN, it, endMs, parent)
            }
        }
        if (rows.isNotEmpty()) readingDao.insertAll(rows)
        return rows
    }

    private fun hrrReading(
        type: MetricType,
        bpmDelta: Double,
        sessionEndMs: Long,
        parent: MetricReading,
    ): MetricReading = MetricReading(
        id = stableId(type, parent.sourceId, sessionEndMs),
        metricType = type.key,
        value = bpmDelta,
        timestamp = sessionEndMs,
        sourceId = parent.sourceId,
        confidence = parent.confidence,
    )

    internal fun stableId(type: MetricType, sourceId: String, sessionEndMs: Long): String =
        UUID.nameUUIDFromBytes("${type.key}|$sourceId|$sessionEndMs".toByteArray()).toString()
}
