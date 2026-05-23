package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.OwnerCondition
import com.bios.app.physiology.PhysiologyState

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
    /** Drug-class gate (#201): when non-empty, fires only if owner's declared cancer-therapy drug class is in this set. */
    val requiredDrugClasses: Set<com.bios.app.physiology.CancerTherapyDrugClass> = emptySet(),
) {
    /** True when the pattern passes all five gates (state both axes, region, owner conditions, drug class). */
    fun appliesIn(
        state: PhysiologyState,
        regionConfig: com.bios.app.config.RegionConfig? = null,
        ownerConditions: Set<OwnerCondition> = emptySet(),
        drugClass: com.bios.app.physiology.CancerTherapyDrugClass = com.bios.app.physiology.CancerTherapyDrugClass.NONE,
    ): Boolean =
        state !in excludedStates && (requiredStates.isEmpty() || state in requiredStates) &&
            (!requiresTropicalRegion || regionConfig?.tropicalDiseaseRelevant == true) &&
            ownerConditions.containsAll(requiredOwnerConditions) &&
            (requiredDrugClasses.isEmpty() || drugClass in requiredDrugClasses)
}

// SignalRule, DeviationDirection, and ThresholdSource live in SignalRule.kt
// (extracted during the #197 environmental-context build-out to keep this
// file under the 500-line ceiling).

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
            TropicalDiseasePatterns.all + EnvironmentalPatterns.all +
            PerioperativePatterns.all + CardioOncologyPatterns.all +
            GrowthAndCompositionPatterns.all + NeurologyUrgentPatterns.all +
            AdvancedCardiologyPatterns.all
    }
}
