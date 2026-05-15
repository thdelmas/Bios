package com.bios.app.engine

import com.bios.app.data.BiosDatabase
import com.bios.app.model.*
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import com.bios.contracts.MetricDomain
import java.time.LocalDate
import java.time.ZoneId

/**
 * Computes personal baselines from historical metric readings using rolling statistics.
 */
class BaselineEngine(
    private val db: BiosDatabase,
    private val latencyTracker: DetectionLatencyTracker? = null
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
            )

            for (metricType in metricsToBaseline) {
                computeBaseline(metricType)
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

        // Baselines only meaningful on direct sensor data — self-reports are
        // perception not physiology, and DERIVED is already smoothed.
        // docs/SELF_REPORTED_DATA_HOME.md decision 3.
        val values = readingDao.fetchValues(
            metricType.key, startMillis, endMillis, ReadingKind.SENSOR.name
        )
        if (values.size < MIN_SAMPLES_FOR_BASELINE) return

        val stats = Stats.compute(values)

        // Compute trend via linear regression on daily means
        val dailyMeans = computeDailyMeans(metricType, startMillis, endMillis)
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
        endMillis: Long
    ): List<Double> {
        val dayMillis = 24L * 3600 * 1000
        return readingDao.fetchBucketedMeans(
            metricType.key, startMillis, endMillis, dayMillis, ReadingKind.SENSOR.name
        )
    }

    private fun computeTrend(dailyMeans: List<Double>): Pair<TrendDirection, Double> {
        if (dailyMeans.size < 3) return Pair(TrendDirection.STABLE, 0.0)

        val n = dailyMeans.size.toDouble()
        val xs = (0 until dailyMeans.size).map { it.toDouble() }
        val sumX = xs.sum()
        val sumY = dailyMeans.sum()
        val sumXY = xs.zip(dailyMeans).sumOf { (x, y) -> x * y }
        val sumX2 = xs.sumOf { it * it }

        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return Pair(TrendDirection.STABLE, 0.0)

        val slope = (n * sumXY - sumX * sumY) / denominator

        val mean = sumY / n
        if (mean == 0.0) return Pair(TrendDirection.STABLE, slope)

        val normalizedSlope = slope / mean

        val direction = when {
            normalizedSlope > 0.02 -> TrendDirection.RISING
            normalizedSlope < -0.02 -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }

        return Pair(direction, slope)
    }
}
