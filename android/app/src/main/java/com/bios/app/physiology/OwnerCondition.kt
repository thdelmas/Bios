package com.bios.app.physiology

/**
 * Owner-declared known conditions that gate specialty condition patterns.
 *
 * Distinct from [PhysiologyState]: a `PhysiologyState` is a *temporary
 * physiological context* (pregnancy, postpartum, athletic taper) that
 * suppresses or shifts the interpretation of baseline-relative signals.
 * An `OwnerCondition` is an *enduring clinical fact* the owner has told
 * Bios about themselves — diagnoses, genetic conditions, prior major
 * events — that *activate* specialty patterns which would otherwise be
 * silenced for the general population.
 *
 * The split exists so a single owner can be e.g. `PREGNANCY_T2` *and*
 * `KNOWN_SICKLE_CELL_DISEASE` simultaneously without one masking the
 * other in the data model. PhysiologyState picks one slot; OwnerCondition
 * is a set.
 *
 * Manifesto guard: the owner sets these explicitly. Bios never infers a
 * condition from readings — a sickle cell crisis screen activates only
 * when the owner has typed "I have sickle cell disease" into Settings,
 * not because Bios saw two desaturation events.
 *
 * Owner-revocable instantly. Removing a condition clears the
 * corresponding patterns from the next detection pass.
 */
enum class OwnerCondition(val displayName: String) {
    /**
     * Sickle cell disease (HbSS, HbSC, HbS-β-thalassemia, or other
     * sickling genotype the owner has been told they carry). Activates
     * the sickle-cell vaso-occlusive-crisis screen pattern, which is
     * silent for the general adult population because the pattern's
     * SpO2-drop + sudden-tachycardia + pain-score signature would
     * mis-fire on infection, exercise, or anxiety in non-SCD owners.
     * Audit gap: AFRICAN_TRADITIONAL_POV §2.4.
     */
    KNOWN_SICKLE_CELL_DISEASE("Sickle cell disease"),
}
