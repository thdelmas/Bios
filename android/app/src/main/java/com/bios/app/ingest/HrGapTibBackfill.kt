package com.bios.app.ingest

import com.bios.app.data.dao.DataSourceDao
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.engine.HrGapTibInference
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Walks recent `HEART_RATE` readings, finds multi-hour gaps that overlap
 * the nocturnal anchor, and persists each gap as a `TIME_IN_BED` reading
 * attributed to the synthetic `BIOS_INFERRED` source.
 *
 * Idempotent: the unique index on `(sourceId, metricType, timestamp)`
 * collapses re-runs to in-place updates, so this can run on every sync
 * or be triggered by an owner-initiated backfill button without
 * producing parallel duplicate rows.
 *
 * Companion `sleep_duration` rows in the same window corroborate a gap
 * — when found, the emitted reading is MEDIUM confidence; otherwise LOW.
 * The math itself lives in [HrGapTibInference]; this class is the
 * DB-aware orchestration layer.
 */
class HrGapTibBackfill(
    private val readingDao: MetricReadingDao,
    private val sourceDao: DataSourceDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * Run the inference over [windowStartMs, windowEndMs] and persist any
     * resulting TIB candidates. Returns the number of rows written
     * (after REPLACE collapse — equals the candidate count for fresh
     * runs, zero for unchanged re-runs only if every candidate is
     * byte-identical to its stored row).
     */
    suspend fun run(windowStartMs: Long, windowEndMs: Long): Int {
        if (windowEndMs <= windowStartMs) return 0
        val hr = readingDao.fetch(MetricType.HEART_RATE.key, windowStartMs, windowEndMs)
        if (hr.size < 2) return 0
        val companionSleep = readingDao
            .fetch(MetricType.SLEEP_DURATION.key, windowStartMs, windowEndMs)
            .filter { it.durationSec != null && it.durationSec > 0 }
            .map { HrGapTibInference.SleepWindow(it.timestamp, it.durationSec!!) }
        val candidates = HrGapTibInference.infer(
            hrSamples = hr.map { HrGapTibInference.HrSample(it.timestamp) },
            companionSleepWindows = companionSleep,
            zone = zone,
        )
        if (candidates.isEmpty()) return 0
        val sourceId = getOrCreateInferredSource()
        val rows = candidates.map { c ->
            MetricReading(
                metricType = MetricType.TIME_IN_BED.key,
                value = c.durationSec.toDouble(),
                timestamp = c.startMs,
                durationSec = c.durationSec,
                sourceId = sourceId,
                confidence = c.confidence.level,
            )
        }
        readingDao.insertAll(rows)
        return rows.size
    }

    /** Convenience wrapper: runs over a window ending now and spanning the
     *  past [lookbackDays] days. Failures are swallowed so a stale HR
     *  fetch can't kill the surrounding sync. */
    suspend fun runForLookback(lookbackDays: Long): Int = runCatching {
        val end = Instant.now()
        val start = end.minus(lookbackDays, ChronoUnit.DAYS)
        run(start.toEpochMilli(), end.toEpochMilli())
    }.getOrDefault(0)

    private suspend fun getOrCreateInferredSource(): String {
        val existing = sourceDao.findByType(SourceType.BIOS_INFERRED.key)
        if (existing != null) return existing.id
        val source = DataSource(
            sourceType = SourceType.BIOS_INFERRED.key,
            deviceName = SOURCE_DEVICE_NAME,
            sensorType = SensorType.DERIVED.name,
        )
        sourceDao.insert(source)
        return source.id
    }

    companion object {
        // Surfaced in the per-metric sources screen — owner-readable hint
        // that this isn't a real device.
        private const val SOURCE_DEVICE_NAME = "HR-gap inference"
    }
}
