package com.bios.app.engine

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlin.math.absoluteValue
import kotlin.math.exp

/**
 * Computes current and projected concentration in the body for any
 * substance with a published pharmacokinetic profile (#136).
 *
 * The math layer ([ConcentrationMath]) is pure and DAO-free so the same
 * code is unit-tested without Room and used in dashboards / correlation
 * engines unchanged. The [ConcentrationCalculator] wrapper threads the
 * pharmacokinetic table and the metric DAO together so callers can ask
 * by [MetricType] key directly.
 *
 * **Manifesto check.** "Caffeine concentration at 14:00 was 280 mg" is
 * pure math over the owner's logged events. Pull-side, owner-curated,
 * same shape as a heart-rate trend. The engine does not classify a
 * concentration as too-high / too-low and does not emit alerts.
 *
 * **Out of scope (deliberate).**
 *  - Plasma-concentration (mg/L) conversion when V_d is present — the
 *    amount-in-body number is the right default for the dashboards the
 *    issue scopes; a plasma helper can land alongside its first consumer.
 *  - Pharmacogenomic individualisation. Population-average half-lives
 *    only; per-owner calibration is a future research direction.
 */
class ConcentrationCalculator(
    private val readingDao: MetricReadingDao,
    private val pharmacokinetics: Map<String, Pharmacokinetics> = PharmacokineticsReference.all,
) {

    /**
     * Current amount in the body (mg) for the substance behind [metricKey].
     * Returns null when the metric has no PK profile or maps to multiple
     * substances (e.g. `medication_intake`, which requires per-event
     * substance routing not yet implemented).
     */
    suspend fun currentConcentration(
        metricKey: String,
        atMillis: Long = System.currentTimeMillis(),
    ): Double? {
        val pk = pharmacokineticsFor(metricKey) ?: return null
        val doses = loadDoses(metricKey, atMillis)
        return ConcentrationMath.amountInBodyAt(pk, doses, atMillis)
    }

    /**
     * Concentration curve from [fromMillis] to [toMillis] sampled every
     * [stepMinutes]. Returned points are (timestampMs, amountMg) pairs.
     * Returns an empty list when the metric has no PK profile.
     */
    suspend fun concentrationCurve(
        metricKey: String,
        fromMillis: Long,
        toMillis: Long,
        stepMinutes: Int = DEFAULT_CURVE_STEP_MIN,
    ): List<Pair<Long, Double>> {
        val pk = pharmacokineticsFor(metricKey) ?: return emptyList()
        val doses = loadDoses(metricKey, toMillis)
        return ConcentrationMath.curve(pk, doses, fromMillis, toMillis, stepMinutes)
    }

    /**
     * Milliseconds from [fromMillis] until projected concentration drops
     * below [thresholdMg]. Returns 0 when already below, and null when the
     * substance has no PK profile or the projection never crosses the
     * threshold within [MAX_FORWARD_LOOKAHEAD_MS].
     */
    suspend fun timeUntilBelow(
        metricKey: String,
        thresholdMg: Double,
        fromMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val pk = pharmacokineticsFor(metricKey) ?: return null
        val doses = loadDoses(metricKey, fromMillis)
        return ConcentrationMath.timeUntilBelow(pk, doses, thresholdMg, fromMillis)
    }

    private fun pharmacokineticsFor(metricKey: String): Pharmacokinetics? {
        val substanceKey = PharmacokineticsReference.substanceKeyForMetric(metricKey) ?: return null
        return pharmacokinetics[substanceKey]
    }

    private suspend fun loadDoses(metricKey: String, atMillis: Long): List<ConcentrationMath.DoseEvent> {
        val readings = readingDao.fetch(
            metricType = metricKey,
            startMillis = atMillis - INTAKE_LOOKBACK_MS,
            endMillis = atMillis,
        )
        return readings.map { intakeToDoseEvent(metricKey, it) }
    }

    private fun intakeToDoseEvent(metricKey: String, reading: MetricReading): ConcentrationMath.DoseEvent =
        intakeReadingToDoseEvent(metricKey, reading)

    companion object {
        /** How far back to read intake events. 14 days covers ≥ 5 half-lives
         *  of the longest-t½ substance currently shipped (THC, ~30 h); doses
         *  older than that contribute < 4 % of their initial amount, well
         *  below dashboard rounding. */
        internal const val INTAKE_LOOKBACK_MS = 14L * 24L * 60L * 60L * 1000L
        internal const val GRAMS_TO_MG = 1000.0
        internal const val DEFAULT_CURVE_STEP_MIN = 5
    }
}

/**
 * Convert an intake [reading] to a milligram-denominated dose event for
 * [ConcentrationMath]. Lives at top level so the dashboard aggregator
 * (#138) can reuse the per-key unit normalisation without duplicating
 * the alcohol grams→mg conversion. Keep this in lockstep with
 * `MetricType.*_INTAKE` units.
 */
fun intakeReadingToDoseEvent(
    metricKey: String,
    reading: MetricReading,
): ConcentrationMath.DoseEvent {
    val doseMg = if (metricKey == MetricType.ALCOHOL_INTAKE.key) {
        reading.value * ConcentrationCalculator.GRAMS_TO_MG
    } else {
        reading.value
    }
    return ConcentrationMath.DoseEvent(timestampMs = reading.timestamp, doseMg = doseMg)
}

/**
 * Pure pharmacokinetic math — exposed as an object so non-DAO callers
 * (tests, future correlation engines that already have the dose list)
 * can use the same code as the DAO-backed [ConcentrationCalculator].
 *
 * One-compartment models throughout. Two kinetics regimes:
 *
 *  - **First-order** (most drugs). Elimination dC/dt = -ke·C. With
 *    non-zero absorption half-life, the Bateman equation gives the
 *    amount-in-body form
 *    `A(t) = D·F·ka / (ka − ke) · (e^(−ke·t) − e^(−ka·t))`.
 *    The ka ≈ ke degenerate case ("flip-flop kinetics") falls back to
 *    `A(t) = D·F·ka·t·e^(−ke·t)`, the analytic limit.
 *
 *  - **Zero-order** (alcohol the canonical case). Elimination is a
 *    constant amount per unit time independent of concentration, until
 *    on-board mass hits zero. Computed by an event-walk: between intakes
 *    the on-board amount decays linearly; at each intake the bioavailable
 *    dose is added. Absorption phase is collapsed to instantaneous for
 *    zero-order substances — alcohol's 15-min absorption is small next
 *    to its hour-scale elimination span and a full Bateman/zero-order
 *    hybrid is overkill for the dashboard surfaces #136 scopes.
 */
object ConcentrationMath {

    /** Single intake event normalised to milligrams. */
    data class DoseEvent(val timestampMs: Long, val doseMg: Double)

    /** Amount in body (mg) at [atMillis] given prior dose events. */
    fun amountInBodyAt(
        pk: Pharmacokinetics,
        doses: List<DoseEvent>,
        atMillis: Long,
    ): Double = when (pk.kinetics) {
        EliminationKinetics.FIRST_ORDER -> firstOrderAmount(pk, doses, atMillis)
        EliminationKinetics.ZERO_ORDER -> zeroOrderAmount(pk, doses, atMillis)
    }

    /** Sampled concentration curve over [fromMillis, toMillis]. */
    fun curve(
        pk: Pharmacokinetics,
        doses: List<DoseEvent>,
        fromMillis: Long,
        toMillis: Long,
        stepMinutes: Int,
    ): List<Pair<Long, Double>> {
        require(stepMinutes > 0) { "stepMinutes must be positive" }
        require(toMillis >= fromMillis) { "toMillis must be >= fromMillis" }
        val stepMs = stepMinutes.toLong() * 60_000L
        val out = ArrayList<Pair<Long, Double>>()
        var t = fromMillis
        while (t <= toMillis) {
            out += t to amountInBodyAt(pk, doses, t)
            t += stepMs
        }
        return out
    }

    /**
     * Milliseconds from [fromMillis] until the projected amount drops below
     * [thresholdMg]. Returns 0 when already below. Returns null when the
     * search horizon expires without crossing the threshold — useful for
     * cases like "caffeine never quite reaches zero on a 10-doses-a-day
     * pattern" without forcing the engine into an infinite loop.
     *
     * Implementation: exponentially expanding probes until we find a
     * sub-threshold point, then bisection to refine. Pure, deterministic,
     * no future-dose assumptions (timeline is what the owner has logged so
     * far).
     */
    fun timeUntilBelow(
        pk: Pharmacokinetics,
        doses: List<DoseEvent>,
        thresholdMg: Double,
        fromMillis: Long,
        maxLookaheadMs: Long = MAX_FORWARD_LOOKAHEAD_MS,
    ): Long? {
        val current = amountInBodyAt(pk, doses, fromMillis)
        if (current <= thresholdMg) return 0L
        var probe = PROBE_INITIAL_MS
        var lastAbove = 0L
        while (probe <= maxLookaheadMs) {
            val amount = amountInBodyAt(pk, doses, fromMillis + probe)
            if (amount <= thresholdMg) {
                return bisectThresholdCrossing(pk, doses, thresholdMg, fromMillis, lastAbove, probe)
            }
            lastAbove = probe
            probe = (probe * 2L).coerceAtMost(maxLookaheadMs + 1L)
            if (lastAbove == maxLookaheadMs) break
        }
        return null
    }

    private fun bisectThresholdCrossing(
        pk: Pharmacokinetics,
        doses: List<DoseEvent>,
        thresholdMg: Double,
        fromMillis: Long,
        lowerMs: Long,
        upperMs: Long,
    ): Long {
        var lo = lowerMs
        var hi = upperMs
        repeat(BISECT_STEPS) {
            val mid = lo + (hi - lo) / 2L
            val amount = amountInBodyAt(pk, doses, fromMillis + mid)
            if (amount > thresholdMg) lo = mid else hi = mid
        }
        return hi
    }

    private fun firstOrderAmount(
        pk: Pharmacokinetics,
        doses: List<DoseEvent>,
        atMillis: Long,
    ): Double {
        require(pk.halfLifeMinutes > 0.0) {
            "FIRST_ORDER substance ${pk.substanceKey} must have halfLifeMinutes > 0"
        }
        val ke = LN2 / pk.halfLifeMinutes
        return doses.sumOf { dose ->
            val elapsedMin = (atMillis - dose.timestampMs).toDouble() / 60_000.0
            if (elapsedMin < 0.0) 0.0 else singleFirstOrderAmount(pk, dose.doseMg, elapsedMin, ke)
        }.coerceAtLeast(0.0)
    }

    private fun singleFirstOrderAmount(
        pk: Pharmacokinetics,
        doseMg: Double,
        elapsedMin: Double,
        ke: Double,
    ): Double {
        val effectiveDose = doseMg * pk.bioavailability
        if (pk.absorptionHalfLifeMinutes <= 0.0) {
            // Instant absorption (IV / inhaled-onset).
            return effectiveDose * exp(-ke * elapsedMin)
        }
        val ka = LN2 / pk.absorptionHalfLifeMinutes
        val gap = (ka - ke).absoluteValue
        val scale = (ka + ke) / 2.0
        if (gap < FLIP_FLOP_TOLERANCE * scale) {
            // Degenerate ka ≈ ke: Bateman blows up. Use the analytic limit
            //   A(t) = D × ka × t × e^(−ke·t)
            return effectiveDose * ka * elapsedMin * exp(-ke * elapsedMin)
        }
        return effectiveDose * ka / (ka - ke) *
            (exp(-ke * elapsedMin) - exp(-ka * elapsedMin))
    }

    private fun zeroOrderAmount(
        pk: Pharmacokinetics,
        doses: List<DoseEvent>,
        atMillis: Long,
    ): Double {
        require(pk.zeroOrderRateMgPerMin > 0.0) {
            "ZERO_ORDER substance ${pk.substanceKey} must have zeroOrderRateMgPerMin > 0"
        }
        val sorted = doses
            .filter { it.timestampMs <= atMillis }
            .sortedBy { it.timestampMs }
        if (sorted.isEmpty()) return 0.0
        val ratePerMs = pk.zeroOrderRateMgPerMin / 60_000.0
        var amount = 0.0
        var prevTs = sorted.first().timestampMs
        for (dose in sorted) {
            val elapsedMs = dose.timestampMs - prevTs
            if (elapsedMs > 0L) {
                amount = (amount - ratePerMs * elapsedMs).coerceAtLeast(0.0)
            }
            amount += dose.doseMg * pk.bioavailability
            prevTs = dose.timestampMs
        }
        val tailMs = atMillis - prevTs
        if (tailMs > 0L) {
            amount = (amount - ratePerMs * tailMs).coerceAtLeast(0.0)
        }
        return amount
    }

    private val LN2 = kotlin.math.ln(2.0)
    private const val FLIP_FLOP_TOLERANCE = 1e-6
    internal const val MAX_FORWARD_LOOKAHEAD_MS = 10L * 24L * 60L * 60L * 1000L
    private const val PROBE_INITIAL_MS = 5L * 60L * 1000L
    private const val BISECT_STEPS = 32
}
