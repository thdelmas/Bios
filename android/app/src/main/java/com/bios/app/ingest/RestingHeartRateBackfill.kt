package com.bios.app.ingest

import com.bios.app.data.dao.DataSourceDao
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.engine.RestingHeartRateInference
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Walks recent `HEART_RATE` and `STEPS` readings, derives one
 * `RESTING_HEART_RATE` per local-zone day from quiet windows, and
 * persists results under the synthetic `BIOS_INFERRED` source.
 *
 * Will not overwrite a sensor- or vendor-reported RHR. If any day in the
 * window already has a `RESTING_HEART_RATE` row from a non-`BIOS_INFERRED`
 * source, that day is skipped — Garmin/Whoop/Oura native RHR is treated
 * as more authoritative than a derivation off cross-source HR + STEPS.
 *
 * Idempotent: re-runs collapse to in-place updates via the unique index
 * on `(sourceId, metricType, timestamp)`. Safe to call on every sync.
 *
 * Math itself lives in [RestingHeartRateInference]; this class is the
 * DB-aware orchestration layer (mirrors [HrGapTibBackfill]).
 */
class RestingHeartRateBackfill(
    private val readingDao: MetricReadingDao,
    private val sourceDao: DataSourceDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun run(windowStartMs: Long, windowEndMs: Long): Int {
        if (windowEndMs <= windowStartMs) return 0
        val hr = readingDao.fetch(MetricType.HEART_RATE.key, windowStartMs, windowEndMs)
        if (hr.size < RestingHeartRateInference.MIN_QUIET_SAMPLES) return 0
        val steps = readingDao.fetch(MetricType.STEPS.key, windowStartMs, windowEndMs)

        val candidates = RestingHeartRateInference.infer(
            hrSamples = hr.map { RestingHeartRateInference.HrSample(it.timestamp, it.value) },
            stepsBuckets = steps.map {
                RestingHeartRateInference.StepsBucket(it.timestamp, it.value, it.durationSec)
            },
            zone = zone,
        )
        if (candidates.isEmpty()) return 0

        val inferredSourceId = getOrCreateInferredSource()
        val existing = readingDao.fetch(
            MetricType.RESTING_HEART_RATE.key, windowStartMs, windowEndMs
        )
        val daysWithExternalRhr = existing
            .filter { it.sourceId != inferredSourceId }
            .map { dayStartMs(it.timestamp) }
            .toSet()

        val rows = candidates
            .filter { it.dayStartMs !in daysWithExternalRhr }
            .map { c ->
                MetricReading(
                    metricType = MetricType.RESTING_HEART_RATE.key,
                    value = c.rhrBpm,
                    timestamp = c.dayStartMs,
                    sourceId = inferredSourceId,
                    confidence = c.confidence.level,
                )
            }
        if (rows.isEmpty()) return 0
        readingDao.insertAll(rows)
        return rows.size
    }

    /** Run over a window ending now and spanning [lookbackDays] back.
     *  Failures are swallowed so a transient fetch error can't kill the
     *  surrounding sync. */
    suspend fun runForLookback(lookbackDays: Long): Int = runCatching {
        val end = Instant.now()
        val start = end.minus(lookbackDays, ChronoUnit.DAYS)
        run(start.toEpochMilli(), end.toEpochMilli())
    }.getOrDefault(0)

    private fun dayStartMs(timestampMs: Long): Long =
        Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

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
        private const val SOURCE_DEVICE_NAME = "Bios derivation"
    }
}
