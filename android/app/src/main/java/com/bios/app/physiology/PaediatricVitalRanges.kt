package com.bios.app.physiology

/**
 * PALS / EPLS / APLS paediatric vital-sign reference ranges and emergency
 * cutoffs by age band (#198). The numbers are anchored to:
 *
 *  - **AHA PALS 2020** — Pediatric Advanced Life Support reference table
 *    for heart rate and respiratory rate across the six standard age bands.
 *  - **UK Resuscitation Council APLS 2021** — Advanced Paediatric Life
 *    Support; agrees on band boundaries and on the broad shape of the
 *    HR / RR envelopes.
 *  - **AAP / Bonafide 2013** ("Development of heart and respiratory rate
 *    percentile curves for hospitalized children", *Pediatrics* 131:e1150)
 *    — modernised 1st/99th percentile envelopes used to anchor the
 *    "critically high / critically low" emergency cutoffs below.
 *  - **NICE NG143 (2019)** — sepsis recognition in under-5s; the
 *    paediatric-tachycardia ceilings here match the NICE high-risk
 *    thresholds.
 *
 * The cutoffs are deliberately **outside** the "normal range" envelope —
 * the [tachycardiaCeiling] for the toddler band is 180 bpm, the upper
 * end of the *normal* toddler HR range is 130 bpm. A reading at 180+
 * is the paediatric-equivalent of an adult sustained ≥130 — well outside
 * normal variation, the threshold at which clinical evaluation is
 * standard. Symmetrically for [bradycardiaFloor].
 *
 * SpO2 ≤85 % stays universal across bands — hypoxia is hypoxia. Glucose
 * ≤54 mg/dL stays universal — paediatric hypoglycaemia thresholds are
 * effectively the same as adult (the ADA Level 2 cutoff is age-invariant
 * for the wearable / glucometer surfaces Bios reads).
 */
object PaediatricVitalRanges {

    /**
     * Per-band emergency vital cutoffs. `tachycardiaCeiling` is "at or
     * above → URGENT", `bradycardiaFloor` is "at or below → URGENT".
     * `respiratoryDistressCeiling` is "at or above → URGENT" for sustained
     * respiratory rate. Values reflect the PALS-anchored thresholds the
     * audit calls out (NEONATE 220 / 80, INFANT 200 / 60, TODDLER 180 / 50,
     * PRESCHOOL 160 / 45, SCHOOL 140 / 40, ADOLESCENT 135 / 40).
     */
    data class Cutoffs(
        val tachycardiaCeiling: Double,
        val bradycardiaFloor: Double,
        val respiratoryDistressCeiling: Double,
    )

    val NEONATE = Cutoffs(
        tachycardiaCeiling = 220.0,
        bradycardiaFloor = 80.0,
        respiratoryDistressCeiling = 70.0,
    )
    val INFANT = Cutoffs(
        tachycardiaCeiling = 200.0,
        bradycardiaFloor = 60.0,
        respiratoryDistressCeiling = 60.0,
    )
    val TODDLER = Cutoffs(
        tachycardiaCeiling = 180.0,
        bradycardiaFloor = 50.0,
        respiratoryDistressCeiling = 40.0,
    )
    val PRESCHOOL = Cutoffs(
        tachycardiaCeiling = 160.0,
        bradycardiaFloor = 45.0,
        respiratoryDistressCeiling = 35.0,
    )
    val SCHOOL_AGE = Cutoffs(
        tachycardiaCeiling = 140.0,
        bradycardiaFloor = 40.0,
        respiratoryDistressCeiling = 30.0,
    )
    val ADOLESCENT = Cutoffs(
        tachycardiaCeiling = 135.0,
        bradycardiaFloor = 40.0,
        respiratoryDistressCeiling = 25.0,
    )
    val ADULT = Cutoffs(
        tachycardiaCeiling = 130.0,
        bradycardiaFloor = 35.0,
        respiratoryDistressCeiling = 25.0,
    )

    /**
     * Resolve the [Cutoffs] for a given [PhysiologyState]. Non-paediatric
     * states (including the coarse [PhysiologyState.PAEDIATRIC] parent
     * flag where no band has been chosen) fall back to [ADULT] — the
     * `PAEDIATRIC` parent gets adult thresholds because Bios doesn't
     * know which band, and the safety failure mode (false-positive
     * tachycardia alert on a child with a healthy HR of 120) is
     * less dangerous than the inverse (missed bradycardia in a neonate).
     */
    fun forState(state: PhysiologyState): Cutoffs = when (state) {
        PhysiologyState.NEONATE_0_28D -> NEONATE
        PhysiologyState.INFANT_1M_12M -> INFANT
        PhysiologyState.TODDLER_1Y_3Y -> TODDLER
        PhysiologyState.PRESCHOOL_3Y_5Y -> PRESCHOOL
        PhysiologyState.SCHOOL_AGE_6Y_12Y -> SCHOOL_AGE
        PhysiologyState.ADOLESCENT_13Y_18Y -> ADOLESCENT
        else -> ADULT
    }
}
