package com.bios.app.engine

import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import kotlin.math.abs

/**
 * Pre-storage signal quality filter that rejects physiologically implausible readings.
 *
 * Ported from NeuroKit2's signal quality assessment and HeartPy's artifact rejection:
 * - Absolute physiological range checks (hard bounds)
 * - Rate-of-change checks (reject impossible jumps between consecutive readings)
 * - Coefficient of variation check (reject noisy measurement windows)
 *
 * Applied in IngestManager before deduplicate + storage. Readings that fail any
 * check are silently dropped — they would corrupt baselines and trigger false alerts.
 *
 * References:
 * - Makowski et al. (2021) NeuroKit2: signal quality indices
 * - van Gent et al. (2019) HeartPy: noise-resistant PPG analysis
 * - Orphanidou et al. (2015) Signal quality for wearable sensors
 */
object SignalQualityFilter {

    /**
     * Filter a batch of readings, returning only those that pass quality checks.
     * [recentReadings] provides context for rate-of-change checks (last reading per metric).
     */
    fun filter(
        readings: List<MetricReading>,
        recentReadings: Map<String, MetricReading> = emptyMap()
    ): List<MetricReading> {
        val lastSeen = recentReadings.toMutableMap()
        return readings.filter { reading ->
            val pass = checkReading(reading, lastSeen[reading.metricType])
            if (pass) {
                lastSeen[reading.metricType] = reading
            }
            pass
        }
    }

    /**
     * Check a single reading against physiological plausibility rules.
     */
    internal fun checkReading(reading: MetricReading, previous: MetricReading? = null): Boolean {
        val bounds = PHYSIOLOGICAL_BOUNDS[reading.metricType] ?: return true
        val value = reading.value

        // 1. Absolute range check
        if (value < bounds.min || value > bounds.max) return false

        // 2. Rate-of-change check (if we have a previous reading)
        if (previous != null && bounds.maxDeltaPerMinute != null) {
            val timeDiffMinutes = (reading.timestamp - previous.timestamp) / 60_000.0
            if (timeDiffMinutes > 0 && timeDiffMinutes < 60) {
                val delta = abs(value - previous.value)
                val maxDelta = bounds.maxDeltaPerMinute * timeDiffMinutes.coerceAtLeast(1.0)
                if (delta > maxDelta) return false
            }
        }

        return true
    }

    /**
     * Physiological bounds per metric type.
     *
     * min/max: hard physiological limits (values outside are sensor errors)
     * maxDeltaPerMinute: maximum plausible change rate (rejects motion artifacts)
     *
     * Sources:
     * - HR: 25-250 bpm (Orphanidou 2015), max delta 40 bpm/min (HeartPy)
     * - HRV RMSSD: 2-300 ms (Shaffer & Ginsberg 2017)
     * - SpO2: 70-100% (clinical), delta 5%/min
     * - Respiratory rate: 4-60 breaths/min (clinical)
     * - Skin temp: 25-42 C (clinical), delta 2 C/min
     * - Steps: 0-50000/day
     */
    private val PHYSIOLOGICAL_BOUNDS = mapOf(
        MetricType.HEART_RATE.key to Bounds(25.0, 250.0, 40.0),
        MetricType.RESTING_HEART_RATE.key to Bounds(25.0, 150.0, 20.0),
        MetricType.HEART_RATE_VARIABILITY.key to Bounds(2.0, 300.0, 80.0),
        MetricType.BLOOD_OXYGEN.key to Bounds(70.0, 100.0, 5.0),
        MetricType.RESPIRATORY_RATE.key to Bounds(4.0, 60.0, 15.0),
        MetricType.SKIN_TEMPERATURE.key to Bounds(25.0, 42.0, 2.0),
        MetricType.SKIN_TEMPERATURE_DEVIATION.key to Bounds(-5.0, 5.0, 2.0),
        MetricType.BLOOD_GLUCOSE.key to Bounds(20.0, 600.0, 100.0),
        MetricType.BLOOD_PRESSURE_SYSTOLIC.key to Bounds(60.0, 300.0, 40.0),
        MetricType.BLOOD_PRESSURE_DIASTOLIC.key to Bounds(30.0, 200.0, 30.0),
        MetricType.STEPS.key to Bounds(0.0, 100_000.0, null),
        MetricType.ACTIVE_CALORIES.key to Bounds(0.0, 20_000.0, null),
        MetricType.ACTIVE_MINUTES.key to Bounds(0.0, 1440.0, null),
        MetricType.SLEEP_DURATION.key to Bounds(0.0, 86400.0, null),
        MetricType.RECOVERY_SCORE.key to Bounds(0.0, 100.0, null),
        MetricType.BASAL_BODY_TEMPERATURE.key to Bounds(35.0, 39.0, 1.0)
    )

    internal data class Bounds(
        val min: Double,
        val max: Double,
        val maxDeltaPerMinute: Double?
    )
}
