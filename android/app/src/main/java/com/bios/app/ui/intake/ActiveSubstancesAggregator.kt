package com.bios.app.ui.intake

import com.bios.app.engine.ConcentrationMath
import com.bios.app.engine.Pharmacokinetics
import com.bios.app.engine.PharmacokineticsReference
import com.bios.app.engine.intakeReadingToDoseEvent
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Pure aggregator behind the "what's still in your body" dashboard (#138).
 *
 * Reads a flat list of recent intake [MetricReading]s (every `*_INTAKE`
 * key that has data) plus the source-label map and a "now" timestamp,
 * and produces one [SubstanceCard] per known substance: current amount
 * in body, last-dose timestamp + source, projected time-until-below the
 * picked threshold, and a sampled decay curve.
 *
 * Pure so the screen layer has zero state-machine logic — the test
 * harness exercises every empty-state / threshold-crossing / multi-dose
 * branch without spinning up Room. The screen calls this once, renders,
 * recomposes on time-tick.
 *
 * **Window semantics.** Intakes older than `historicalWindowMs` are
 * dropped before the curve sample because the engine math already
 * weights them by elapsed time and a 24 h window keeps the sparkline
 * legible. Owners who want longer lookback get it from the trends tab.
 */
object ActiveSubstancesAggregator {

    /**
     * Per-substance dashboard tile. [pk] is non-null even for empty
     * tiles so the UI can render the unit label and substance name
     * without re-deriving them. [lastDoseTimestampMs] / [lastDoseLabel]
     * are null when no intake landed inside [historicalWindowMs] — the
     * UI uses that to distinguish "zero on board" from "unknown".
     */
    data class SubstanceCard(
        val metricKey: String,
        val pk: Pharmacokinetics,
        val currentAmountMg: Double,
        val lastDoseTimestampMs: Long?,
        val lastDoseLabel: String?,
        val thresholdMg: Double?,
        val timeUntilBelowThresholdMs: Long?,
        val curve: List<Pair<Long, Double>>,
        val recentDoses: List<DoseDisplay>,
    ) {
        val hasRecentIntake: Boolean get() = lastDoseTimestampMs != null
    }

    /** A single intake row as the dashboard renders it. */
    data class DoseDisplay(
        val timestampMs: Long,
        val doseMg: Double,
        val sourceLabel: String,
    )

    /**
     * Compute one tile per [PharmacokineticsReference] substance.
     *
     * @param intakes every intake reading the screen wants to consider —
     *   the aggregator filters to the keys it knows.
     * @param sourceLabels sourceId → human-readable label.
     * @param nowMs current epoch ms.
     * @param historicalWindowMs how far back to sample the curve + list
     *   recent doses. Defaults to 24 h per the issue's "Last 24h timeline"
     *   ship target.
     * @param curveStepMinutes sparkline resolution. 5 min × 288 samples =
     *   one 24 h curve.
     * @param thresholdOverridesMg owner-picked per-metric-key threshold
     *   overrides; falls back to [Pharmacokinetics.defaultThresholdMg].
     */
    fun compute(
        intakes: List<MetricReading>,
        sourceLabels: Map<String, String>,
        nowMs: Long = System.currentTimeMillis(),
        historicalWindowMs: Long = DEFAULT_WINDOW_MS,
        curveStepMinutes: Int = DEFAULT_CURVE_STEP_MIN,
        thresholdOverridesMg: Map<String, Double> = emptyMap(),
    ): List<SubstanceCard> {
        val windowStart = nowMs - historicalWindowMs
        return PharmacokineticsReference.all.values
            .mapNotNull { pk ->
                val metricKey = metricKeyForSubstance(pk.substanceKey) ?: return@mapNotNull null
                cardFor(
                    metricKey = metricKey,
                    pk = pk,
                    intakes = intakes,
                    sourceLabels = sourceLabels,
                    nowMs = nowMs,
                    windowStart = windowStart,
                    curveStepMinutes = curveStepMinutes,
                    thresholdMg = thresholdOverridesMg[metricKey] ?: pk.defaultThresholdMg,
                )
            }
    }

    private fun cardFor(
        metricKey: String,
        pk: Pharmacokinetics,
        intakes: List<MetricReading>,
        sourceLabels: Map<String, String>,
        nowMs: Long,
        windowStart: Long,
        curveStepMinutes: Int,
        thresholdMg: Double?,
    ): SubstanceCard {
        // Engine math reads the full prior history (a dose from 8 h ago
        // still contributes to current concentration); the window only
        // bounds the curve we draw and the recent-doses list.
        val priorIntakes = intakes
            .asSequence()
            .filter { it.metricType == metricKey }
            .filter { it.timestamp <= nowMs }
            .sortedBy { it.timestamp }
            .toList()
        val doseEvents = priorIntakes.map { intakeReadingToDoseEvent(metricKey, it) }
        val currentAmount = ConcentrationMath.amountInBodyAt(pk, doseEvents, nowMs)
        val curve = ConcentrationMath.curve(
            pk, doseEvents, fromMillis = windowStart, toMillis = nowMs,
            stepMinutes = curveStepMinutes,
        )

        val timeUntilBelow = thresholdMg?.let { thr ->
            ConcentrationMath.timeUntilBelow(pk, doseEvents, thr, fromMillis = nowMs)
        }

        val last = priorIntakes.lastOrNull()
        val recent = priorIntakes
            .filter { it.timestamp >= windowStart }
            .map {
                DoseDisplay(
                    timestampMs = it.timestamp,
                    doseMg = intakeReadingToDoseEvent(metricKey, it).doseMg,
                    sourceLabel = sourceLabels[it.sourceId] ?: UNKNOWN_SOURCE_LABEL,
                )
            }

        return SubstanceCard(
            metricKey = metricKey,
            pk = pk,
            currentAmountMg = currentAmount,
            lastDoseTimestampMs = last?.timestamp,
            lastDoseLabel = last?.sourceId?.let { sourceLabels[it] ?: UNKNOWN_SOURCE_LABEL },
            thresholdMg = thresholdMg,
            timeUntilBelowThresholdMs = timeUntilBelow,
            curve = curve,
            recentDoses = recent,
        )
    }

    /**
     * Reverse of [PharmacokineticsReference.substanceKeyForMetric] for
     * the single-metric substances. Multi-metric substances (the future
     * medication_intake → many drugs) need per-event routing and are
     * deliberately absent here — the dashboard only renders deterministic
     * tiles.
     */
    private fun metricKeyForSubstance(substanceKey: String): String? = when (substanceKey) {
        "caffeine" -> MetricType.CAFFEINE_INTAKE.key
        "alcohol" -> MetricType.ALCOHOL_INTAKE.key
        // Nicotine + THC are produced by Smokeless via tobacco_use /
        // cannabis_use (event-marker keys, no dose). The dashboard cannot
        // compute an honest concentration without a dose, so the cards
        // are left out until a dose-bearing companion key ships.
        else -> null
    }

    internal const val DEFAULT_WINDOW_MS = 24L * 60L * 60L * 1000L
    internal const val DEFAULT_CURVE_STEP_MIN = 5
    internal const val UNKNOWN_SOURCE_LABEL = "unknown source"
}
