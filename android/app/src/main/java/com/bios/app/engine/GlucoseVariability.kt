package com.bios.app.engine

import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * Pure-function derivations over a window of BLOOD_GLUCOSE readings (#28).
 *
 * Bios ingests raw CGM samples through [com.bios.app.ingest.DexcomApiAdapter]
 * (or Health Connect's blood-glucose feed) at the device's native cadence —
 * typically every 5 minutes for Dexcom, less frequent for finger-stick
 * imports. The three derivations here describe how variable that stream is
 * over the window the caller passes in. None of them compares a value with
 * a target range:
 *
 *  - **GLUCOSE_CV** — coefficient of variation (SD / mean × 100), in
 *    percent.
 *  - **GLUCOSE_MAGE** — Mean Amplitude of Glycemic Excursions, in mg/dL,
 *    using Service et al.'s 1970 algorithm: among the peak↔nadir transitions
 *    in the series, keep those whose amplitude exceeds 1 SD and average
 *    them.
 *  - **GLUCOSE_PEAK_24H** — max value in the window, in mg/dL.
 *
 * **Window semantics.** Each function takes the readings already pulled into
 * the sync batch (typically the last 24 h in `IngestManager.syncRecentData`)
 * and emits one reading per call, anchored at the first sample's timestamp
 * and tagged with `durationSec` spanning to the last sample. The caller is
 * responsible for the window — these functions don't query the DAO. That
 * keeps the derivations pure and testable without Room.
 *
 * **Minimum-data threshold.** All three return `null` below
 * [MIN_SAMPLES_FOR_VARIABILITY] readings — under ~8 hours of 5-min CGM
 * coverage, the statistics are too sparse to be informative and would
 * mostly add baseline noise.
 *
 * Confidence and sourceId are inherited from the input rows, mirroring
 * the [SleepDerivations] convention.
 */
object GlucoseVariability {

    /** Coefficient of variation = SD / mean × 100. Returns null when the
     *  window is too sparse or mean ≤ 0 (defensive against bad data). */
    fun deriveCv(readings: List<MetricReading>, sourceId: String): MetricReading? {
        val series = glucoseSeries(readings) ?: return null
        val mean = series.sumOf { it.value } / series.size
        if (mean <= 0.0) return null
        val variance = series.sumOf { (it.value - mean) * (it.value - mean) } / series.size
        val cv = sqrt(variance) / mean * 100.0
        return derivedReading(MetricType.GLUCOSE_CV, cv, series, sourceId)
    }

    /** Highest mg/dL value in the window. */
    fun derivePeak(readings: List<MetricReading>, sourceId: String): MetricReading? {
        val series = glucoseSeries(readings) ?: return null
        val peak = series.maxOf { it.value }
        return derivedReading(MetricType.GLUCOSE_PEAK_24H, peak, series, sourceId)
    }

    /**
     * Service-1970 MAGE: average amplitude of peak↔nadir transitions that
     * exceed 1 SD of the glucose series. Walks the series once, marking
     * turning points (a sample is a turn when its slope sign flips relative
     * to the previous run), then takes |Δ| between adjacent turns and
     * averages the ones above the SD threshold.
     *
     * Returns null when the window has fewer than two turning points whose
     * excursion clears the SD threshold — a flat-line trace has no MAGE
     * to report and emitting a zero would mis-rank truly stable days
     * against days where the algorithm just didn't fire.
     */
    fun deriveMage(readings: List<MetricReading>, sourceId: String): MetricReading? {
        val series = glucoseSeries(readings) ?: return null
        val values = series.map { it.value }
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        if (sd <= 0.0) return null

        // Mark turning points. A sample is a turn when the running direction
        // (rising vs. falling) reverses; the first and last samples bookend
        // the series and are always included so a monotonic window still
        // yields one excursion.
        val turns = mutableListOf<Double>()
        turns += values.first()
        var direction = 0  // -1 falling, 0 unknown, +1 rising
        for (i in 1 until values.size) {
            val delta = values[i] - values[i - 1]
            val sign = when {
                delta > 0 -> 1
                delta < 0 -> -1
                else -> direction
            }
            if (direction != 0 && sign != 0 && sign != direction) {
                turns += values[i - 1]
            }
            if (sign != 0) direction = sign
        }
        turns += values.last()
        if (turns.size < 2) return null

        val excursions = (1 until turns.size)
            .map { (turns[it] - turns[it - 1]).absoluteValue }
            .filter { it > sd }
        if (excursions.isEmpty()) return null

        val mage = excursions.average()
        return derivedReading(MetricType.GLUCOSE_MAGE, mage, series, sourceId)
    }

    /** Filter to BLOOD_GLUCOSE, sort, and return only when the window has
     *  enough samples to compute meaningful variability statistics. */
    private fun glucoseSeries(readings: List<MetricReading>): List<MetricReading>? {
        val series = readings
            .filter { it.metricType == MetricType.BLOOD_GLUCOSE.key }
            .sortedBy { it.timestamp }
        if (series.size < MIN_SAMPLES_FOR_VARIABILITY) return null
        return series
    }

    private fun derivedReading(
        type: MetricType,
        value: Double,
        series: List<MetricReading>,
        sourceId: String,
    ): MetricReading = MetricReading(
        metricType = type.key,
        value = value,
        timestamp = series.first().timestamp,
        durationSec = ((series.last().timestamp - series.first().timestamp) / 1000L).toInt(),
        sourceId = sourceId,
        confidence = series.first().confidence,
    )

    /** ~8 hours of 5-min CGM coverage. Below this the stats are too sparse
     *  to be informative — see "Minimum-data threshold" in the class kdoc. */
    internal const val MIN_SAMPLES_FOR_VARIABILITY = 96
}
