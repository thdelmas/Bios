package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PaediatricVitalRanges
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * PALS-anchored paediatric emergency vital-sign patterns (#198, audit
 * PAEDIATRICS_POV §2.1 — convergent with Primary Care Gap #7, Emergency /
 * Critical Care PALS, and the Oncology paediatric haem-onc audit).
 *
 * The adult [EmergencyVitalPatterns.tachycardiaCritical] (≥130 bpm) and
 * [EmergencyVitalPatterns.bradycardiaCritical] (≤35 bpm) are wrong for
 * children across both directions: 130 fires on a healthy toddler's
 * resting HR, 35 stays silent on a neonate at 80 in real bradycardia.
 *
 * This file ships one pattern per [PhysiologyState] sub-band, gated by
 * [ConditionPattern.requiredStates] so the AnomalyDetector only evaluates
 * the band that matches the owner's state. The thresholds are anchored to:
 *  - AHA PALS 2020 reference table
 *  - UK Resuscitation Council APLS 2021
 *  - AAP / Bonafide 2013 percentile envelopes
 *  - NICE NG143 (2019) under-5 sepsis recognition thresholds
 *
 * Each pattern preserves the same shape as the adult version: single
 * `required = true` absolute-threshold rule, `severityFloor = URGENT`,
 * `minActiveSignals = 1`. The alert text obeys
 * [com.bios.app.alerts.AlertContentPolicy] — data statements plus
 * professional-referral language. No diagnosis claims, no "your child
 * has X" language. The owner / caregiver reads, evaluates, and decides.
 */
object PaediatricEmergencyVitalPatterns {

    val all by lazy {
        listOf(
            // Tachycardia ceilings, one per band.
            neonateTachycardia, infantTachycardia, toddlerTachycardia,
            preschoolTachycardia, schoolAgeTachycardia, adolescentTachycardia,
            // Bradycardia floors, one per band.
            neonateBradycardia, infantBradycardia, toddlerBradycardia,
            preschoolBradycardia, schoolAgeBradycardia, adolescentBradycardia,
        )
    }

    // -- Tachycardia: one pattern per age band --

    val neonateTachycardia = tachycardiaPattern(
        idSuffix = "neonate",
        bandLabel = "neonate (0–28 days)",
        band = PhysiologyState.NEONATE_0_28D,
        cutoffs = PaediatricVitalRanges.NEONATE,
    )
    val infantTachycardia = tachycardiaPattern(
        idSuffix = "infant",
        bandLabel = "infant (1–12 months)",
        band = PhysiologyState.INFANT_1M_12M,
        cutoffs = PaediatricVitalRanges.INFANT,
    )
    val toddlerTachycardia = tachycardiaPattern(
        idSuffix = "toddler",
        bandLabel = "toddler (1–3 years)",
        band = PhysiologyState.TODDLER_1Y_3Y,
        cutoffs = PaediatricVitalRanges.TODDLER,
    )
    val preschoolTachycardia = tachycardiaPattern(
        idSuffix = "preschool",
        bandLabel = "preschool (3–5 years)",
        band = PhysiologyState.PRESCHOOL_3Y_5Y,
        cutoffs = PaediatricVitalRanges.PRESCHOOL,
    )
    val schoolAgeTachycardia = tachycardiaPattern(
        idSuffix = "school_age",
        bandLabel = "school age (6–12 years)",
        band = PhysiologyState.SCHOOL_AGE_6Y_12Y,
        cutoffs = PaediatricVitalRanges.SCHOOL_AGE,
    )
    val adolescentTachycardia = tachycardiaPattern(
        idSuffix = "adolescent",
        bandLabel = "adolescent (13–18 years)",
        band = PhysiologyState.ADOLESCENT_13Y_18Y,
        cutoffs = PaediatricVitalRanges.ADOLESCENT,
    )

    // -- Bradycardia: one pattern per age band --

    val neonateBradycardia = bradycardiaPattern(
        idSuffix = "neonate",
        bandLabel = "neonate (0–28 days)",
        band = PhysiologyState.NEONATE_0_28D,
        cutoffs = PaediatricVitalRanges.NEONATE,
    )
    val infantBradycardia = bradycardiaPattern(
        idSuffix = "infant",
        bandLabel = "infant (1–12 months)",
        band = PhysiologyState.INFANT_1M_12M,
        cutoffs = PaediatricVitalRanges.INFANT,
    )
    val toddlerBradycardia = bradycardiaPattern(
        idSuffix = "toddler",
        bandLabel = "toddler (1–3 years)",
        band = PhysiologyState.TODDLER_1Y_3Y,
        cutoffs = PaediatricVitalRanges.TODDLER,
    )
    val preschoolBradycardia = bradycardiaPattern(
        idSuffix = "preschool",
        bandLabel = "preschool (3–5 years)",
        band = PhysiologyState.PRESCHOOL_3Y_5Y,
        cutoffs = PaediatricVitalRanges.PRESCHOOL,
    )
    val schoolAgeBradycardia = bradycardiaPattern(
        idSuffix = "school_age",
        bandLabel = "school age (6–12 years)",
        band = PhysiologyState.SCHOOL_AGE_6Y_12Y,
        cutoffs = PaediatricVitalRanges.SCHOOL_AGE,
    )
    val adolescentBradycardia = bradycardiaPattern(
        idSuffix = "adolescent",
        bandLabel = "adolescent (13–18 years)",
        band = PhysiologyState.ADOLESCENT_13Y_18Y,
        cutoffs = PaediatricVitalRanges.ADOLESCENT,
    )

    // -- Builders --

    private fun tachycardiaPattern(
        idSuffix: String,
        bandLabel: String,
        band: PhysiologyState,
        cutoffs: PaediatricVitalRanges.Cutoffs,
    ): ConditionPattern {
        val ceiling = cutoffs.tachycardiaCeiling
        return ConditionPattern(
            id = "emergency_tachycardia_paediatric_$idSuffix",
            title = "Critically elevated heart rate (paediatric — $bandLabel)",
            category = ConditionCategory.CARDIOVASCULAR,
            signalRules = listOf(
                SignalRule(
                    MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                    ThresholdSource.LITERATURE,
                    "AHA PALS 2020 / UK Resuscitation Council APLS 2021 — paediatric tachycardia threshold for the $bandLabel band is ≥${ceiling.toInt()} bpm",
                    required = true,
                    absoluteAbove = ceiling,
                ),
            ),
            minActiveSignals = 1,
            severityFloor = AlertTier.URGENT,
            explanation = "The most recent heart-rate reading is at or above ${ceiling.toInt()} bpm — the PALS-anchored threshold for the $bandLabel band. Paediatric heart-rate norms differ from adult norms by age, and the adult ≥130 bpm cutoff does not apply for this age. A reading at this level is outside normal variation for the band and is the standard threshold for evaluating tachyarrhythmia, infection / sepsis, severe dehydration, or pain in this population.",
            suggestedAction = "If accompanied by lethargy, poor feeding, breathing difficulty, mottled or cool skin, fewer wet nappies, or any new symptoms, seek immediate medical attention. Without acute symptoms, re-check at rest after several minutes; a sustained reading at this level warrants prompt paediatric evaluation by a healthcare provider.",
            references = listOf(
                "American Heart Association (2020) — Pediatric Advanced Life Support Provider Manual",
                "UK Resuscitation Council (2021) — Advanced Paediatric Life Support, 6th edition",
                "Bonafide CP et al. (2013) — Development of heart and respiratory rate percentile curves for hospitalized children. Pediatrics 131:e1150",
                "NICE NG143 (2019) — Sepsis: recognition, diagnosis and early management",
            ),
            earlyDetection = "Paediatric tachycardia is one of the earliest and most sensitive signs of physiologic decompensation in children — sepsis, dehydration, and shock all present with sustained heart-rate elevation before blood pressure changes. Children compensate for circulatory stress by raising heart rate; hypotension is a late and ominous sign. A wearable estimate sitting at or above the PALS threshold for the age band warrants the same response as a clinical reading at that level.",
            requiredStates = setOf(band),
        )
    }

    private fun bradycardiaPattern(
        idSuffix: String,
        bandLabel: String,
        band: PhysiologyState,
        cutoffs: PaediatricVitalRanges.Cutoffs,
    ): ConditionPattern {
        val floor = cutoffs.bradycardiaFloor
        return ConditionPattern(
            id = "emergency_bradycardia_paediatric_$idSuffix",
            title = "Critically low heart rate (paediatric — $bandLabel)",
            category = ConditionCategory.CARDIOVASCULAR,
            signalRules = listOf(
                SignalRule(
                    MetricType.RESTING_HEART_RATE, DeviationDirection.BELOW, 0.0, 0, 1.5,
                    ThresholdSource.LITERATURE,
                    "AHA PALS 2020 / UK Resuscitation Council APLS 2021 — paediatric bradycardia threshold for the $bandLabel band is ≤${floor.toInt()} bpm",
                    required = true,
                    absoluteBelow = floor,
                ),
            ),
            minActiveSignals = 1,
            severityFloor = AlertTier.URGENT,
            explanation = "The most recent heart-rate reading is at or below ${floor.toInt()} bpm — the PALS-anchored bradycardia threshold for the $bandLabel band. The adult ≤35 bpm cutoff does not apply for this age; in paediatrics, sustained bradycardia at a band-specific threshold is a sign of hypoxia, raised intracranial pressure, or pre-arrest physiology and is treated as an emergency in PALS / APLS protocols.",
            suggestedAction = "If accompanied by poor feeding, lethargy, breathing difficulty, mottled or cool skin, or any new symptoms, seek immediate medical attention. A sustained heart-rate reading at this level in a child this age warrants urgent paediatric evaluation regardless of how the child appears.",
            references = listOf(
                "American Heart Association (2020) — Pediatric Advanced Life Support Provider Manual",
                "UK Resuscitation Council (2021) — Advanced Paediatric Life Support, 6th edition",
                "Bonafide CP et al. (2013) — Development of heart and respiratory rate percentile curves for hospitalized children. Pediatrics 131:e1150",
            ),
            earlyDetection = "In paediatrics, bradycardia is rarely benign — it is the immediate pre-arrest rhythm in most paediatric cardiac arrests and is most often a sign of hypoxia, vagal stimulation, or raised intracranial pressure. PALS protocols treat sustained bradycardia at the band-specific threshold as an emergency even in a child who looks well; the wearable detection threshold matches the PALS clinical threshold.",
            requiredStates = setOf(band),
        )
    }
}
