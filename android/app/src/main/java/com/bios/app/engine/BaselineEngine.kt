package com.bios.app.engine

import com.bios.app.data.BiosDatabase
import com.bios.app.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Computes personal baselines from historical metric readings using rolling statistics.
 */
class BaselineEngine(private val db: BiosDatabase) {

    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val aggregateDao = db.computedAggregateDao()

    companion object {
        const val MINIMUM_DATA_DAYS = 7
        const val DEFAULT_WINDOW_DAYS = 14
    }

    // MARK: - Compute all baselines

    suspend fun computeAllBaselines() {
        val metricsToBaseline = listOf(
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.RESTING_HEART_RATE,
            MetricType.BLOOD_OXYGEN,
            MetricType.RESPIRATORY_RATE,
            MetricType.SKIN_TEMPERATURE_DEVIATION,
            MetricType.STEPS,
            MetricType.ACTIVE_MINUTES,
            MetricType.SLEEP_STAGE
        )

        for (metricType in metricsToBaseline) {
            computeBaseline(metricType)
        }
    }

    suspend fun computeBaseline(
        metricType: MetricType,
        context: BaselineContext = BaselineContext.ALL,
        windowDays: Int = DEFAULT_WINDOW_DAYS
    ) {
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - windowDays.toLong() * 24 * 3600 * 1000

        val values = readingDao.fetchValues(metricType.key, startMillis, endMillis)
        if (values.size < 10) return  // need minimum samples

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
            MetricType.ACTIVE_CALORIES
        )

        for (metricType in metricsToAggregate) {
            val values = readingDao.fetchValues(metricType.key, dayStart, dayEnd)
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
        val dailyMeans = mutableListOf<Double>()
        val zone = ZoneId.systemDefault()
        var current = Instant.ofEpochMilli(startMillis)
            .atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis)
            .atZone(zone).toLocalDate()

        while (!current.isAfter(endDate)) {
            val dayStart = current.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = current.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val values = readingDao.fetchValues(metricType.key, dayStart, dayEnd)

            if (values.isNotEmpty()) {
                dailyMeans.add(values.average())
            }

            current = current.plusDays(1)
        }

        return dailyMeans
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
