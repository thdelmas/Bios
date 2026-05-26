package com.bios.app.ingest

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.engine.SleepDerivations
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Re-derives sleep metrics (efficiency, TIB, latency, WASO, fragmentation,
 * score) from SLEEP_STAGE rows already in the DB. Repairs nights where
 * stages landed without the matching derivations — e.g. phone-inferred
 * sessions inserted before [PhoneSleepWorker.deriveSleep] was wired up,
 * or batches that bypassed [IngestManager.deriveAll] for any other
 * historical reason.
 *
 * Idempotent: each derivation uses the session's first-stage timestamp,
 * so re-running collapses through the unique
 * `(sourceId, metricType, timestamp)` index. Sessions are split on
 * inter-stage gaps > [SESSION_GAP_MS] to keep each derivation coherent
 * against a single sleep window rather than smearing across nights.
 */
class SleepDerivationsBackfill(
    private val readingDao: MetricReadingDao,
) {

    suspend fun run(windowStartMs: Long, windowEndMs: Long): Int {
        if (windowEndMs <= windowStartMs) return 0
        val stages = readingDao.fetch(
            MetricType.SLEEP_STAGE.key, windowStartMs, windowEndMs,
        )
        if (stages.isEmpty()) return 0

        val derived = mutableListOf<MetricReading>()
        stages.groupBy { it.sourceId }.forEach { (sourceId, sourceStages) ->
            Companion.splitSessions(sourceStages.sortedBy { it.timestamp }).forEach { session ->
                SleepDerivations.deriveSleepLatency(session, sourceId)?.let { derived += it }
                SleepDerivations.deriveSleepEfficiency(session, sourceId)?.let { derived += it }
                SleepDerivations.deriveTimeInBed(session, sourceId)?.let { derived += it }
                SleepDerivations.deriveSleepFragmentation(session, sourceId)?.let { derived += it }
                SleepDerivations.deriveWakeAfterSleepOnset(session, sourceId)?.let { derived += it }
                SleepDerivations.deriveSleepScore(session, sourceId)?.let { derived += it }
            }
        }
        if (derived.isEmpty()) return 0
        readingDao.insertAll(derived)
        return derived.size
    }

    suspend fun runForLookback(lookbackDays: Long): Int = runCatching {
        val end = Instant.now()
        val start = end.minus(lookbackDays, ChronoUnit.DAYS)
        run(start.toEpochMilli(), end.toEpochMilli())
    }.getOrDefault(0)

    companion object {
        internal const val SESSION_GAP_MS: Long = 60L * 60L * 1000L

        /**
         * Splits a sorted, single-source stage list into sessions on any
         * gap larger than [SESSION_GAP_MS] between consecutive stages.
         * A real night's stages arrive every few minutes; a gap of an
         * hour or more is structurally a different session — running one
         * derivation across two nights would over-count both TIB and
         * efficiency.
         */
        internal fun splitSessions(stages: List<MetricReading>): List<List<MetricReading>> {
            if (stages.isEmpty()) return emptyList()
            val sessions = mutableListOf<MutableList<MetricReading>>()
            var current = mutableListOf<MetricReading>()
            for (row in stages) {
                val last = current.lastOrNull()
                if (last == null) {
                    current += row
                    continue
                }
                val lastEndMs = last.timestamp + (last.durationSec ?: 0) * 1000L
                if (row.timestamp - lastEndMs > SESSION_GAP_MS) {
                    sessions += current
                    current = mutableListOf(row)
                } else {
                    current += row
                }
            }
            if (current.isNotEmpty()) sessions += current
            return sessions
        }
    }
}
