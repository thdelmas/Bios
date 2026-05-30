package com.bios.app.engine

import com.bios.app.data.BiosDatabase
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.*
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import com.bios.contracts.MetricDomain
import java.time.LocalDate
import java.time.ZoneId

/**
 * Computes personal baselines from historical metric readings using rolling statistics.
 *
 * `reproductiveReadingDao` is the optional reading DAO for the isolated
 * [com.bios.app.data.ReproductiveDatabase]. When non-null, reproductive
 * metrics (BBT, CYCLE_PHASE, CYCLE_DAY) get a baseline computed from rows
 * in that DB, with the SENSOR-only filter relaxed — those readings are
 * SELF_REPORTED by design. The resulting [PersonalBaseline] summary is
 * still stored in the main DB (no raw values escape the reproductive DB),
 * which is what [AnomalyDetector] reads when scoring the cycle pattern.
 */
class BaselineEngine(
    private val db: BiosDatabase,
    private val latencyTracker: DetectionLatencyTracker? = null,
    private val reproductiveReadingDao: MetricReadingDao? = null
) {

    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val aggregateDao = db.computedAggregateDao()

    companion object {
        const val MINIMUM_DATA_DAYS = 7
        const val DEFAULT_WINDOW_DAYS = 14
        const val MIN_SAMPLES_FOR_BASELINE = 10
    }

    /**
     * What Bios actually has for a metric, expressed in the same terms the
     * baseline computation uses. Drives UI that explains why a baseline is
     * (or is not) computable, instead of the previous "needs 7 days" stub
     * which lied when the real problem was zero ingested readings.
     */
    data class Coverage(
        val sensorSamplesInWindow: Int,
        val totalSamples: Int,
        val lastTimestamp: Long?,
    ) {
        val hasAnyData: Boolean get() = totalSamples > 0
        val meetsBaselineMinimum: Boolean get() = sensorSamplesInWindow >= MIN_SAMPLES_FOR_BASELINE
    }

    suspend fun coverageFor(
        metricType: MetricType,
        windowDays: Int = DEFAULT_WINDOW_DAYS
    ): Coverage {
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - windowDays.toLong() * 24 * 3600 * 1000
        val sensor = readingDao.countInRange(
            metricType.key, startMillis, endMillis, ReadingKind.SENSOR.name
        )
        val total = readingDao.count(metricType.key)
        val last = readingDao.lastTimestampFor(metricType.key)
        return Coverage(
            sensorSamplesInWindow = sensor,
            totalSamples = total,
            lastTimestamp = last,
        )
    }

    suspend fun coverageForTracked(): Map<MetricType, Coverage> =
        TRACKED_METRICS.associateWith { coverageFor(it) }

    private val TRACKED_METRICS = listOf(
        MetricType.HEART_RATE,
        MetricType.HEART_RATE_VARIABILITY,
        MetricType.LF_HF_RATIO,
        MetricType.RESTING_HEART_RATE,
        MetricType.BLOOD_OXYGEN,
        MetricType.RESPIRATORY_RATE,
        MetricType.SKIN_TEMPERATURE_DEVIATION,
        MetricType.STEPS,
        MetricType.SLEEP_DURATION,
    )

    // MARK: - Compute all baselines

    suspend fun computeAllBaselines() {
        val block: suspend () -> Unit = {
            val metricsToBaseline = listOf(
                MetricType.HEART_RATE,
                MetricType.HEART_RATE_VARIABILITY,
                MetricType.LF_HF_RATIO,
                MetricType.RESTING_HEART_RATE,
                MetricType.BLOOD_OXYGEN,
                MetricType.RESPIRATORY_RATE,
                MetricType.SKIN_TEMPERATURE,
                MetricType.SKIN_TEMPERATURE_DEVIATION,
                MetricType.STEPS,
                MetricType.ACTIVE_MINUTES,
                MetricType.SLEEP_STAGE,
                MetricType.SLEEP_DURATION,
                MetricType.AMBIENT_LIGHT,
                MetricType.AIR_PM25,
                MetricType.AIR_VOC,
                MetricType.AIR_CO2,
                MetricType.BODY_MASS,
                MetricType.BODY_FAT_PCT,
                MetricType.LEAN_MASS,
                MetricType.BODY_WATER_PCT,
                MetricType.BONE_MASS,
            )

            for (metricType in metricsToBaseline) {
                computeBaseline(metricType)
            }
            // VO2 max changes slowly (weeks-to-months timescale). Use a
            // 90-day window so the baseline reflects the genuine fitness
            // trajectory rather than the noisy weekly Garmin/Oura
            // re-estimates the default 14-day window would amplify.
            computeBaseline(MetricType.VO2_MAX, windowDays = 90)

            // Reproductive baselines pull from the isolated reproductive DB
            // when it's available; otherwise this is a no-op (the owner
            // hasn't enabled BBT tracking yet).
            if (reproductiveReadingDao != null) {
                computeBaseline(MetricType.BASAL_BODY_TEMPERATURE)
            }
        }

        if (latencyTracker != null) {
            latencyTracker.track(PipelineStage.BASELINE_COMPUTATION) { block() }
        } else {
            block()
        }
    }

    suspend fun computeBaseline(
        metricType: MetricType,
        context: BaselineContext = BaselineContext.ALL,
        windowDays: Int = DEFAULT_WINDOW_DAYS
    ) {
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - windowDays.toLong() * 24 * 3600 * 1000

        val isReproductive = metricType.domain == MetricDomain.WOMENS_HEALTH
        // Reproductive readings live in the isolated reproductive DB and are
        // SELF_REPORTED by design (manual BBT entry — no sensor adapter
        // writes there). Skip the SENSOR-only filter for those; for every
        // other metric, baselines stay sensor-only per
        // docs/SELF_REPORTED_DATA_HOME.md decision 3.
        val sourceDao = if (isReproductive) reproductiveReadingDao ?: return else readingDao
        val kindFilter = if (isReproductive) null else ReadingKind.SENSOR.name

        val values = sourceDao.fetchValues(
            metricType.key, startMillis, endMillis, kindFilter
        )
        if (values.size < MIN_SAMPLES_FOR_BASELINE) {
            // Drop the stale row only when there is *truly* no data left —
            // i.e. the owner deleted every reading for this metric. A slow
            // week (sensor samples dipping below the in-window minimum)
            // must NOT vanish a baseline that was validly computed from
            // historical data, or owners with sparse-but-real records (sleep
            // tracker offline a few nights, weight measured weekly) lose
            // their baseline on every sync.
            val totalRows = readingDao.count(metricType.key)
            if (totalRows == 0) {
                baselineDao.delete(metricType.key, context.name)
            }
            return
        }

        val stats = Stats.compute(values)

        // Compute trend via linear regression on daily means
        val dailyMeans = computeDailyMeans(metricType, startMillis, endMillis, sourceDao, kindFilter)
        val (trend, slope) = computeTrend(dailyMeans)

        val baseline = PersonalBaseline(
            metricType = metricType.key,
            context = context.name,
            windowDays = windowDays,
            computedAt = System.currentTimeMillis(),
            mean = stats.mean,
            stdDev = stats.stdDev,
            p5 = stats.p5,
            p95 = stats.p95,
            trend = trend.name,
            trendSlope = slope
        )

        baselineDao.upsert(baseline)
    }

    // MARK: - Compute daily aggregates

    suspend fun computeDailyAggregates(date: LocalDate = LocalDate.now()) {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val metricsToAggregate = listOf(
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.RESTING_HEART_RATE,
            MetricType.BLOOD_OXYGEN,
            MetricType.RESPIRATORY_RATE,
            MetricType.STEPS,
            MetricType.ACTIVE_CALORIES,
            MetricType.SLEEP_DURATION
        )

        for (metricType in metricsToAggregate) {
            val values = readingDao.fetchValues(
                metricType.key, dayStart, dayEnd, ReadingKind.SENSOR.name
            )
            if (values.isEmpty()) continue

            val stats = Stats.compute(values)

            val aggregate = ComputedAggregate(
                metricType = metricType.key,
                period = AggregatePeriod.DAY.name,
                periodStart = dayStart,
                mean = stats.mean,
                median = stats.median,
                min = stats.min,
                max = stats.max,
                stdDev = stats.stdDev,
                p5 = stats.p5,
                p95 = stats.p95,
                sampleCount = values.size
            )

            aggregateDao.upsert(aggregate)
        }
    }

    // MARK: - Helpers

    private suspend fun computeDailyMeans(
        metricType: MetricType,
        startMillis: Long,
        endMillis: Long,
        sourceDao: MetricReadingDao = readingDao,
        readingKindFilter: String? = ReadingKind.SENSOR.name
    ): List<Double> {
        val dayMillis = 24L * 3600 * 1000
        return sourceDao.fetchBucketedMeans(
            metricType.key, startMillis, endMillis, dayMillis, readingKindFilter
        )
    }
}
