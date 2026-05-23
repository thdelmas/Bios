package com.bios.app.alerts

import com.bios.app.model.ElevationBand
import com.bios.contracts.MetricType

/**
 * One individual signal inside a [ConditionPattern]. Each rule pairs a metric
 * with how the detector should read it: a baseline-relative deviation
 * (sigma + direction + minimum duration), an absolute clinical threshold
 * (lab cut-offs, vital-sign hard floors), or an "absence" gate (no events
 * in a window). Extracted from `ConditionPatterns.kt` for file-length
 * hygiene during the #197 environmental-context build-out.
 */
data class SignalRule(
    val metricType: MetricType,
    val direction: DeviationDirection,
    val thresholdSigma: Double,
    val minDurationHours: Int,
    val weight: Double,
    val source: ThresholdSource = ThresholdSource.ENGINEERING,
    val citation: String = "",
    /**
     * If true, the pattern only fires when this rule is active. Lets a pattern
     * gate on the *presence* (or [DeviationDirection.ABSENT]: absence) of one
     * signal before any supporting signals count — e.g. cessation-recovery
     * requires tobacco-use absence before reading positive cardiovascular
     * trends as recovery.
     */
    val required: Boolean = false,
    /**
     * Absolute clinical threshold: rule fires when the metric's latest reading
     * is at or above this value, ignoring [direction] / [thresholdSigma] /
     * personal baseline. Used for lab biomarkers where the literature
     * threshold (e.g. hsCRP ≥ 1.0 mg/L) is a hard clinical cutoff, not a
     * baseline-relative deviation. When non-null, the evaluator reads the
     * latest reading via `fetchLatest` (no SENSOR filter, no time window) so
     * self-reported lab values qualify.
     */
    val absoluteAbove: Double? = null,
    /** Mirror of [absoluteAbove] for "at or below" thresholds. */
    val absoluteBelow: Double? = null,
    /**
     * For multi-reading absolute checks: when > 0, the rule fires only if
     * the **median** of all readings within the last [absoluteWindowHours]
     * crosses the cutoff *and* there are at least [absoluteMinReadings]
     * readings in that window. Used for cuff-measured metrics like blood
     * pressure where a single reading carries white-coat / motion noise —
     * median across multiple home readings is the established home-BP
     * convention (ESH 2023 ABPM guidelines).
     *
     * Default 0 = single-latest-reading semantics (the original biomarker
     * shape: lab values are sparse, dated, and intentional).
     */
    val absoluteWindowHours: Int = 0,
    /**
     * Minimum readings required in the [absoluteWindowHours] window before
     * the median check fires. Default 1 — only meaningful when
     * [absoluteWindowHours] > 0. Three is the standard floor for cuff-based
     * home BP averaging.
     */
    val absoluteMinReadings: Int = 1,
    /**
     * Per-[ElevationBand] override of [absoluteBelow] (#197). Modulates the
     * hard cutoff when the owner's resolved environmental elevation places
     * them in one of the listed bands; bands not present in the map fall
     * back to the base [absoluteBelow]. The SOWA_RIGPA audit (§2.1)
     * documented that `BLOOD_OXYGEN ≤ 85 %` is correct at sea level but
     * fires false-positive URGENT alerts for acclimatised owners at
     * 3500–4500 m whose true baseline sits in the high 80s. Reference:
     * West JB (2010) *High Altitude Medicine and Physiology*.
     *
     * Only meaningful for rules using [absoluteBelow]; the
     * [com.bios.app.engine.AnomalyDetector] consults
     * [com.bios.app.config.EnvironmentalContextProvider] when this map is
     * non-empty.
     */
    val elevationAdjustedBelow: Map<ElevationBand, Double> = emptyMap(),
    /**
     * #205 (SURGICAL_POV §2.1): when true, the rule evaluates against the frozen
     * pre-op [com.bios.app.physiology.PerioperativeBaseline] instead of the
     * rolling 14-day personal baseline. Lets post-op-windowed patterns detect
     * re-elevation after normalisation (the NHSN SSI shape).
     */
    val useFrozenBaseline: Boolean = false,
    /**
     * #268 (NEUROLOGY_POV §2.1): minimum [com.bios.app.model.MetricReading.durationSec]
     * a row must carry to count toward this rule. Used by
     * `status_epilepticus_convulsive` to enforce the ILAE 2015 t1 = 5 min
     * convulsive-SE cutoff: a 2-minute SEIZURE_EVENT is silently dropped,
     * a 5+ minute event passes through to the absolute-threshold check.
     *
     * Only meaningful for absolute-rule rows ([absoluteWindowHours] > 0);
     * the detector swaps to the row-level DAO fetch when this field is
     * non-null so it can see the [com.bios.app.model.MetricReading.durationSec]
     * column. Default null = no duration filter.
     */
    val durationAtLeastSec: Int? = null,
) {
    /** True when this rule is evaluated as an absolute clinical threshold. */
    val isAbsolute: Boolean get() = absoluteAbove != null || absoluteBelow != null

    /**
     * Resolve the [absoluteBelow] cutoff after applying the elevation
     * adjustment, if any. Returns the base [absoluteBelow] when no
     * adjustment applies. The detector reads this instead of the raw field
     * once it has a resolved [ElevationBand].
     */
    fun resolvedAbsoluteBelow(band: ElevationBand?): Double? {
        if (band == null) return absoluteBelow
        return elevationAdjustedBelow[band] ?: absoluteBelow
    }
}

enum class DeviationDirection {
    ABOVE, BELOW, IRREGULAR,
    /**
     * No readings of this metric in the [SignalRule.minDurationHours] window.
     * Intended for EVENT-unit metrics where "no event for N hours" is the signal
     * (e.g. tobacco-use absence as a cessation marker). Doesn't read a baseline;
     * [SignalRule.thresholdSigma] is unused for this direction.
     */
    ABSENT,
}

enum class ThresholdSource {
    /** Set by engineering judgment — not yet validated against literature. */
    ENGINEERING,
    /** Derived from peer-reviewed research with specific citation. */
    LITERATURE,
    /** Learned from the owner's own historical data (future). */
    PERSONAL,
    /** Signal computed by a companion app (W2F) and injected via ContentProvider. */
    COMPANION
}
