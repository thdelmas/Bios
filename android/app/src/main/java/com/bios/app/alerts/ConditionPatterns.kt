package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.OwnerCondition
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

data class ConditionPattern(
    val id: String,
    val title: String,
    val category: ConditionCategory,
    val signalRules: List<SignalRule>,
    val minActiveSignals: Int,
    val explanation: String,
    val suggestedAction: String?,
    val references: List<String> = emptyList(),
    val earlyDetection: String = "",
    val prevention: String = "",
    val healing: String = "",
    val risks: String = "",
    /**
     * Minimum severity tier — see [AlertTier]. Non-null lifts severity
     * above the classifier's default cap (used by emergency vital-sign
     * patterns).
     */
    val severityFloor: AlertTier? = null,
    /** Negative state gate (#159, audit gap §2.7; #186 frailty): states where this pattern is suppressed. */
    val excludedStates: Set<PhysiologyState> = emptySet(),
    /** Positive state gate (#200, audit gap §2.9): when non-empty, pattern fires only in listed states. */
    val requiredStates: Set<PhysiologyState> = emptySet(),
    /** Region gate (#196, audit gap §2.6): when true, fires only where RegionConfig.tropicalDiseaseRelevant. */
    val requiresTropicalRegion: Boolean = false,
    /** Owner-condition gate (#196): when non-empty, fires only if owner has declared all listed conditions. */
    val requiredOwnerConditions: Set<OwnerCondition> = emptySet(),
) {
    /** True when the pattern passes all four gates (state both axes, region, owner conditions). */
    fun appliesIn(state: PhysiologyState, regionConfig: com.bios.app.config.RegionConfig? = null, ownerConditions: Set<OwnerCondition> = emptySet()): Boolean =
        state !in excludedStates && (requiredStates.isEmpty() || state in requiredStates) &&
            (!requiresTropicalRegion || regionConfig?.tropicalDiseaseRelevant == true) &&
            ownerConditions.containsAll(requiredOwnerConditions)
}

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
     * gate on the *presence* (or [ABSENT]: absence) of one signal before any
     * supporting signals count — e.g. cessation-recovery requires tobacco-use
     * absence before reading positive cardiovascular trends as recovery.
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
) {
    /** True when this rule is evaluated as an absolute clinical threshold. */
    val isAbsolute: Boolean get() = absoluteAbove != null || absoluteBelow != null
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

/**
 * Aggregator for every [ConditionPattern] in the catalogue. Patterns live in
 * per-domain sibling files (BaselineDeviationPatterns, SepsisScreenPattern,
 * AfibRhythmPattern, PregnancyPatterns, HeadachePatterns, …); this object
 * is the single concatenation point.
 *
 * Adding a new pattern: put it in a domain-appropriate file (create one if
 * none fits), expose an `all` list there, then add `+ NewPatterns.all` below.
 */
object ConditionPatterns {

    val all by lazy {
        BaselineDeviationPatterns.all +
            CircadianConditionPattern.all + CompanionConditionPatterns.all +
            BiomarkerConditionPatterns.all + EmergencyVitalPatterns.all +
            HypertensionPatterns.all + SleepApneaPattern.all +
            RespiratoryExacerbationPatterns.all + SepsisScreenPattern.all +
            AfibRhythmPattern.all + AutonomicPatternShiftPattern.all +
            PregnancyPatterns.all + HeadachePatterns.all +
            HeartFailureDecompensationPattern.all + AcuteWindowPatterns.all +
            TropicalDiseasePatterns.all + GrowthAndCompositionPatterns.all
    }
}
