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
 * Produces one [SubstanceCard] per known substance. Two modes:
 *
 *  - [DisplayMode.DOSED] — the intake key carries a dose (caffeine,
 *    alcohol). The card renders concentration math: current amount in
 *    body, threshold crossing, 24 h decay curve.
 *  - [DisplayMode.EVENT_ONLY] — the intake key is timestamp-only
 *    (`tobacco_use`, `cannabis_use` from Smokeless). The card renders
 *    the event log: how many in the window, when the last one was,
 *    which app logged it. No concentration math because the engine
 *    needs a dose to be honest. Documented explicitly in #138 as the
 *    interim shape until a dose-bearing companion key ships.
 *
 * Pure so the screen layer is a thin renderer — every empty-state /
 * threshold / window edge runs under JUnit without Compose or Room.
 *
 * **Window semantics.** Intakes older than `historicalWindowMs` are
 * dropped before the curve sample and the recent-doses list; the
 * engine math still reads the full prior history for the
 * concentration computation in DOSED mode.
 */
object ActiveSubstancesAggregator {

    enum class DisplayMode {
        /** Carries a dose; concentration math drives the card. */
        DOSED,

        /** Timestamp-only event marker; the card renders the event log. */
        EVENT_ONLY,
    }

    /**
     * Per-substance dashboard tile.
     *
     *  - In [DisplayMode.DOSED]: [pk], [currentAmountMg], [curve], and
     *    optionally [thresholdMg]/[timeUntilBelowThresholdMs] populate.
     *  - In [DisplayMode.EVENT_ONLY]: [pk] is null, [currentAmountMg]
     *    is null, [curve] is empty, [thresholdMg] is null. The card
     *    surfaces `recentDoses` (one entry per logged event) plus the
     *    last-event metadata.
     *
     * [lastDoseTimestampMs] / [lastDoseLabel] are null when nothing
     * landed inside the window — the UI uses that to distinguish *"zero
     * on board"* from *"unknown"*.
     */
    data class SubstanceCard(
        val metricKey: String,
        val displayMode: DisplayMode,
        val pk: Pharmacokinetics?,
        val currentAmountMg: Double?,
        val lastDoseTimestampMs: Long?,
        val lastDoseLabel: String?,
        val thresholdMg: Double?,
        val timeUntilBelowThresholdMs: Long?,
        val curve: List<Pair<Long, Double>>,
        val recentDoses: List<DoseDisplay>,
    ) {
        val hasRecentIntake: Boolean get() = lastDoseTimestampMs != null
    }

    /** A single intake row as the dashboard renders it. For event-only
     *  substances [doseMg] is null since no dose is recorded. */
    data class DoseDisplay(
        val timestampMs: Long,
        val doseMg: Double?,
        val sourceLabel: String,
    )

    /**
     * Compute one tile per known substance (dosed + event-only).
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
        val dosedCards = PharmacokineticsReference.all.values.mapNotNull { pk ->
            val metricKey = metricKeyForSubstance(pk.substanceKey) ?: return@mapNotNull null
            dosedCardFor(
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
        val eventCards = EVENT_ONLY_METRIC_KEYS.map { metricKey ->
            eventOnlyCardFor(
                metricKey = metricKey,
                intakes = intakes,
                sourceLabels = sourceLabels,
                nowMs = nowMs,
                windowStart = windowStart,
            )
        }
        return dosedCards + eventCards
    }

    private fun dosedCardFor(
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
            displayMode = DisplayMode.DOSED,
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

    private fun eventOnlyCardFor(
        metricKey: String,
        intakes: List<MetricReading>,
        sourceLabels: Map<String, String>,
        nowMs: Long,
        windowStart: Long,
    ): SubstanceCard {
        val priorEvents = intakes
            .asSequence()
            .filter { it.metricType == metricKey }
            .filter { it.timestamp <= nowMs }
            .sortedBy { it.timestamp }
            .toList()
        val last = priorEvents.lastOrNull()
        val recent = priorEvents
            .filter { it.timestamp >= windowStart }
            .map {
                DoseDisplay(
                    timestampMs = it.timestamp,
                    doseMg = null,
                    sourceLabel = sourceLabels[it.sourceId] ?: UNKNOWN_SOURCE_LABEL,
                )
            }
        return SubstanceCard(
            metricKey = metricKey,
            displayMode = DisplayMode.EVENT_ONLY,
            pk = null,
            currentAmountMg = null,
            lastDoseTimestampMs = last?.timestamp,
            lastDoseLabel = last?.sourceId?.let { sourceLabels[it] ?: UNKNOWN_SOURCE_LABEL },
            thresholdMg = null,
            timeUntilBelowThresholdMs = null,
            curve = emptyList(),
            recentDoses = recent,
        )
    }

    /**
     * Reverse of [PharmacokineticsReference.substanceKeyForMetric] for
     * dosed substances. Multi-metric substances (the future
     * medication_intake → many drugs) need per-event routing and are
     * deliberately absent here — the dashboard only renders deterministic
     * tiles.
     */
    private fun metricKeyForSubstance(substanceKey: String): String? = when (substanceKey) {
        "caffeine" -> MetricType.CAFFEINE_INTAKE.key
        "alcohol" -> MetricType.ALCOHOL_INTAKE.key
        else -> null
    }

    /** Event-marker intake keys Smokeless writes — concentration math is
     *  skipped because no dose ships, but the dashboard still surfaces
     *  the event log so logged intakes are visible (#138). */
    internal val EVENT_ONLY_METRIC_KEYS: List<String> = listOf(
        MetricType.TOBACCO_USE.key,
        MetricType.CANNABIS_USE.key,
    )

    internal const val DEFAULT_WINDOW_MS = 24L * 60L * 60L * 1000L
    internal const val DEFAULT_CURVE_STEP_MIN = 5
    internal const val UNKNOWN_SOURCE_LABEL = "unknown source"
}
